package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.MailLayoutSettingsRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.MailLayoutSettingsResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.MailLayoutSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Email Layout", description = "Cấu hình header/footer chung cho mọi email — requires ADMIN role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/email/layout")
@RequiredArgsConstructor
public class AdminMailLayoutSettingsController {

	private final MailLayoutSettingsService mailLayoutSettingsService;

	@Operation(summary = "Xem khung header/footer email hiện tại")
	@GetMapping
	public ResponseEntity<ApiResponse<MailLayoutSettingsResponse>> getSettings() {
		return ResponseEntity.ok(ApiResponse.success(mailLayoutSettingsService.getSettings()));
	}

	@Operation(summary = "Cập nhật khung header/footer email")
	@PutMapping
	public ResponseEntity<ApiResponse<MailLayoutSettingsResponse>> updateSettings(
			Authentication authentication,
			@Valid @RequestBody MailLayoutSettingsRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Đã cập nhật khung email",
				mailLayoutSettingsService.updateSettings(extractUserId(authentication), request)));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
