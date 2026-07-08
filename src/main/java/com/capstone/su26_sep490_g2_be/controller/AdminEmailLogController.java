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

@Tag(name = "Admin — Email Logs", description = "Nhật ký gửi email toàn hệ thống — requires ADMIN role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/email/logs")
@RequiredArgsConstructor
public class AdminEmailLogController {

	private final EmailLogService emailLogService;

	@Operation(summary = "Danh sách nhật ký gửi email")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<EmailSendLogResponse>>> search(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) Long tournamentId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(emailLogService.search(status, tournamentId, page, size)));
	}

	@Operation(summary = "Chi tiết 1 lượt gửi — xem lại HTML đã render")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<EmailSendLogDetailResponse>> getDetail(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(emailLogService.getDetail(id)));
	}
}
