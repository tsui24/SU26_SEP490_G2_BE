package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.AdminRegistrationFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Registration Form Templates", description = "Admin tạo và cấu hình template form đăng ký")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/registration-form-templates")
@RequiredArgsConstructor
public class AdminRegistrationFormTemplateController {

	private final AdminRegistrationFormService adminRegistrationFormService;

	@Operation(summary = "Danh sách template form đăng ký")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<RegistrationFormTemplateListItemResponse>>> listTemplates(
			@RequestParam(required = false) Boolean isActive,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				adminRegistrationFormService.listTemplates(isActive, page, size)));
	}

	@Operation(summary = "Chi tiết template")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<RegistrationFormTemplateDetailResponse>> getTemplate(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(adminRegistrationFormService.getTemplate(id)));
	}

	@Operation(summary = "Tạo template form đăng ký")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Code đã tồn tại")
	})
	@PostMapping
	public ResponseEntity<ApiResponse<RegistrationFormTemplateCreateResponse>> createTemplate(
			Authentication authentication,
			@Valid @RequestBody CreateRegistrationFormTemplateRequest request) {
		RegistrationFormTemplateCreateResponse response = adminRegistrationFormService.createTemplate(
				extractUserId(authentication), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Registration form template created", response));
	}

	@Operation(summary = "Cập nhật metadata template")
	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<RegistrationFormTemplateDetailResponse>> updateTemplate(
			@PathVariable Long id,
			@Valid @RequestBody UpdateRegistrationFormTemplateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(adminRegistrationFormService.updateTemplate(id, request)));
	}

	@Operation(summary = "Bật/tắt template")
	@PatchMapping("/{id}")
	public ResponseEntity<ApiResponse<RegistrationFormTemplateActivePatchResponse>> patchTemplateActive(
			@PathVariable Long id,
			@Valid @RequestBody PatchRegistrationFormTemplateActiveRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				adminRegistrationFormService.patchTemplateActive(id, request)));
	}

	@Operation(summary = "Load form chỉnh sửa field của template")
	@GetMapping("/{id}/fields")
	public ResponseEntity<ApiResponse<RegistrationFormTemplateFieldsFormResponse>> getTemplateFieldsForm(
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(adminRegistrationFormService.getTemplateFieldsForm(id)));
	}

	@Operation(summary = "Lưu field cho template")
	@PutMapping("/{id}/fields")
	public ResponseEntity<ApiResponse<RegistrationFormTemplateFieldsSaveResponse>> saveTemplateFields(
			@PathVariable Long id,
			@Valid @RequestBody UpsertRegistrationFormTemplateFieldsRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				adminRegistrationFormService.saveTemplateFields(id, request)));
	}

	@Operation(summary = "Xóa field khỏi template")
	@DeleteMapping("/{id}/fields/{fieldKey}")
	public ResponseEntity<ApiResponse<Void>> deleteTemplateField(
			@PathVariable Long id,
			@PathVariable String fieldKey) {
		adminRegistrationFormService.deleteTemplateField(id, fieldKey);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@Operation(summary = "Preview template form đăng ký")
	@GetMapping("/{id}/preview")
	public ResponseEntity<ApiResponse<RegistrationFormPreviewResponse>> getTemplatePreview(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(adminRegistrationFormService.getTemplatePreview(id)));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
