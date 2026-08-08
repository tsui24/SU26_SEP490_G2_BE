package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.RegisterDeviceTokenRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.NotificationResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.service.NotificationService;
import com.capstone.su26_sep490_g2_be.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Hộp thông báo dùng chung cho web và mobile.
 *
 * Cố ý KHÔNG nằm dưới {@code /api/v1/player/**}: web chủ yếu do Admin, Owner, Manager và Staff
 * dùng, mà nhánh player bị {@code SecurityConfig} khoá cứng vào role PLAYER. Đặt ở đây thì mọi
 * người đã đăng nhập đều gọi được qua luật {@code anyRequest().authenticated()} có sẵn — không
 * phải sửa cấu hình bảo mật.
 *
 * Mỗi người chỉ thấy thông báo của chính mình: id người dùng lấy từ JWT, không nhận từ tham số.
 */
@Tag(name = "Notification", description = "Hộp thông báo và thiết bị nhận thông báo đẩy")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@Operation(summary = "Danh sách thông báo của tôi",
			description = "Dựng từ lịch sử email hệ thống đã gửi cho chính người đang đăng nhập, mới nhất trước")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> listMine(
			Authentication authentication,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Long userId = SecurityUtil.extractUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(notificationService.listMine(userId, page, size)));
	}

	@Operation(summary = "Đếm thông báo chưa đọc",
			description = "Trạng thái đã đọc do máy của người dùng giữ, nên mốc thời gian đã đọc được gửi kèm. "
					+ "Bỏ trống 'after' nghĩa là chưa từng đọc gì.")
	@GetMapping("/unread-count")
	public ResponseEntity<ApiResponse<Long>> countUnread(
			Authentication authentication,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant after) {
		Long userId = SecurityUtil.extractUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(notificationService.countUnread(userId, after)));
	}

	@Operation(summary = "Đăng ký thiết bị nhận thông báo đẩy",
			description = "Chỉ mobile dùng. Gọi sau khi đăng nhập và người dùng đã cho phép nhận thông báo. "
					+ "Gọi lại nhiều lần là an toàn.")
	@PostMapping("/device-tokens")
	public ResponseEntity<ApiResponse<Void>> registerDevice(
			Authentication authentication,
			@Valid @RequestBody RegisterDeviceTokenRequest request) {
		Long userId = SecurityUtil.extractUserId(authentication);
		notificationService.registerDevice(userId, request);
		return ResponseEntity.ok(ApiResponse.success("Đã đăng ký thiết bị nhận thông báo", null));
	}

	@Operation(summary = "Gỡ thiết bị khỏi danh sách nhận thông báo",
			description = "Gọi khi đăng xuất, để người mượn máy đăng nhập sau không nhận thông báo của chủ cũ")
	@DeleteMapping("/device-tokens")
	public ResponseEntity<ApiResponse<Void>> unregisterDevice(
			Authentication authentication,
			@RequestParam String expoToken) {
		Long userId = SecurityUtil.extractUserId(authentication);
		notificationService.unregisterDevice(userId, expoToken);
		return ResponseEntity.ok(ApiResponse.success("Đã gỡ thiết bị", null));
	}
}
