package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ConfigFieldCatalogItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ConfigFieldCatalogListResponse;
import com.capstone.su26_sep490_g2_be.service.AdminTournamentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Config Field Catalog", description = "Admin quản lý catalog field cấu hình giải — requires ADMIN role")
@RestController
@RequestMapping("/api/v1/admin/config-field-catalog")
@RequiredArgsConstructor
public class AdminConfigFieldController {

	private final AdminTournamentConfigService adminTournamentConfigService;

	@Operation(summary = "Danh sách catalog field", description = "Catalog field (Developer seed) — Admin chọn field gán cho thể thức")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền")
	})
	@GetMapping
	public ResponseEntity<ApiResponse<ConfigFieldCatalogListResponse>> getCatalog(
			@Parameter(description = "Lọc theo scope, phân cách bằng dấu phẩy")
			@RequestParam(required = false) String scope,
			@Parameter(description = "Lọc active, default true")
			@RequestParam(required = false, defaultValue = "true") Boolean isActive) {
		return ResponseEntity.ok(ApiResponse.success(
				adminTournamentConfigService.getConfigFieldCatalog(scope, isActive)));
	}

	@Operation(summary = "Chi tiết catalog field")
	@GetMapping("/{fieldKey}")
	public ResponseEntity<ApiResponse<ConfigFieldCatalogItemResponse>> getCatalogItem(
			@PathVariable String fieldKey) {
		return ResponseEntity.ok(ApiResponse.success(
				adminTournamentConfigService.getConfigFieldCatalogItem(fieldKey)));
	}
}
