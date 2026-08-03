package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailSendLogDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailSendLogResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.service.EmailLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Tag(name = "Admin — Email Logs", description = "Nhật ký gửi email toàn hệ thống — requires ADMIN role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/email/logs")
@RequiredArgsConstructor
public class AdminEmailLogController {

	private static final ZoneId ZONE = ZoneId.systemDefault();

	private final EmailLogService emailLogService;

	@Operation(summary = "Danh sách nhật ký gửi email")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<EmailSendLogResponse>>> search(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) Long tournamentId,
			@RequestParam(required = false) String triggerType,
			@RequestParam(required = false) String templateCode,
			@RequestParam(required = false) String recipientEmail,
			@RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(emailLogService.search(
				status, tournamentId, triggerType, templateCode, recipientEmail,
				parseFrom(fromDate), parseTo(toDate), page, size)));
	}

	private Instant parseFrom(String from) {
		if (from == null || from.isBlank()) return null;
		return LocalDate.parse(from.trim()).atStartOfDay(ZONE).toInstant();
	}

	private Instant parseTo(String to) {
		if (to == null || to.isBlank()) return null;
		return LocalDate.parse(to.trim()).atTime(LocalTime.MAX).atZone(ZONE).toInstant();
	}

	@Operation(summary = "Chi tiết 1 lượt gửi — xem lại HTML đã render")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<EmailSendLogDetailResponse>> getDetail(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(emailLogService.getDetail(id)));
	}
}
