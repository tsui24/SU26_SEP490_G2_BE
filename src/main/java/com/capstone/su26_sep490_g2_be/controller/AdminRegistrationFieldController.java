package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CreateRegistrationFieldRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchRegistrationFieldActiveRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateRegistrationFieldRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationFieldCatalogItemResponse;
import com.capstone.su26_sep490_g2_be.service.AdminRegistrationFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Registration Field Catalog", description = "Admin quản lý catalog field form đăng ký")
@RestController
@RequestMapping("/api/v1/admin/registration-field-catalog")
@RequiredArgsConstructor
public class AdminRegistrationFieldController {

	private final AdminRegistrationFormService adminRegistrationFormService;

	@Operation(summary = "Danh sách catalog field đăng ký")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<RegistrationFieldCatalogItemResponse>>> getCatalog(
			@Parameter(description = "Lọc active, default true")
			@RequestParam(required = false, defaultValue = "true") Boolean isActive,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				adminRegistrationFormService.getFieldCatalog(isActive, page, size)));
	}

	@Operation(summary = "Chi tiết catalog field")
	@GetMapping("/{fieldKey}")
	public ResponseEntity<ApiResponse<RegistrationFieldCatalogItemResponse>> getCatalogItem(
			@PathVariable String fieldKey) {
		return ResponseEntity.ok(ApiResponse.success(
				adminRegistrationFormService.getFieldCatalogItem(fieldKey)));
	}

	@Operation(summary = "Tạo catalog field")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "fieldKey đã tồn tại")
	})
	@PostMapping
	public ResponseEntity<ApiResponse<RegistrationFieldCatalogItemResponse>> createField(
			@Valid @RequestBody CreateRegistrationFieldRequest request) {
		RegistrationFieldCatalogItemResponse response = adminRegistrationFormService.createField(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Registration field created", response));
	}

	@Operation(summary = "Cập nhật catalog field")
	@PutMapping("/{fieldKey}")
	public ResponseEntity<ApiResponse<RegistrationFieldCatalogItemResponse>> updateField(
			@PathVariable String fieldKey,
			@Valid @RequestBody UpdateRegistrationFieldRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				adminRegistrationFormService.updateField(fieldKey, request)));
	}

	@Operation(summary = "Bật/tắt catalog field")
	@PatchMapping("/{fieldKey}")
	public ResponseEntity<ApiResponse<RegistrationFieldCatalogItemResponse>> patchFieldActive(
			@PathVariable String fieldKey,
			@Valid @RequestBody PatchRegistrationFieldActiveRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				adminRegistrationFormService.patchFieldActive(fieldKey, request)));
	}
}
