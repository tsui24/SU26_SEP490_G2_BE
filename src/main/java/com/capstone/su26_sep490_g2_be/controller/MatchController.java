package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CompleteMatchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.AssignMatchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.BulkAssignMatchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SwapPlayersRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateScoreRequest;
import com.capstone.su26_sep490_g2_be.dto.response.StandingsEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.entity.MatchScoreEvent;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.MatchBroadcastService;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import com.capstone.su26_sep490_g2_be.service.TournamentResultService;
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
    private final TournamentResultService tournamentResultService;
    private final com.capstone.su26_sep490_g2_be.repository.TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;

    /* ─── Bracket generation ─────────────────────────────────── */

    @Operation(summary = "Bốc thăm — sinh bracket từ danh sách người tham gia (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/tournaments/{id}/draw")
    public ResponseEntity<ApiResponse<DrawResultResponse>> drawOwner(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bốc thăm thành công", bracketGenerationService.generate(id, extractUserId(auth))));
    }

    @Operation(summary = "Bốc thăm — sinh bracket từ danh sách người tham gia (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/tournaments/{id}/draw")
    public ResponseEntity<ApiResponse<DrawResultResponse>> drawManager(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bốc thăm thành công", bracketGenerationService.generate(id, extractUserId(auth))));
    }

    @Operation(summary = "Huỷ bốc thăm — xoá bracket nháp, DRAW_PREVIEW → REGISTRATION_CLOSED (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/owner/tournaments/{id}/draw")
    public ResponseEntity<ApiResponse<Void>> cancelDrawOwner(Authentication auth, @PathVariable Long id) {
        bracketGenerationService.cancelDraw(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success("Đã huỷ bốc thăm", null));
    }

    @Operation(summary = "Huỷ bốc thăm — xoá bracket nháp, DRAW_PREVIEW → REGISTRATION_CLOSED (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/manager/tournaments/{id}/draw")
    public ResponseEntity<ApiResponse<Void>> cancelDrawManager(Authentication auth, @PathVariable Long id) {
        bracketGenerationService.cancelDraw(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success("Đã huỷ bốc thăm", null));
    }

    @Operation(summary = "Xác nhận bracket — DRAW_PREVIEW → DRAW_DONE (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/tournaments/{id}/draw/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmDrawOwner(Authentication auth, @PathVariable Long id) {
        bracketGenerationService.confirmDraw(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận bracket", null));
    }

    @Operation(summary = "Xác nhận bracket — DRAW_PREVIEW → DRAW_DONE (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/tournaments/{id}/draw/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmDrawManager(Authentication auth, @PathVariable Long id) {
        bracketGenerationService.confirmDraw(id, extractUserId(auth));
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
            Authentication auth, @PathVariable Long id, @Valid @RequestBody SwapPlayersRequest req) {
        assertManagerCanAccessTournament(auth, id);
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
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> stagesManager(
            Authentication auth, @PathVariable Long id) {
        assertManagerCanAccessTournament(auth, id);
        return ResponseEntity.ok(ApiResponse.success(buildStageResponse(id)));
    }

    @Operation(summary = "Danh sách stage + trận đấu — Public (khi tournament cho phép)")
    @GetMapping("/tournaments/{id}/stages")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> stagesPublic(@PathVariable Long id) {
        requirePublicRatio(id);
        return ResponseEntity.ok(ApiResponse.success(buildStageResponse(id)));
    }

    /* ─── CUT_TO_SE: populate SE bracket từ DE survivors ─────── */

    @Operation(summary = "[CUT_TO_SE] Điền SE bracket từ W+L survivors (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/tournaments/{id}/populate-final-bracket")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> populateFinalOwner(Authentication auth, @PathVariable Long id) {
        bracketGenerationService.populateFinalBracket(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success("Đã điền bracket loại trực tiếp", buildStageResponse(id)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/tournaments/{id}/populate-final-bracket")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> populateFinalManager(Authentication auth, @PathVariable Long id) {
        bracketGenerationService.populateFinalBracket(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success("Đã điền bracket loại trực tiếp", buildStageResponse(id)));
    }

    /* ─── Xếp hạng giải đấu ──────────────────────────────────────── */

    /**
     * Bảng xếp hạng tổng hợp theo bracket — dùng cho tab "Xếp hạng" trên FE.
     * Placement tính theo vòng bị loại của bracket loại trực tiếp.
     */
    @Operation(summary = "Xếp hạng giải đấu (theo bracket) — Public")
    @GetMapping("/tournaments/{id}/rankings")
    public ResponseEntity<ApiResponse<TournamentRankingResponse>> rankingsPublic(@PathVariable Long id) {
        requirePublicRatio(id);
        return ResponseEntity.ok(ApiResponse.success(tournamentResultService.getRankings(id)));
    }

    /** Cùng logic rankingsPublic — endpoint cho Owner dashboard. */
    @Operation(summary = "Xếp hạng giải đấu (theo bracket) — Owner")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/owner/tournaments/{id}/rankings")
    public ResponseEntity<ApiResponse<TournamentRankingResponse>> rankingsOwner(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tournamentResultService.getRankings(id)));
    }

    /* ─── PROGRESSIVE_ROUND_ROBIN: chuyển giai đoạn + standings từng GĐ ── */

    @Operation(summary = "[PROGRESSIVE] Chuyển sang giai đoạn tiếp theo (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/tournaments/{id}/advance-stage")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> advanceStageOwner(@PathVariable Long id) {
        bracketGenerationService.advanceProgressiveStage(id);
        return ResponseEntity.ok(ApiResponse.success("Đã chuyển giai đoạn", buildStageResponse(id)));
    }

    @Operation(summary = "[PROGRESSIVE] Chuyển sang giai đoạn tiếp theo (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/tournaments/{id}/advance-stage")
    public ResponseEntity<ApiResponse<List<StageWithMatchesResponse>>> advanceStageManager(
            Authentication auth, @PathVariable Long id) {
        assertManagerCanAccessTournament(auth, id);
        bracketGenerationService.advanceProgressiveStage(id);
        return ResponseEntity.ok(ApiResponse.success("Đã chuyển giai đoạn", buildStageResponse(id)));
    }

    @Operation(summary = "[PROGRESSIVE] Bảng xếp hạng của một giai đoạn (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/owner/tournaments/{id}/stage-standings")
    public ResponseEntity<ApiResponse<List<StandingsEntryResponse>>> stageStandingsOwner(
            @PathVariable Long id, @RequestParam Long stageId) {
        return ResponseEntity.ok(ApiResponse.success(bracketGenerationService.computeStageStandings(stageId)));
    }

    @Operation(summary = "[PROGRESSIVE] Bảng xếp hạng của một giai đoạn (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/manager/tournaments/{id}/stage-standings")
    public ResponseEntity<ApiResponse<List<StandingsEntryResponse>>> stageStandingsManager(
            Authentication auth, @PathVariable Long id, @RequestParam Long stageId) {
        assertManagerCanAccessTournament(auth, id);
        return ResponseEntity.ok(ApiResponse.success(bracketGenerationService.computeStageStandings(stageId)));
    }

    @Operation(summary = "[PROGRESSIVE] Bảng xếp hạng của một giai đoạn — Public")
    @GetMapping("/tournaments/{id}/stage-standings")
    public ResponseEntity<ApiResponse<List<StandingsEntryResponse>>> stageStandingsPublic(
            @PathVariable Long id, @RequestParam Long stageId) {
        requirePublicRatio(id);
        return ResponseEntity.ok(ApiResponse.success(bracketGenerationService.computeStageStandings(stageId)));
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
        requirePublicRatio(id);
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
        Long staffId = extractUserId(auth);
        matchService.assertStaffAssigned(matchId, staffId);
        matchService.startMatch(matchId, staffId);
        return ResponseEntity.ok(ApiResponse.success(fetchAndBroadcast(matchId)));
    }

    @Operation(summary = "Danh sách trọng tài khả dụng cho giải (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/owner/tournaments/{id}/referees")
    public ResponseEntity<ApiResponse<List<StaffBriefResponse>>> refereesOwner(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(matchService.getRefereesForTournament(id)));
    }

    @Operation(summary = "Danh sách trọng tài khả dụng cho giải (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/manager/tournaments/{id}/referees")
    public ResponseEntity<ApiResponse<List<StaffBriefResponse>>> refereesManager(
            Authentication auth, @PathVariable Long id) {
        assertManagerCanAccessTournament(auth, id);
        return ResponseEntity.ok(ApiResponse.success(matchService.getRefereesForTournament(id)));
    }

    @Operation(summary = "Gán trọng tài và/hoặc số bàn (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/owner/matches/{matchId}/assignment")
    public ResponseEntity<ApiResponse<MatchResponse>> assignOwner(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody AssignMatchRequest req) {
        matchService.assignMatch(matchId, req, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchAndBroadcast(matchId)));
    }

    @Operation(summary = "Gán trọng tài và/hoặc số bàn (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/manager/matches/{matchId}/assignment")
    public ResponseEntity<ApiResponse<MatchResponse>> assignManager(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody AssignMatchRequest req) {
        matchService.assignMatch(matchId, req, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchAndBroadcast(matchId)));
    }

    @Operation(summary = "Gán trọng tài/bàn/giờ cho nhiều trận cùng lúc (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/owner/matches/bulk-assignment")
    public ResponseEntity<ApiResponse<List<MatchResponse>>> bulkAssignOwner(
            Authentication auth, @Valid @RequestBody BulkAssignMatchRequest req) {
        return ResponseEntity.ok(ApiResponse.success(bulkAssignAndBroadcast(req, extractUserId(auth))));
    }

    @Operation(summary = "Gán trọng tài/bàn/giờ cho nhiều trận cùng lúc (Manager)")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/manager/matches/bulk-assignment")
    public ResponseEntity<ApiResponse<List<MatchResponse>>> bulkAssignManager(
            Authentication auth, @Valid @RequestBody BulkAssignMatchRequest req) {
        return ResponseEntity.ok(ApiResponse.success(bulkAssignAndBroadcast(req, extractUserId(auth))));
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
        Long staffId = extractUserId(auth);
        matchService.assertStaffAssigned(matchId, staffId);
        matchService.updateScore(matchId, req.getPlayer1Score(), req.getPlayer2Score(), staffId);
        return ResponseEntity.ok(ApiResponse.success(fetchAndBroadcast(matchId)));
    }

    @Operation(summary = "Kết thúc trận — xác nhận người thắng (Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/matches/{matchId}/complete")
    public ResponseEntity<ApiResponse<MatchResponse>> completeOwner(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody CompleteMatchRequest req) {
        matchService.completeMatch(matchId, req.getWinnerParticipantId(), Boolean.TRUE.equals(req.getConfirmEarlyEnd()), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchBroadcastAndSyncBracket(matchId)));
    }

    @PostMapping("/manager/matches/{matchId}/complete")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<MatchResponse>> completeManager(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody CompleteMatchRequest req) {
        matchService.completeMatch(matchId, req.getWinnerParticipantId(), Boolean.TRUE.equals(req.getConfirmEarlyEnd()), extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(fetchBroadcastAndSyncBracket(matchId)));
    }

    @PostMapping("/staff/matches/{matchId}/complete")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<MatchResponse>> completeStaff(
            Authentication auth, @PathVariable Long matchId,
            @Valid @RequestBody CompleteMatchRequest req) {
        Long staffId = extractUserId(auth);
        matchService.assertStaffAssigned(matchId, staffId);
        matchService.completeMatch(matchId, req.getWinnerParticipantId(), Boolean.TRUE.equals(req.getConfirmEarlyEnd()), staffId);
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
        Long staffId = extractUserId(auth);
        matchService.assertStaffAssigned(matchId, staffId);
        matchService.walkover(matchId, req.getWinnerParticipantId(), staffId);
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

    private List<MatchResponse> bulkAssignAndBroadcast(BulkAssignMatchRequest req, Long updatedByUserId) {
        AssignMatchRequest assignRequest = new AssignMatchRequest();
        assignRequest.setAssignedStaffId(req.getAssignedStaffId());
        assignRequest.setClearAssignedStaff(req.getClearAssignedStaff());
        assignRequest.setTableId(req.getTableId());
        assignRequest.setTableNo(req.getTableNo());
        assignRequest.setClearTable(req.getClearTable());
        assignRequest.setScheduledAt(req.getScheduledAt());
        assignRequest.setClearScheduledAt(req.getClearScheduledAt());

        return matchService.bulkAssignMatches(req.getMatchIds(), assignRequest, updatedByUserId).stream()
                .map(m -> fetchAndBroadcast(m.getId()))
                .toList();
    }

    /** Fetch + broadcast match update — dùng sau start / ghi điểm (không sync cả bracket). */
    private MatchResponse fetchAndBroadcast(Long matchId) {
        MatchResponse resp = bracketHelper.getMatchResponseById(matchId);
        broadcastService.broadcastMatchUpdate(resp);
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

    private void requirePublicRatio(Long tournamentId) {
        var tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!Boolean.TRUE.equals(tournament.getIsPublicRatio())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || !(auth.getCredentials() instanceof Long)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return (Long) auth.getCredentials();
    }

    /**
     * Manager chỉ được xem/thao tác các route con của giải đấu (stages, stage-standings, referees,
     * đổi chỗ R1, chuyển giai đoạn) nếu giải thuộc chi nhánh mình được cấp quyền. Draw/confirm/
     * populate-final-bracket tự chặn đúng vì service nhận actorUserId (xem
     * BracketGenerationServiceImpl.assertActorCanAccessTournament) — nhưng nhóm route này gọi
     * thẳng xuống service không nhận actorUserId nên trước đây chưa được chặn ở tầng nào cả, lộ dữ
     * liệu (và cho phép thao tác) của giải đấu chi nhánh khác. Owner giữ nguyên full-access.
     */
    private void assertManagerCanAccessTournament(Authentication auth, Long tournamentId) {
        User actor = userRepository.findById(extractUserId(auth))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Long branchId = tournament.getBranch() != null ? tournament.getBranch().getId() : null;
        if (!branchAccessService.canActorAccessBranch(actor, branchId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
    }
}
