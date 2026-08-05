package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.DrawResultResponse;

import com.capstone.su26_sep490_g2_be.dto.response.StandingsEntryResponse;
import java.util.List;

public interface BracketGenerationService {

    DrawResultResponse generate(Long tournamentId, Long actorUserId);

    void confirmDraw(Long tournamentId, Long actorUserId);

    void swapPlayers(Long tournamentId, Long matchId1, String slot1, Long matchId2, String slot2);

    /**
     * CUT_TO_SE: sau khi tất cả W và L cutoff rounds hoàn thành,
     * điền seSize/2 W survivors + seSize/2 L survivors vào SE bracket.
     * Tournament status → FINAL_BRACKET_READY.
     */
    void populateFinalBracket(Long tournamentId, Long actorUserId);

    /**
     * PROGRESSIVE_ROUND_ROBIN: chuyển sang giai đoạn kế tiếp — tính standings GĐ hiện tại,
     * loại người ngoài top-survivors, rồi sinh lịch RR mới cho nhóm đi tiếp hoặc bracket Playoff.
     */
    void advanceProgressiveStage(Long tournamentId);

    /** Bảng xếp hạng riêng của một giai đoạn (theo stageId) — có head-to-head. */
    List<StandingsEntryResponse> computeStageStandings(Long stageId);
}
