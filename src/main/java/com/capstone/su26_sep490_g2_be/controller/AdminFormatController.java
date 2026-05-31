package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;
import com.capstone.su26_sep490_g2_be.service.AdminTournamentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Tournament Formats", description = "Admin setup thể thức giải đấu — requires ADMIN role")
@RestController
@RequestMapping("/api/v1/admin/formats")
@RequiredArgsConstructor
public class AdminFormatController {

	private final AdminTournamentConfigService adminTournamentConfigService;

	@Operation(summary = "Danh sách thể thức")
	@GetMapping
	public ResponseEntity<ApiResponse<FormatListResponse>> listFormats(
			@RequestParam(required = false) Boolean isActive,
			@RequestParam(required = false) FormatSetupStatus setupStatus) {
		return ResponseEntity.ok(ApiResponse.success(
				adminTournamentConfigService.listFormats(isActive, setupStatus)));
	}

	@Operation(summary = "Chi tiết thể thức")
	@GetMapping("/{code}")
	public ResponseEntity<ApiResponse<FormatDetailResponse>> getFormat(@PathVariable String code) {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.getFormat(code)));
	}

	@Operation(summary = "Tạo thể thức", description = "Wizard màn 1 — tạo metadata thể thức")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Code đã tồn tại")
	})
	@PostMapping
	public ResponseEntity<ApiResponse<FormatCreateResponse>> createFormat(
			@Valid @RequestBody CreateFormatRequest request) {
		FormatCreateResponse response = adminTournamentConfigService.createFormat(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Format created", response));
	}

	@Operation(summary = "Cập nhật metadata thể thức")
	@PutMapping("/{code}")
	public ResponseEntity<ApiResponse<FormatDetailResponse>> updateFormat(
			@PathVariable String code,
			@Valid @RequestBody UpdateFormatRequest request) {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.updateFormat(code, request)));
	}

	@Operation(summary = "Bật/tắt thể thức")
	@PatchMapping("/{code}")
	public ResponseEntity<ApiResponse<FormatActivePatchResponse>> patchFormatActive(
			@PathVariable String code,
			@Valid @RequestBody PatchFormatActiveRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				adminTournamentConfigService.patchFormatActive(code, request)));
	}

	@Operation(summary = "Trạng thái setup thể thức")
	@GetMapping("/{code}/setup-status")
	public ResponseEntity<ApiResponse<FormatSetupStatusResponse>> getSetupStatus(@PathVariable String code) {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.getSetupStatus(code)));
	}

	@Operation(summary = "Load form config fields", description = "Wizard màn 2 — load default field form")
	@GetMapping("/{code}/config-fields")
	public ResponseEntity<ApiResponse<FormatConfigFieldsFormResponse>> getConfigFieldsForm(@PathVariable String code) {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.getConfigFieldsForm(code)));
	}

	@Operation(summary = "Lưu config fields", description = "Wizard màn 2 — INSERT/UPDATE default field")
	@PutMapping("/{code}/config-fields")
	public ResponseEntity<ApiResponse<FormatConfigFieldsSaveResponse>> saveConfigFields(
			@PathVariable String code,
			@Valid @RequestBody UpsertFormatConfigFieldsRequest request) {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.saveConfigFields(code, request)));
	}

	@Operation(summary = "Load race-to rules", description = "Wizard màn 3 — load default race-to")
	@GetMapping("/{code}/race-to-rules")
	public ResponseEntity<ApiResponse<FormatRaceToRulesFormResponse>> getRaceToRulesForm(@PathVariable String code) {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.getRaceToRulesForm(code)));
	}

	@Operation(summary = "Lưu race-to rules", description = "Wizard màn 3 — INSERT/UPDATE default race-to")
	@PutMapping("/{code}/race-to-rules")
	public ResponseEntity<ApiResponse<FormatRaceToRulesSaveResponse>> saveRaceToRules(
			@PathVariable String code,
			@Valid @RequestBody UpsertFormatRaceToRulesRequest request) {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.saveRaceToRules(code, request)));
	}

	@Operation(summary = "Preview setup", description = "Wizard màn 4 — preview trước khi kích hoạt")
	@GetMapping("/{code}/setup-summary")
	public ResponseEntity<ApiResponse<FormatSetupSummaryResponse>> getSetupSummary(@PathVariable String code) {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.getSetupSummary(code)));
	}

	@Operation(summary = "Kích hoạt thể thức", description = "Wizard màn 4 — Owner mới chọn được format")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kích hoạt thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Setup chưa hoàn tất")
	})
	@PostMapping("/{code}/activate")
	public ResponseEntity<ApiResponse<FormatActivateResponse>> activateFormat(@PathVariable String code) {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.activateFormat(code)));
	}

	@Operation(summary = "Bootstrap default mẫu", description = "Insert nhanh default mẫu quán bi-a")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Bootstrap thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Đã có default")
	})
	@PostMapping("/{code}/bootstrap-defaults")
	public ResponseEntity<ApiResponse<FormatBootstrapResponse>> bootstrapDefaults(
			@PathVariable String code,
			@RequestBody(required = false) BootstrapDefaultsRequest request) {
		FormatBootstrapResponse response = adminTournamentConfigService.bootstrapDefaults(code, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Defaults bootstrapped", response));
	}
}
