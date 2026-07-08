package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.EmailAutomationRuleRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailAutomationRuleResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.MailAutomationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Email Automation Rules", description = "Quản lý quy tắc gửi email tự động toàn hệ thống — requires ADMIN role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/email/automation-rules")
@RequiredArgsConstructor
public class AdminEmailAutomationController {

	private final MailAutomationService mailAutomationService;

	@Operation(summary = "Danh sách quy tắc tự động toàn hệ thống")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<EmailAutomationRuleResponse>>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(mailAutomationService.listGlobalRules(page, size)));
	}

	@Operation(summary = "Tạo quy tắc tự động")
	@PostMapping
	public ResponseEntity<ApiResponse<EmailAutomationRuleResponse>> create(
			Authentication authentication,
			@Valid @RequestBody EmailAutomationRuleRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Đã tạo quy tắc tự động",
				mailAutomationService.createRule(extractUserId(authentication), request)));
	}

	@Operation(summary = "Cập nhật quy tắc tự động")
	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<EmailAutomationRuleResponse>> update(
			@PathVariable Long id,
			@Valid @RequestBody EmailAutomationRuleRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Đã cập nhật quy tắc tự động",
				mailAutomationService.updateRule(id, request)));
	}

	@Operation(summary = "Bật/tắt quy tắc tự động")
	@PatchMapping("/{id}/enabled")
	public ResponseEntity<ApiResponse<EmailAutomationRuleResponse>> setEnabled(
			@PathVariable Long id, @RequestParam boolean enabled) {
		return ResponseEntity.ok(ApiResponse.success(mailAutomationService.setEnabled(id, enabled)));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
