package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.DrawResultResponse;

import com.capstone.su26_sep490_g2_be.dto.response.StandingsEntryResponse;
import java.util.List;

public interface BracketGenerationService {

    DrawResultResponse generate(Long tournamentId);

    void confirmDraw(Long tournamentId);

    void swapPlayers(Long tournamentId, Long matchId1, String slot1, Long matchId2, String slot2);

    /** Xếp hạng vòng tròn — wins → frameDiff → framesWon */
    List<StandingsEntryResponse> getLeagueStandings(Long tournamentId);

    /** Điền bracket playoff từ kết quả vòng tròn (chỉ khi tất cả trận GROUP đã hoàn thành) */
    void populateLeaguePlayoff(Long tournamentId);

    /**
     * CUT_TO_SE: sau khi tất cả W và L cutoff rounds hoàn thành,
     * điền seSize/2 W survivors + seSize/2 L survivors vào SE bracket.
     * Tournament status → FINAL_BRACKET_READY.
     */
    void populateFinalBracket(Long tournamentId);

    /**
     * GROUP_PLAYOFF progressive elimination: giữ keepCount người top standings,
     * loại bottom → INACTIVE, auto-WALKOVER các trận còn lại của họ.
     */
    void eliminateBottomParticipants(Long tournamentId, int keepCount);
}
