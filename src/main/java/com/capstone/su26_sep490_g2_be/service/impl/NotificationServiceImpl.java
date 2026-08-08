package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.RegisterDeviceTokenRequest;
import com.capstone.su26_sep490_g2_be.dto.response.NotificationResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.DeviceToken;
import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.DeviceTokenRepository;
import com.capstone.su26_sep490_g2_be.repository.NotificationLogRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

	/** Đủ để nhận ra thông báo nói về chuyện gì mà không biến thẻ thành một bài đọc. */
	private static final int PREVIEW_LENGTH = 120;

	private final NotificationLogRepository notificationLogRepository;
	private final DeviceTokenRepository deviceTokenRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> listMine(Long userId, int page, int size) {
		Pageable pageable = PageRequest.of(
				Math.max(page, 0),
				Math.min(Math.max(size, 1), 100),
				Sort.by("createdAt").descending());

		Page<EmailSendLog> logs = notificationLogRepository.findMine(userId, pageable);
		return PageResponse.of(logs, this::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public long countUnread(Long userId, Instant after) {
		return notificationLogRepository.countUnread(userId, after);
	}

	@Override
	@Transactional
	public void registerDevice(Long userId, RegisterDeviceTokenRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		String expoToken = request.getExpoToken().trim();

		// Máy cho mượn rồi đăng nhập tài khoản khác vẫn giữ nguyên token Expo cũ. Gán lại chủ sở
		// hữu thay vì thêm dòng mới, nếu không người chủ trước tiếp tục nhận thông báo trên máy
		// không còn là của mình.
		DeviceToken device = deviceTokenRepository.findByExpoToken(expoToken)
				.orElseGet(() -> DeviceToken.builder()
						.expoToken(expoToken)
						.build());

		device.setUser(user);
		device.setPlatform(request.getPlatform());
		device.setLastSeenAt(Instant.now());

		deviceTokenRepository.save(device);
	}

	@Override
	@Transactional
	public void unregisterDevice(Long userId, String expoToken) {
		// Chỉ xoá được token của chính mình — nếu không, biết token của người khác là chặn được
		// thông báo của họ.
		deviceTokenRepository.findByExpoToken(expoToken)
				.filter(d -> d.getUser() != null && d.getUser().getId().equals(userId))
				.ifPresent(deviceTokenRepository::delete);
	}

	private NotificationResponse toResponse(EmailSendLog log) {
		Tournament tournament = log.getTournament();

		return NotificationResponse.builder()
				.id(log.getId())
				.title(log.getSubjectRendered())
				.preview(toPreview(log.getBodyRendered()))
				.eventType(log.getRule() != null ? log.getRule().getEventType() : null)
				.tournamentId(tournament != null ? tournament.getId() : null)
				.tournamentName(tournament != null ? tournament.getName() : null)
				.createdAt(log.getCreatedAt())
				.build();
	}

	/**
	 * Rút vài dòng đầu của thân email làm dòng phụ cho thẻ thông báo.
	 *
	 * {@code body_rendered} là email HTML hoàn chỉnh, kèm cả style và bố cục bảng — trả nguyên
	 * vẹn thì vừa nặng cho mạng di động vừa không hiển thị được trong danh sách. Bỏ hẳn phần
	 * {@code <style>}/{@code <script>} trước khi gỡ thẻ, vì nội dung bên trong hai thẻ đó không
	 * phải chữ để đọc mà gỡ thẻ xong lại lọt ra thành rác.
	 */
	private String toPreview(String html) {
		if (html == null || html.isBlank()) {
			return "";
		}

		String text = html
				.replaceAll("(?is)<(style|script|head)[^>]*>.*?</\\1>", " ")
				.replaceAll("(?s)<!--.*?-->", " ")
				.replaceAll("(?s)<[^>]+>", " ")
				.replace("&nbsp;", " ")
				.replace("&amp;", "&")
				.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&quot;", "\"")
				.replace("&#39;", "'")
				.replaceAll("\\s+", " ")
				.trim();

		if (text.length() <= PREVIEW_LENGTH) {
			return text;
		}
		return text.substring(0, PREVIEW_LENGTH).trim() + "…";
	}
}
