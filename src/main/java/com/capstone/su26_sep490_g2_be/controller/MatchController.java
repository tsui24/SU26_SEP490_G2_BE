package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CompleteMatchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.EliminateBottomRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SwapPlayersRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateScoreRequest;
import com.capstone.su26_sep490_g2_be.dto.response.StandingsEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.entity.MatchScoreEvent;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.MatchBroadcastService;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import com.capstone.su26_sep490_g2_be.service.impl.BracketGenerationServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Matches & Bracket", description = "Quản lý bốc thăm, lịch thi đấu và kết quả")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MatchController {

    private final BracketGenerationService bracketGenerationService;
    private final BracketGenerationServiceImpl bracketHelper;
    private final MatchService matchService;
    private final MatchBroadcastService broadcastService;

    /* ─── Bracket generation ─────────────────────────────────── */

    @Operation(summary = "Bốc thăm — sinh bracket từ danh sách người tham gia (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/tournaments/{id}/draw")
    public ResponseEntity<ApiResponse<DrawResultResponse>> drawOwner(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bốc thăm thành công", bracketGenerationService.generate(id)));
    }

    @Operation(summary = "Bốc thăm — sinh bracket từ danh sách người tham gia (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/tournaments/{id}/draw")
    public ResponseEntity<ApiResponse<DrawResultResponse>> drawManager(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bốc thăm thành công", bracketGenerationService.generate(id)));
    }

    @Operation(summary = "Xác nhận bracket — DRAW_PREVIEW → DRAW_DONE (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/tournaments/{id}/draw/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmDrawOwner(@PathVariable Long id) {
        bracketGenerationService.confirmDraw(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận bracket", null));
    }

    @Operation(summary = "Xác nhận bracket — DRAW_PREVIEW → DRAW_DONE (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/tournaments/{id}/draw/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmDrawManager(@PathVariable Long id) {
        bracketGenerationService.confirmDraw(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận bracket", null));
    }

    @Operation(summary = "Đổi chỗ 2 người chơi R1 trong DRAW_PREVIEW (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/tournaments/{id}/draw/swap")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> swapOwner(
            @PathVariable Long id, @Valid @RequestBody SwapPlayersRequest req) {
        bracketGenerationService.swapPlayers(id, req.getMatchId1(), req.getSlot1(), req.getMatchId2(), req.getSlot2());
        return ResponseEntity.ok(ApiResponse.success("Đã đổi chỗ", buildStageResponse(id)));
    }

    @Operation(summary = "Đổi chỗ 2 người chơi R1 trong DRAW_PREVIEW (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/tournaments/{id}/draw/swap")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> swapManager(
            @PathVariable Long id, @Valid @RequestBody SwapPlayersRequest req) {
        bracketGenerationService.swapPlayers(id, req.getMatchId1(), req.getSlot1(), req.getMatchId2(), req.getSlot2());
        return ResponseEntity.ok(ApiResponse.success("Đã đổi chỗ", buildStageResponse(id)));
    }

    /* ─── View stages + matches ──────────────────────────────── */

    @Operation(summary = "Danh sách stage + trận đấu (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/owner/tournaments/{id}/stages")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> stagesOwner(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(buildStageResponse(id)));
    }

    @Operation(summary = "Danh sách stage + trận đấu (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/manager/tournaments/{id}/stages")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> stagesManager(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(buildStageResponse(id)));
    }

    @Operation(summary = "Danh sách stage + trận đấu — Public (khi tournament cho phép)")
    @GetMapping("/tournaments/{id}/stages")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> stagesPublic(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(buildStageResponse(id)));
    }

    /* ─── CUT_TO_SE: populate SE bracket từ DE survivors ─────── */

    @Operation(summary = "[CUT_TO_SE] Điền SE bracket từ W+L survivors (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/tournaments/{id}/populate-final-bracket")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> populateFinalOwner(@PathVariable Long id) {
        bracketGenerationService.populateFinalBracket(id);
        return ResponseEntity.ok(ApiResponse.success("Đã điền bracket loại trực tiếp", buildStageResponse(id)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/tournaments/{id}/populate-final-bracket")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> populateFinalManager(@PathVariable Long id) {
        bracketGenerationService.populateFinalBracket(id);
        return ResponseEntity.ok(ApiResponse.success("Đã điền bracket loại trực tiếp", buildStageResponse(id)));
    }

    /* ─── GROUP_PLAYOFF: progressive elimination ──────────────── */

    @Operation(summary = "Xếp hạng vòng tròn — Public")
    @GetMapping("/tournaments/{id}/standings")
    public ResponseEntity<ApiResponse<List<StandingsEntryResponse>>> standingsPublic(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bracketGenerationService.getLeagueStandings(id)));
    }

    @Operation(summary = "Xếp hạng vòng tròn — Owner")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/owner/tournaments/{id}/standings")
    public ResponseEntity<ApiResponse<List<StandingsEntryResponse>>> standingsOwner(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bracketGenerationService.getLeagueStandings(id)));
    }

    @Operation(summary = "[GROUP_PLAYOFF] Loại bottom — giữ keepCount người (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/tournaments/{id}/eliminate-bottom")
    public ResponseEntity<ApiResponse<List<StandingsEntryResponse>>> eliminateOwner(
            @PathVariable Long id, @Valid @RequestBody EliminateBottomRequest req) {
        bracketGenerationService.eliminateBottomParticipants(id, req.getKeepCount());
        return ResponseEntity.ok(ApiResponse.success("Đã loại bottom", bracketGenerationService.getLeagueStandings(id)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/tournaments/{id}/eliminate-bottom")
    public ResponseEntity<ApiResponse<List<StandingsEntryResponse>>> eliminateManager(
            @PathVariable Long id, @Valid @RequestBody EliminateBottomRequest req) {
        bracketGenerationService.eliminateBottomParticipants(id, req.getKeepCount());
        return ResponseEntity.ok(ApiResponse.success("Đã loại bottom", bracketGenerationService.getLeagueStandings(id)));
    }

    @Operation(summary = "Tất cả trận đấu của một giải (Owner/Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/owner/tournaments/{id}/matches")
    public ResponseEntity<ApiResponse<List<MatchResponse>>> matchesOwner(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(buildMatchList(id)));
    }

    @Operation(summary = "Tất cả trận đấu của một giải (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/manager/tournaments/{id}/matches")
    public ResponseEntity<ApiResponse<List<MatchResponse>>> matchesManager(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(buildMatchList(id)));
    }

    @Operation(summary = "Tất cả trận đấu — Public")
    @GetMapping("/tournaments/{id}/matches")
    public ResponseEntity<ApiResponse<List<MatchResponse>>> matchesPublic(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(buildMatchList(id)));
    }

    /* ─── Match detail ───────────────────────────────────────── */

    @Operation(summary = "Chi tiết trận đấu")
    @GetMapping("/matches/{matchId}")
    public ResponseEntity<ApiResponse<MatchResponse>> matchDetail(@PathVariable Long matchId) {
        return ResponseEntity.ok(ApiResponse.success(bracketHelper.getMatchResponseById(matchId)));
    }

    /* ─── Match lifecycle ────────────────────────────────────── */
    /*
     * Pattern: mutate trong @Transactional (matchService.xxx),
     * sau đó re-fetch với JOIN FETCH (bracketHelper.getMatchResponseById)
     * để tránh LazyInitializationException với proxy.
     */

    @Operation(summary = "Bắt đầu trận đấu (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/owner/matches/{matchId}/start")
    public ResponseEntity<ApiResponse<MatchResponse>> startOwner(
            Authentication auth, @PathVariable Long matchId) {
        matchService.startMatch(matchId, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchAndBroadcast(matchId)));
    }

    @Operation(summary = "Bắt đầu trận đấu (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/manager/matches/{matchId}/start")
    public ResponseEntity<ApiResponse<MatchResponse>> startManager(
            Authentication auth, @PathVariable Long matchId) {
        matchService.startMatch(matchId, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchAndBroadcast(matchId)));
    }

    @PatchMapping("/staff/matches/{matchId}/start")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<MatchResponse>> startStaff(
            Authentication auth, @PathVariable Long matchId) {
        matchService.startMatch(matchId, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchAndBroadcast(matchId)));
    }

    @Operation(summary = "Cập nhật tỷ số (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/owner/matches/{matchId}/score")
    public ResponseEntity<ApiResponse<MatchResponse>> scoreOwner(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody UpdateScoreRequest req) {
        matchService.updateScore(matchId, req.getPlayer1Score(), req.getPlayer2Score(), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchAndBroadcast(matchId)));
    }

    @PutMapping("/manager/matches/{matchId}/score")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<MatchResponse>> scoreManager(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody UpdateScoreRequest req) {
        matchService.updateScore(matchId, req.getPlayer1Score(), req.getPlayer2Score(), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchAndBroadcast(matchId)));
    }

    @PutMapping("/staff/matches/{matchId}/score")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<MatchResponse>> scoreStaff(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody UpdateScoreRequest req) {
        matchService.updateScore(matchId, req.getPlayer1Score(), req.getPlayer2Score(), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchAndBroadcast(matchId)));
    }

    @Operation(summary = "Kết thúc trận — xác nhận người thắng (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/matches/{matchId}/complete")
    public ResponseEntity<ApiResponse<MatchResponse>> completeOwner(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody CompleteMatchRequest req) {
        matchService.completeMatch(matchId, req.getWinnerParticipantId(), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchBroadcastAndSyncBracket(matchId)));
    }

    @PostMapping("/manager/matches/{matchId}/complete")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<MatchResponse>> completeManager(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody CompleteMatchRequest req) {
        matchService.completeMatch(matchId, req.getWinnerParticipantId(), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchBroadcastAndSyncBracket(matchId)));
    }

    @PostMapping("/staff/matches/{matchId}/complete")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<MatchResponse>> completeStaff(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody CompleteMatchRequest req) {
        matchService.completeMatch(matchId, req.getWinnerParticipantId(), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchBroadcastAndSyncBracket(matchId)));
    }

    @Operation(summary = "Walkover — người vắng mặt (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/matches/{matchId}/walkover")
    public ResponseEntity<ApiResponse<MatchResponse>> walkoverOwner(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody CompleteMatchRequest req) {
        matchService.walkover(matchId, req.getWinnerParticipantId(), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchBroadcastAndSyncBracket(matchId)));
    }

    @PostMapping("/manager/matches/{matchId}/walkover")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<MatchResponse>> walkoverManager(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody CompleteMatchRequest req) {
        matchService.walkover(matchId, req.getWinnerParticipantId(), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchBroadcastAndSyncBracket(matchId)));
    }

    @PostMapping("/staff/matches/{matchId}/walkover")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<MatchResponse>> walkoverStaff(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody CompleteMatchRequest req) {
        matchService.walkover(matchId, req.getWinnerParticipantId(), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchBroadcastAndSyncBracket(matchId)));
    }

    /* ─── Score audit / history ──────────────────────────────── */

    @Operation(summary = "Lịch sử cập nhật tỷ số của một trận")
    @GetMapping("/matches/{matchId}/events")
    public ResponseEntity<ApiResponse<List<MatchScoreEventResponse>>> scoreEvents(
            @PathVariable Long matchId) {
        List<MatchScoreEvent> events = matchService.getScoreEvents(matchId);
        List<MatchScoreEventResponse> response = events.stream()
                .map(e -> MatchScoreEventResponse.builder()
                        .id(e.getId())
                        .matchId(e.getMatch().getId())
                        .eventType(e.getEventType())
                        .player1ScoreAfter(e.getPlayer1ScoreAfter())
                        .player2ScoreAfter(e.getPlayer2ScoreAfter())
                        .createdByName(e.getCreatedBy() != null ? e.getCreatedBy().getEmail() : null)
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /* ─── Player: view own matches ───────────────────────────── */

    @Operation(summary = "Lịch thi đấu của tôi (Player)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/player/matches")
    public ResponseEntity<ApiResponse<List<MatchResponse>>> playerMatches(Authentication auth) {
        Long userId = extractUserId(auth);
        List<MatchResponse> myMatches = bracketHelper.getMatchesForPlayer(userId);
        return ResponseEntity.ok(ApiResponse.success(myMatches));
    }

    /* ─── Helpers — delegate to @Transactional service methods ── */

    private List<StageWithMatchesResponse> buildStageResponse(Long tournamentId) {
        return bracketHelper.getStagesWithMatches(tournamentId);
    }

    private List<MatchResponse> buildMatchList(Long tournamentId) {
        return bracketHelper.getMatchesForTournament(tournamentId);
    }

    /** Fetch + broadcast — dùng sau mỗi mutation (score / start) */
    private MatchResponse fetchAndBroadcast(Long matchId) {
        MatchResponse resp = bracketHelper.getMatchResponseById(matchId);
        broadcastService.broadcastMatchUpdate(resp);
        broadcastService.broadcastBracketSync(resp.getTournamentId(),
                bracketHelper.getMatchesForTournament(resp.getTournamentId()));
        return resp;
    }

    /** Complete / walkover — broadcast trận liên quan + sync cả bracket */
    private MatchResponse fetchBroadcastAndSyncBracket(Long matchId) {
        MatchResponse resp = bracketHelper.getMatchResponseById(matchId);
        Long tournamentId = resp.getTournamentId();

        broadcastService.broadcastMatchUpdate(resp);
        if (resp.getNextMatchWinId() != null) {
            broadcastService.broadcastMatchUpdate(
                    bracketHelper.getMatchResponseById(resp.getNextMatchWinId()));
        }
        if (resp.getNextMatchLoseId() != null) {
            broadcastService.broadcastMatchUpdate(
                    bracketHelper.getMatchResponseById(resp.getNextMatchLoseId()));
        }
        broadcastService.broadcastBracketSync(tournamentId,
                bracketHelper.getMatchesForTournament(tournamentId));
        return resp;
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || !(auth.getCredentials() instanceof Long)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return (Long) auth.getCredentials();
    }
}
