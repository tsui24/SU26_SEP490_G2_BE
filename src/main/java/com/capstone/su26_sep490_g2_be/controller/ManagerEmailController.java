package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplatePreviewRequest;
import com.capstone.su26_sep490_g2_be.dto.request.ManualSendEmailRequest;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.EmailTemplateService;
import com.capstone.su26_sep490_g2_be.service.TournamentEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Manager — Email", description = "Gửi email và quản lý automation rule theo giải đấu — requires MANAGER role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class ManagerEmailController {

	private final TournamentEmailService tournamentEmailService;
	private final EmailTemplateService emailTemplateService;

	@Operation(summary = "Danh sách mẫu email khả dụng (toàn hệ thống)")
	@GetMapping("/email/templates")
	public ResponseEntity<ApiResponse<PageResponse<EmailTemplateResponse>>> listTemplates(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(emailTemplateService.listTemplates(null, true, page, size)));
	}

	@Operation(summary = "Preview email trước khi gửi cho giải đấu")
	@PostMapping("/tournaments/{id}/email/preview")
	public ResponseEntity<ApiResponse<RenderedEmailResponse>> preview(
			Authentication authentication,
			@PathVariable Long id,
			@RequestBody EmailTemplatePreviewRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				tournamentEmailService.preview(extractUserId(authentication), id, false, request)));
	}

	@Operation(summary = "Gửi email thủ công cho người tham gia giải đấu")
	@PostMapping("/tournaments/{id}/email/send-manual")
	public ResponseEntity<ApiResponse<ManualSendResultResponse>> sendManual(
			Authentication authentication,
			@PathVariable Long id,
			@Valid @RequestBody ManualSendEmailRequest request) {
		ManualSendResultResponse result = tournamentEmailService
				.sendManual(extractUserId(authentication), id, false, request);
		return ResponseEntity.ok(ApiResponse.success(
				"Đã xếp hàng gửi " + result.getQueuedCount() + " email", result));
	}

	@Operation(summary = "Danh sách quy tắc tự động áp dụng cho giải đấu")
	@GetMapping("/tournaments/{id}/email/automation-rules")
	public ResponseEntity<ApiResponse<List<EmailAutomationRuleResponse>>> listAutomationRules(
			Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				tournamentEmailService.listAutomationRules(extractUserId(authentication), id, false)));
	}

	@Operation(summary = "Bật/tắt quy tắc tự động cho giải đấu")
	@PatchMapping("/tournaments/{id}/email/automation-rules/{ruleId}/enabled")
	public ResponseEntity<ApiResponse<EmailAutomationRuleResponse>> setRuleEnabled(
			Authentication authentication,
			@PathVariable Long id, @PathVariable Long ruleId, @RequestParam boolean enabled) {
		return ResponseEntity.ok(ApiResponse.success(
				tournamentEmailService.setRuleEnabled(extractUserId(authentication), id, false, ruleId, enabled)));
	}

	@Operation(summary = "Lịch sử gửi email của giải đấu")
	@GetMapping("/tournaments/{id}/email/logs")
	public ResponseEntity<ApiResponse<PageResponse<EmailSendLogResponse>>> listLogs(
			Authentication authentication,
			@PathVariable Long id,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				tournamentEmailService.listLogs(extractUserId(authentication), id, false, status, page, size)));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
