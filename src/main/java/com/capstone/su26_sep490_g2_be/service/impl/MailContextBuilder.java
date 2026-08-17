package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MailProperties;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Xây dựng context chuẩn ({@code {{namespace.key}}}) dùng chung cho render thật lẫn preview.
 * Namespace nào không có dữ liệu (vd. không có match) thì không put — placeholder tương ứng sẽ
 * render ra rỗng thay vì lỗi.
 */
@Component
@RequiredArgsConstructor
public class MailContextBuilder {

	private static final DateTimeFormatter DATE_TIME_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final MailProperties mailProperties;
	private final TournamentRepository tournamentRepository;
	private final RegistrationRepository registrationRepository;
	private final PaymentRepository paymentRepository;

	public Map<String, Object> systemContext() {
		Map<String, Object> ctx = new HashMap<>();
		ctx.put("system", Map.of(
				"appName", mailProperties.getAppName(),
				"supportEmail", mailProperties.getSupportEmail(),
				"currentYear", String.valueOf(LocalDate.now().getYear())));
		return ctx;
	}

	public void putUser(Map<String, Object> ctx, User user) {
		if (user == null) return;
		String fullName = user.getProfile() != null && user.getProfile().getFullName() != null
				? user.getProfile().getFullName()
				: user.getEmail();
		ctx.put("user", Map.of("fullName", fullName, "email", user.getEmail()));
	}

	public void putTournament(Map<String, Object> ctx, Tournament tournament) {
		if (tournament == null) return;
		Map<String, Object> map = new HashMap<>();
		map.put("name", tournament.getName());
		map.put("startAt", formatInstant(tournament.getStartAt()));
		map.put("status", tournament.getStatus());
		ctx.put("tournament", map);
	}

	public void putRegistration(Map<String, Object> ctx, Registration registration) {
		if (registration == null) return;
		ctx.put("registration", Map.of(
				"playerFullName", registration.getPlayerFullName(),
				"status", registration.getStatus()));
		if (registration.getTournament() != null) {
			putTournament(ctx, registration.getTournament());
		}
		if (registration.getUser() != null) {
			putUser(ctx, registration.getUser());
		}
	}

	public void putMatch(Map<String, Object> ctx, Match match) {
		if (match == null) return;
		Map<String, Object> map = new HashMap<>();
		map.put("roundNo", String.valueOf(match.getRoundNo()));
		map.put("player1Name", match.getPlayer1() != null ? match.getPlayer1().getDisplayName() : "");
		map.put("player2Name", match.getPlayer2() != null ? match.getPlayer2().getDisplayName() : "");
		map.put("score", match.getPlayer1Score() + " - " + match.getPlayer2Score());
		/* Mã trận, bàn và giờ: ba thứ giúp phân biệt các thông báo với nhau. Một trọng
		 * tài phụ trách nhiều trận cùng lúc sẽ nhận nhiều thông báo, mà nếu chỉ có tên
		 * hai cơ thủ và số vòng thì họ không biết đứng bàn nào, lúc nào. */
		map.put("code", match.getMatchCode() != null ? match.getMatchCode() : "");
		map.put("tableNo", match.getTableNo() != null ? String.valueOf(match.getTableNo()) : "chưa gán");
		String scheduledAt = formatInstantDateTime(match.getScheduledAt());
		map.put("scheduledAt", scheduledAt.isEmpty() ? "chưa xếp giờ" : scheduledAt);
		ctx.put("match", map);
		if (match.getTournament() != null) {
			putTournament(ctx, match.getTournament());
		}
	}

	public void putPayment(Map<String, Object> ctx, Payment payment) {
		if (payment == null) return;
		ctx.put("payment", Map.of(
				"amount", payment.getAmount() + " VNĐ",
				"checkoutUrl", payment.getCheckoutUrl() != null ? payment.getCheckoutUrl() : ""));
	}

	/** Context mẫu cho preview khi không truyền id thật — không được để placeholder trống trên UI. */
	public Map<String, Object> sampleContext() {
		Map<String, Object> ctx = systemContext();
		ctx.put("user", Map.of("fullName", "Nguyễn Văn A", "email", "nguyenvana@example.com"));
		ctx.put("tournament", Map.of("name", "Giải Bi-a Mở Rộng 2026", "startAt", "20/08/2026", "status", "DRAW_DONE"));
		ctx.put("registration", Map.of("playerFullName", "Nguyễn Văn A", "status", "APPROVED"));
		ctx.put("match", Map.of("roundNo", "1", "player1Name", "Nguyễn Văn A", "player2Name", "Trần Văn B",
				"score", "0 - 0", "code", "R1-M3", "tableNo", "4", "scheduledAt", "20/08/2026 14:30"));
		ctx.put("payment", Map.of("amount", "200,000 VNĐ", "checkoutUrl", "https://pay.payos.vn/sample"));
		ctx.put("custom", Map.of("otp", "123456", "subject", "Thông báo từ ban tổ chức", "message", "Nội dung thông báo mẫu."));
		return ctx;
	}

	/** Preview với id thật khi có, merge đè lên context mẫu để phần thiếu vẫn có giá trị hiển thị. */
	public Map<String, Object> previewContext(Long tournamentId, Long sampleRegistrationId) {
		Map<String, Object> ctx = new HashMap<>(sampleContext());

		if (sampleRegistrationId != null) {
			registrationRepository.findById(sampleRegistrationId).ifPresent(r -> putRegistration(ctx, r));
			paymentRepository.findByRegistrationId(sampleRegistrationId).stream()
					.max(java.util.Comparator.comparing(Payment::getCreatedAt))
					.ifPresent(p -> putPayment(ctx, p));
		} else if (tournamentId != null) {
			tournamentRepository.findById(tournamentId).ifPresent(t -> putTournament(ctx, t));
		}
		return ctx;
	}

	private String formatInstant(java.time.Instant instant) {
		if (instant == null) return "";
		return instant.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().toString();
	}

	/** Có cả giờ phút — giờ thi đấu của một trận không thể chỉ ghi ngày. */
	private String formatInstantDateTime(java.time.Instant instant) {
		if (instant == null) return "";
		return DATE_TIME_VN.format(instant.atZone(ZoneId.of("Asia/Ho_Chi_Minh")));
	}
}
