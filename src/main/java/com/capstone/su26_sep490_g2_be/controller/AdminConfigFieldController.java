package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CreateConfigFieldCatalogRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchFormatActiveRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateConfigFieldCatalogRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ConfigFieldCatalogItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.service.AdminTournamentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin — Config Field Catalog", description = "Admin quản lý catalog field cấu hình giải — requires ADMIN role")
@RestController
@RequestMapping("/api/v1/admin/config-field-catalog")
@RequiredArgsConstructor
public class AdminConfigFieldController {

	private final AdminTournamentConfigService adminTournamentConfigService;

	@Operation(summary = "Danh sách catalog field (phân trang server-side)", description = "Catalog field (Developer seed) — Admin chọn field gán cho thể thức")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền")
	})
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<ConfigFieldCatalogItemResponse>>> getCatalog(
			@Parameter(description = "Lọc theo scope (COMMON, KNOCKOUT, …). Có thể truyền nhiều lần hoặc phân cách bằng dấu phẩy")
			@RequestParam(required = false) List<String> scope,
			@Parameter(description = "Lọc active, default true")
			@RequestParam(required = false, defaultValue = "true") Boolean isActive,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				adminTournamentConfigService.getConfigFieldCatalog(scope, isActive, page, size)));
	}

	@Operation(summary = "Chi tiết catalog field")
	@GetMapping("/{fieldKey}")
	public ResponseEntity<ApiResponse<ConfigFieldCatalogItemResponse>> getCatalogItem(
			@PathVariable String fieldKey) {
		return ResponseEntity.ok(ApiResponse.success(
				adminTournamentConfigService.getConfigFieldCatalogItem(fieldKey)));
	}

	@Operation(summary = "Thêm field mới vào catalog")
	@PostMapping
	public ResponseEntity<ApiResponse<ConfigFieldCatalogItemResponse>> createCatalogItem(
			@Valid @RequestBody CreateConfigFieldCatalogRequest request) {
		ConfigFieldCatalogItemResponse response = adminTournamentConfigService.createConfigFieldCatalogItem(request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Đã thêm field vào catalog", response));
	}

	@Operation(summary = "Cập nhật metadata catalog field")
	@PutMapping("/{fieldKey}")
	public ResponseEntity<ApiResponse<ConfigFieldCatalogItemResponse>> updateCatalogItem(
			@PathVariable String fieldKey,
			@Valid @RequestBody UpdateConfigFieldCatalogRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				"Đã cập nhật catalog field",
				adminTournamentConfigService.updateConfigFieldCatalogItem(fieldKey, request)));
	}

	@Operation(summary = "Bật/tắt catalog field")
	@PatchMapping("/{fieldKey}/active")
	public ResponseEntity<ApiResponse<ConfigFieldCatalogItemResponse>> patchCatalogItemActive(
			@PathVariable String fieldKey,
			@Valid @RequestBody PatchFormatActiveRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				"Đã cập nhật trạng thái catalog field",
				adminTournamentConfigService.patchConfigFieldCatalogActive(fieldKey, request)));
	}
}
