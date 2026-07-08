package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplatePreviewRequest;
import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplateRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailTemplateResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailVariableItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.EmailTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin — Email Templates", description = "Quản lý mẫu email toàn hệ thống — requires ADMIN role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/email/templates")
@RequiredArgsConstructor
public class AdminEmailTemplateController {

	private final EmailTemplateService emailTemplateService;

	@Operation(summary = "Danh sách mẫu email")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<EmailTemplateResponse>>> list(
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Boolean isActive,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				emailTemplateService.listTemplates(category, isActive, page, size)));
	}

	@Operation(summary = "Danh sách placeholder khả dụng")
	@GetMapping("/variables")
	public ResponseEntity<ApiResponse<List<EmailVariableItemResponse>>> listVariables() {
		return ResponseEntity.ok(ApiResponse.success(emailTemplateService.listVariables()));
	}

	@Operation(summary = "Chi tiết mẫu email")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<EmailTemplateResponse>> getTemplate(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(emailTemplateService.getTemplate(id)));
	}

	@Operation(summary = "Tạo mẫu email")
	@PostMapping
	public ResponseEntity<ApiResponse<EmailTemplateResponse>> create(
			Authentication authentication,
			@Valid @RequestBody EmailTemplateRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Đã tạo mẫu email",
				emailTemplateService.createTemplate(extractUserId(authentication), request)));
	}

	@Operation(summary = "Cập nhật mẫu email")
	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<EmailTemplateResponse>> update(
			@PathVariable Long id,
			@Valid @RequestBody EmailTemplateRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Đã cập nhật mẫu email",
				emailTemplateService.updateTemplate(id, request)));
	}

	@Operation(summary = "Bật/tắt mẫu email")
	@PatchMapping("/{id}/active")
	public ResponseEntity<ApiResponse<EmailTemplateResponse>> setActive(
			@PathVariable Long id, @RequestParam boolean active) {
		return ResponseEntity.ok(ApiResponse.success(emailTemplateService.setActive(id, active)));
	}

	@Operation(summary = "Preview mẫu email")
	@PostMapping("/{id}/preview")
	public ResponseEntity<ApiResponse<RenderedEmailResponse>> preview(
			@PathVariable Long id, @RequestBody EmailTemplatePreviewRequest request) {
		request.setTemplateId(id);
		return ResponseEntity.ok(ApiResponse.success(emailTemplateService.preview(request)));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
