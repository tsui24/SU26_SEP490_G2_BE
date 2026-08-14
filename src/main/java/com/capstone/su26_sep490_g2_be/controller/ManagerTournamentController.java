package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.OwnerTournamentService;
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

import java.util.List;

@Tag(name = "Manager — Tournaments", description = "Manager tạo và cấu hình giải đấu — requires MANAGER role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class ManagerTournamentController {

	private final OwnerTournamentService ownerTournamentService;

	@Operation(summary = "Danh sách giải đấu", description = "Phân trang — tất cả giải (Manager)")
	@GetMapping("/tournaments")
	public ResponseEntity<ApiResponse<PageResponse<TournamentListItemResponse>>> listTournaments(
			Authentication authentication,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String gameType,
			@RequestParam(required = false) String participantType,
			@RequestParam(required = false) Boolean isRegister,
			@RequestParam(required = false) Long branchId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.listTournaments(
						extractUserId(authentication), true, status, search,
						gameType, participantType, isRegister, branchId, page, size)));
	}

	@Operation(summary = "Danh sách thể thức")
	@GetMapping("/formats")
	public ResponseEntity<ApiResponse<OwnerFormatListResponse>> listFormats() {
		return ResponseEntity.ok(ApiResponse.success(ownerTournamentService.listFormats()));
	}

	@Operation(summary = "Danh sách loại bi")
	@GetMapping("/game-types")
	public ResponseEntity<ApiResponse<OwnerGameTypeListResponse>> listGameTypes() {
		return ResponseEntity.ok(ApiResponse.success(ownerTournamentService.listGameTypes()));
	}

	@Operation(summary = "Tạo giải đấu")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo thành công")
	})
	@PostMapping("/tournaments")
	public ResponseEntity<ApiResponse<CreateTournamentResponse>> createTournament(
			Authentication authentication,
			@Valid @RequestBody CreateTournamentRequest request) {
		CreateTournamentResponse response = ownerTournamentService.createTournament(
				extractUserId(authentication), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Đã tạo giải đấu", response));
	}

	@Operation(summary = "Cập nhật giải đấu")
	@PutMapping("/tournaments/{id}")
	public ResponseEntity<ApiResponse<UpdateTournamentResponse>> updateTournament(
			Authentication authentication,
			@PathVariable Long id,
			@Valid @RequestBody UpdateTournamentRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.updateTournament(extractUserId(authentication), id, request, true)));
	}

	@Operation(summary = "Chi tiết giải đấu")
	@GetMapping("/tournaments/{id}")
	public ResponseEntity<ApiResponse<TournamentDetailResponse>> getTournament(
			Authentication authentication,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.getTournament(extractUserId(authentication), id, true)));
	}

	@Operation(summary = "Load form config")
	@GetMapping("/tournaments/{id}/config-form")
	public ResponseEntity<ApiResponse<TournamentConfigFormResponse>> getConfigForm(
			Authentication authentication,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.getConfigForm(extractUserId(authentication), id, true)));
	}

	@Operation(summary = "Lưu config giải")
	@PutMapping("/tournaments/{id}/config")
	public ResponseEntity<ApiResponse<SaveTournamentConfigResponse>> saveConfig(
			Authentication authentication,
			@PathVariable Long id,
			@Valid @RequestBody SaveTournamentConfigRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.saveConfig(extractUserId(authentication), id, request, true)));
	}

	@Operation(summary = "Config đã resolve")
	@GetMapping("/tournaments/{id}/config")
	public ResponseEntity<ApiResponse<TournamentConfigResolvedResponse>> getResolvedConfig(
			Authentication authentication,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.getResolvedConfig(extractUserId(authentication), id, true)));
	}

	@Operation(summary = "Validate config giải")
	@PostMapping("/tournaments/{id}/config/validate")
	public ResponseEntity<ApiResponse<TournamentConfigValidateResponse>> validateConfig(
			Authentication authentication,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.validateConfig(extractUserId(authentication), id, true)));
	}

	@Operation(summary = "Đổi trạng thái giải")
	@PatchMapping("/tournaments/{id}/status")
	public ResponseEntity<ApiResponse<PatchTournamentStatusResponse>> patchStatus(
			Authentication authentication,
			@PathVariable Long id,
			@Valid @RequestBody PatchTournamentStatusRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.patchStatus(extractUserId(authentication), id, request, true)));
	}

	@Operation(summary = "Bật/tắt hiển thị công khai", description = "Cho phép đổi ở mọi trạng thái giải, không chỉ Nháp — giải DRAFT/CANCELLED vẫn luôn bị ẩn khỏi trang công khai bất kể giá trị này")
	@PatchMapping("/tournaments/{id}/visibility")
	public ResponseEntity<ApiResponse<PatchTournamentVisibilityResponse>> updateVisibility(
			Authentication authentication,
			@PathVariable Long id,
			@Valid @RequestBody PatchTournamentVisibilityRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.updateVisibility(extractUserId(authentication), id, request, true)));
	}

	@Operation(summary = "Lịch sử đổi trạng thái giải", description = "Audit trail — cả thao tác thủ công và tự động")
	@GetMapping("/tournaments/{id}/audit-logs")
	public ResponseEntity<ApiResponse<List<TournamentStatusHistoryResponse>>> getAuditLogs(
			Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.getStatusHistory(extractUserId(authentication), id, true)));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
