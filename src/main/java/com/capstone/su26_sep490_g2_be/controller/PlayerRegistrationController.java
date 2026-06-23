package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.SubmitTournamentRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationFormPreviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentListItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRegistrationResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.OwnerTournamentService;
import com.capstone.su26_sep490_g2_be.service.RegistrationFormService;
import com.capstone.su26_sep490_g2_be.service.RegistrationService;
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

@Tag(name = "Player — Tournament Registration", description = "Player xem form và đăng ký giải đấu")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/player")
@RequiredArgsConstructor
public class PlayerRegistrationController {

	private final RegistrationService registrationService;
	private final RegistrationFormService registrationFormService;
	private final TournamentRepository tournamentRepository;
	private final OwnerTournamentService ownerTournamentService;

	@Operation(summary = "Danh sách giải đấu đang mở / sắp mở")
	@GetMapping("/tournaments")
	public ResponseEntity<ApiResponse<PageResponse<TournamentListItemResponse>>> listTournaments(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.listPlayerTournaments(status, search, page, size)));
	}

	@Operation(summary = "Chi tiết giải đấu")
	@GetMapping("/tournaments/{tournamentId}")
	public ResponseEntity<ApiResponse<TournamentDetailResponse>> getTournamentDetail(
			@PathVariable Long tournamentId) {
		return ResponseEntity.ok(ApiResponse.success(
				ownerTournamentService.getPlayerTournamentDetail(tournamentId)));
	}

	@Operation(summary = "Xem form đăng ký của giải")
	@GetMapping("/tournaments/{tournamentId}/registration-form")
	public ResponseEntity<ApiResponse<RegistrationFormPreviewResponse>> getRegistrationForm(
			@PathVariable Long tournamentId) {
		var tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		return ResponseEntity.ok(ApiResponse.success(registrationFormService.resolveTournamentForm(tournament)));
	}

	@Operation(summary = "Kiểm tra đăng ký của tôi cho một giải — null nếu chưa đăng ký")
	@GetMapping("/tournaments/{tournamentId}/my-registration")
	public ResponseEntity<ApiResponse<TournamentRegistrationResponse>> getMyRegistrationForTournament(
			Authentication authentication,
			@PathVariable Long tournamentId) {
		Long userId = extractUserId(authentication);
		TournamentRegistrationResponse reg = registrationService.getMyRegistrationForTournament(tournamentId, userId);
		return ResponseEntity.ok(ApiResponse.success(reg));
	}

	@Operation(summary = "Đăng ký giải đấu")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đăng ký thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Đã đăng ký rồi")
	})
	@PostMapping("/tournaments/{tournamentId}/registrations")
	public ResponseEntity<ApiResponse<TournamentRegistrationResponse>> submitRegistration(
			Authentication authentication,
			@PathVariable Long tournamentId,
			@Valid @RequestBody SubmitTournamentRegistrationRequest request) {
		TournamentRegistrationResponse response = registrationService.submitRegistration(
				tournamentId, extractUserId(authentication), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Registration submitted", response));
	}

	@Operation(summary = "Danh sách đăng ký của tôi")
	@GetMapping("/registrations")
	public ResponseEntity<ApiResponse<PageResponse<TournamentRegistrationResponse>>> getMyRegistrations(
			Authentication authentication,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				registrationService.getMyRegistrations(extractUserId(authentication), page, size)));
	}

	@Operation(summary = "Chi tiết đăng ký của tôi")
	@GetMapping("/registrations/{id}")
	public ResponseEntity<ApiResponse<TournamentRegistrationResponse>> getRegistrationDetail(
			Authentication authentication,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				registrationService.getRegistrationDetail(id, extractUserId(authentication), false)));
	}

	@Operation(summary = "Hủy đăng ký")
	@DeleteMapping("/registrations/{id}")
	public ResponseEntity<ApiResponse<Void>> cancelRegistration(
			Authentication authentication,
			@PathVariable Long id) {
		registrationService.cancel(id, extractUserId(authentication));
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
