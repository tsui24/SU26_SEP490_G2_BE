package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.StartupMailGuard;
import com.capstone.su26_sep490_g2_be.entity.EmailAutomationRule;
import com.capstone.su26_sep490_g2_be.enums.EmailRecipientType;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.ExpoPushService;
import com.capstone.su26_sep490_g2_be.service.MailAutomationService;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.MailRecipientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Đẩy thông báo tới điện thoại khi có sự kiện nghiệp vụ, chạy song song với
 * {@link MailAutomationEventListener} và hoàn toàn độc lập với nó.
 *
 * Cố ý dùng lại đúng {@link MailAutomationService} và {@link MailRecipientResolver} mà listener
 * email dùng, thay vì tự quyết định gửi cho ai: hộp thông báo trong app được dựng từ
 * {@code email_send_logs}, nên nếu hai bên chọn người nhận theo hai luật khác nhau sẽ sinh cảnh
 * điện thoại báo một đằng mà mở app ra không thấy dòng nào.
 *
 * Không đụng tới bất kỳ file nghiệp vụ nào — {@link MailDomainEvent} vốn được thiết kế để thêm
 * listener mà không phải sửa nơi phát sự kiện.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

	private final MailAutomationService mailAutomationService;
	private final MailRecipientResolver mailRecipientResolver;
	private final ExpoPushService expoPushService;
	private final TournamentRepository tournamentRepository;
	private final StartupMailGuard startupMailGuard;

	/**
	 * Cố ý KHÔNG đánh {@code @Transactional} ở đây, giống {@link MailAutomationEventListener}.
	 *
	 * Spring cấm hẳn việc gắn {@code @Transactional} lên một {@code @TransactionalEventListener}
	 * chạy sau AFTER_COMMIT — giao dịch gốc đã đóng, không còn gì để tham gia — và sẽ ném lỗi
	 * ngay lúc khởi động chứ không đợi tới lúc chạy. Không cần nó thật: mỗi lời gọi repository
	 * bên dưới tự mở giao dịch riêng, và phương thức này chỉ đọc cột thường của rule chứ không
	 * chạm vào quan hệ lazy nào.
	 */
	@Async("mailTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMailDomainEvent(MailDomainEvent event) {
		if (startupMailGuard.isSuppressed()) {
			return;
		}

		List<EmailAutomationRule> rules = mailAutomationService
				.resolveActiveRulesForEvent(event.eventType(), event.tournamentId());

		if (rules.isEmpty()) {
			return;
		}

		// Nhiều rule cùng khớp một sự kiện là chuyện bình thường (rule riêng của giải chồng lên
		// rule global). Gom người nhận lại rồi gửi một lần, nếu không cùng một người sẽ rung
		// máy vài lần cho đúng một việc.
		Set<Long> userIds = new LinkedHashSet<>();
		for (EmailAutomationRule rule : rules) {
			resolveRecipients(rule, event).stream()
					.map(MailRecipient::userId)
					.filter(Objects::nonNull)
					.forEach(userIds::add);
		}

		if (userIds.isEmpty()) {
			return;
		}

		String tournamentName = resolveTournamentName(event.tournamentId());

		expoPushService.sendToUsers(
				List.copyOf(userIds),
				event.eventType().getDisplayName(),
				buildBody(event, tournamentName),
				buildData(event));
	}

	/**
	 * Lặp lại đúng cách chọn người nhận của listener email: với PLAYER/MATCH_PLAYERS thì nơi phát
	 * sự kiện đã chỉ đích danh, các loại còn lại mới cần resolver quét theo giải.
	 */
	private List<MailRecipient> resolveRecipients(EmailAutomationRule rule, MailDomainEvent event) {
		if (event.explicitRecipients() != null && !event.explicitRecipients().isEmpty()) {
			return event.explicitRecipients();
		}

		try {
			EmailRecipientType type = EmailRecipientType.valueOf(rule.getRecipientType());
			return mailRecipientResolver.resolve(type, event.tournamentId(), null);
		} catch (IllegalArgumentException e) {
			log.warn("Rule {} có recipientType lạ: {}", rule.getCode(), rule.getRecipientType());
			return List.of();
		}
	}

	private String resolveTournamentName(Long tournamentId) {
		if (tournamentId == null) {
			return null;
		}
		return tournamentRepository.findById(tournamentId)
				.map(t -> t.getName())
				.orElse(null);
	}

	private String buildBody(MailDomainEvent event, String tournamentName) {
		String matchLine = buildMatchLine(event);
		boolean hasTournament = tournamentName != null && !tournamentName.isBlank();

		if (matchLine != null) {
			return hasTournament ? tournamentName + "\n" + matchLine : matchLine;
		}
		if (hasTournament) {
			return tournamentName;
		}
		return event.eventType().isTournamentScoped()
				? "Mở ứng dụng để xem chi tiết"
				: "Có cập nhật mới về tài khoản của bạn";
	}

	/**
	 * Dòng nhận dạng trận: {@code "R1-M3 · Bàn 4 · 20/08/2026 14:30"}. Null nếu sự kiện
	 * không gắn với trận nào.
	 *
	 * Bắt buộc phải có với các sự kiện cấp trận. Một trọng tài được phân công nhiều trận
	 * cùng lúc, mà tiêu đề thông báo thì cố định theo loại sự kiện — nếu phần thân cũng chỉ
	 * có tên giải thì 5 lần phân công ra 5 thông báo giống nhau từng ký tự, không phân biệt
	 * được trận nào. Đó đúng là thứ mà việc tách thông báo theo từng trận nhằm tránh.
	 *
	 * Đọc từ {@code variables.match} do {@code MailContextBuilder#putMatch} dựng, để phần
	 * thân thông báo và phần thân email luôn nói cùng một chuyện.
	 */
	private String buildMatchLine(MailDomainEvent event) {
		Map<String, Object> variables = event.variables();
		if (variables == null || !(variables.get("match") instanceof Map<?, ?> match)) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		String code = text(match.get("code"));
		if (!code.isEmpty()) parts.add(code);
		String tableNo = text(match.get("tableNo"));
		// putMatch trả về số bàn, hoặc chữ "chưa gán" khi trận còn chưa có bàn.
		if (!tableNo.isEmpty()) parts.add(tableNo.matches("\\d+") ? "Bàn " + tableNo : tableNo);
		String scheduledAt = text(match.get("scheduledAt"));
		if (!scheduledAt.isEmpty()) parts.add(scheduledAt);
		return parts.isEmpty() ? null : String.join(" · ", parts);
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString().trim();
	}

	/** App đọc phần này để biết bấm vào thông báo thì mở màn nào. */
	private Map<String, Object> buildData(MailDomainEvent event) {
		Map<String, Object> data = new HashMap<>();
		data.put("eventType", event.eventType().getValue());
		if (event.tournamentId() != null) {
			data.put("tournamentId", event.tournamentId());
		}
		// Có matchId thì app mở thẳng trận đó thay vì chỉ mở màn giải đấu.
		if (event.matchId() != null) {
			data.put("matchId", event.matchId());
		}
		return data;
	}
}
