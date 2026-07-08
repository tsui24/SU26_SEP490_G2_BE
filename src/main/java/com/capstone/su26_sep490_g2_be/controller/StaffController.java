package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.ScoreIncrementRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.IncrementScoreResponse;
import com.capstone.su26_sep490_g2_be.dto.response.MatchResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.MatchBroadcastService;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import com.capstone.su26_sep490_g2_be.service.impl.BracketGenerationServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Staff", description = "Staff / Referee APIs — requires STAFF role")
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

	private final MatchService matchService;
	private final BracketGenerationServiceImpl bracketHelper;
	private final MatchBroadcastService broadcastService;

	@Operation(summary = "Danh sách trận được phân công cho trọng tài hiện tại (lọc tên giải trong phạm vi đã gán)")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping("/matches")
	public ResponseEntity<ApiResponse<List<MatchResponse>>> getMyMatches(
			Authentication auth,
			@RequestParam(required = false) Long tournamentId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String tournamentName) {
		Long staffId = extractUserId(auth);
		List<MatchResponse> matches = matchService
				.getMatchesForReferee(staffId, tournamentId, status, tournamentName)
				.stream()
				.map(bracketHelper::toMatchResponse)
				.toList();
		return ResponseEntity.ok(ApiResponse.success(matches));
	}

	@Operation(summary = "Cộng/trừ điểm theo delta (+1 / -1) — tránh race condition")
	@SecurityRequirement(name = "bearerAuth")
	@PatchMapping("/matches/{matchId}/score/increment")
	public ResponseEntity<ApiResponse<IncrementScoreResponse>> incrementScore(
			Authentication auth,
			@PathVariable Long matchId,
			@Valid @RequestBody ScoreIncrementRequest req) {
		Long staffId = extractUserId(auth);
		matchService.incrementScore(matchId, req.getPlayerSlot(), req.getDelta(), staffId);
		IncrementScoreResponse body = buildIncrementResponse(matchId);
		broadcastService.broadcastMatchUpdate(body.getMatch());
		return ResponseEntity.ok(ApiResponse.success(body));
	}

	private IncrementScoreResponse buildIncrementResponse(Long matchId) {
		MatchResponse resp = bracketHelper.getMatchResponseById(matchId);
		boolean suggest = false;
		Long suggestedWinnerId = null;
		Integer raceTo = resp.getRaceTo();
		if (raceTo != null) {
			int s1 = resp.getPlayer1Score() != null ? resp.getPlayer1Score() : 0;
			int s2 = resp.getPlayer2Score() != null ? resp.getPlayer2Score() : 0;
			if (s1 >= raceTo || s2 >= raceTo) {
				suggest = true;
				if (s1 > s2 && resp.getPlayer1() != null) {
					suggestedWinnerId = resp.getPlayer1().getId();
				} else if (s2 > s1 && resp.getPlayer2() != null) {
					suggestedWinnerId = resp.getPlayer2().getId();
				} else if (s1 >= raceTo && resp.getPlayer1() != null) {
					suggestedWinnerId = resp.getPlayer1().getId();
				} else if (s2 >= raceTo && resp.getPlayer2() != null) {
					suggestedWinnerId = resp.getPlayer2().getId();
				}
			}
		}
		return IncrementScoreResponse.builder()
				.match(resp)
				.suggestComplete(suggest)
				.suggestedWinnerId(suggestedWinnerId)
				.build();
	}

	private Long extractUserId(Authentication auth) {
		if (auth == null || !(auth.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) auth.getCredentials();
	}
}
