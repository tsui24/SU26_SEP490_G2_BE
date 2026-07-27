package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.RejectRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.OwnerRegistrationFormTemplateListResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationFormPreviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRegistrationResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.OwnerTournamentService;
import com.capstone.su26_sep490_g2_be.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Manager — Tournament Registrations", description = "Manager quản lý đăng ký giải đấu")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class ManagerRegistrationController {

	private final OwnerTournamentService ownerTournamentService;
	private final RegistrationService registrationService;

	@Operation(summary = "Danh sách template form đăng ký active")
	@GetMapping("/registration-form-templates")
	public ResponseEntity<ApiResponse<OwnerRegistrationFormTemplateListResponse>> listRegistrationFormTemplates() {
		return ResponseEntity.ok(ApiResponse.success(ownerTournamentService.listRegistrationFormTemplates()));
	}

	@Operation(summary = "Preview template form đăng ký")
	@GetMapping("/registration-form-templates/{id}/preview")
	public ResponseEntity<ApiResponse<RegistrationFormPreviewResponse>> previewRegistrationFormTemplate(
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(ownerTournamentService.previewRegistrationFormTemplate(id)));
	}

	@Operation(summary = "Preview form đăng ký của giải")
	@GetMapping("/tournaments/{id}/registration-form")
	public ResponseEntity<ApiResponse<RegistrationFormPreviewResponse>> getTournamentRegistrationForm(
			Authentication authentication,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.getTournamentRegistrationForm(extractUserId(authentication), id, true)));
	}

	@Operation(summary = "Danh sách đăng ký theo giải")
	@GetMapping("/tournaments/{id}/registrations")
	public ResponseEntity<ApiResponse<PageResponse<TournamentRegistrationResponse>>> listTournamentRegistrations(
			Authentication authentication,
			@PathVariable Long id,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				registrationService.getTournamentRegistrations(id, extractUserId(authentication), status, page, size)));
	}

	@Operation(summary = "Chi tiết đăng ký")
	@GetMapping("/registrations/{id}")
	public ResponseEntity<ApiResponse<TournamentRegistrationResponse>> getRegistrationDetail(
			Authentication authentication,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				registrationService.getRegistrationDetail(id, extractUserId(authentication), true)));
	}

	@Operation(summary = "Duyệt đăng ký")
	@PostMapping("/registrations/{id}/approve")
	public ResponseEntity<ApiResponse<TournamentRegistrationResponse>> approveRegistration(
			Authentication authentication,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				registrationService.approve(id, extractUserId(authentication))));
	}

	@Operation(summary = "Từ chối đăng ký")
	@PostMapping("/registrations/{id}/reject")
	public ResponseEntity<ApiResponse<TournamentRegistrationResponse>> rejectRegistration(
			Authentication authentication,
			@PathVariable Long id,
			@Valid @RequestBody RejectRegistrationRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				registrationService.reject(id, extractUserId(authentication), request)));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
