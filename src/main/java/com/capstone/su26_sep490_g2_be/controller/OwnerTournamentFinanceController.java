package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.TournamentFinanceEntryRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentFinanceEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentFinanceSummaryResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.TournamentFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Owner — Tournament Finance", description = "Owner quản lý thu/chi giải đấu (ngoài tiền đăng ký)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/owner/tournaments/{id}/finance")
@RequiredArgsConstructor
public class OwnerTournamentFinanceController {

	private final TournamentFinanceService financeService;

	@Operation(summary = "Danh sách + tổng hợp thu/chi của giải")
	@GetMapping
	public ResponseEntity<ApiResponse<TournamentFinanceSummaryResponse>> getSummary(
			Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				financeService.getSummary(id, extractUserId(authentication))));
	}

	@Operation(summary = "Thêm khoản thu/chi")
	@PostMapping
	public ResponseEntity<ApiResponse<TournamentFinanceEntryResponse>> create(
			Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody TournamentFinanceEntryRequest request) {
		TournamentFinanceEntryResponse response =
				financeService.create(id, extractUserId(authentication), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Đã thêm khoản thu/chi", response));
	}

	@Operation(summary = "Sửa khoản thu/chi")
	@PutMapping("/{entryId}")
	public ResponseEntity<ApiResponse<TournamentFinanceEntryResponse>> update(
			Authentication authentication, @PathVariable Long id, @PathVariable Long entryId,
			@Valid @RequestBody TournamentFinanceEntryRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				financeService.update(id, entryId, extractUserId(authentication), request)));
	}

	@Operation(summary = "Xóa khoản thu/chi")
	@DeleteMapping("/{entryId}")
	public ResponseEntity<ApiResponse<Void>> delete(
			Authentication authentication, @PathVariable Long id, @PathVariable Long entryId) {
		financeService.delete(id, entryId, extractUserId(authentication));
		return ResponseEntity.ok(ApiResponse.success("Đã xóa khoản thu/chi", null));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
