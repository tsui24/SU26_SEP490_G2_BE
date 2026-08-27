package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.AssignMatchRequest;
import com.capstone.su26_sep490_g2_be.dto.response.StaffBriefResponse;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.MatchScoreEvent;

import java.util.List;

public interface MatchService {

    Match getById(Long id);

    List<Match> getByStage(Long stageId);

    List<Match> getByTournament(Long tournamentId);

    List<Match> getByTournamentAndStatus(Long tournamentId, String status);

    List<Match> getByParticipant(Long participantId);

    List<Match> getMatchesForReferee(Long refereeUserId, Long tournamentId, String status, String tournamentName);

    void assertStaffAssigned(Long matchId, Long staffUserId);

    /** Danh sách trọng tài (STAFF) khả dụng để gán cho trận của giải — chỉ staff thuộc chi nhánh tổ chức. */
    List<StaffBriefResponse> getRefereesForTournament(Long tournamentId);

    Match assignMatch(Long matchId, AssignMatchRequest request, Long updatedByUserId);

    /** Gán bàn/giờ/trọng tài cho nhiều trận cùng lúc. Trận đã resolved (COMPLETED/WALKOVER/BYE) bị bỏ qua, không fail cả batch. */
    List<Match> bulkAssignMatches(List<Long> matchIds, AssignMatchRequest request, Long updatedByUserId);

    Match startMatch(Long matchId, Long updatedByUserId);

    Match updateScore(Long matchId, Integer player1Score, Integer player2Score, Long updatedByUserId);

    Match incrementScore(Long matchId, int playerSlot, int delta, Long updatedByUserId);

    Match completeMatch(Long matchId, Long winnerParticipantId, boolean confirmEarlyEnd, Long updatedByUserId);

    Match walkover(Long matchId, Long winnerParticipantId, Long updatedByUserId);

    List<MatchScoreEvent> getScoreEvents(Long matchId);

    /**
     * Quét toàn hệ thống, tự xử thắng cho các trận L-R1 (Nhánh Thua vòng 1) đang kẹt vì ô còn lại
     * không bao giờ có người (do trận W-R1 song hành là BYE — xem
     * {@code autoResolveIfOtherLoserSlotIsDead}). Chỉ ảnh hưởng dữ liệu đã sinh TRƯỚC khi fix này
     * tồn tại; trận hoàn thành sau khi có fix đã tự chạy đúng qua {@link #completeMatch}/{@link #walkover}.
     * Chạy 1 lần lúc khởi động app — gọi từ {@code DataInitializer}.
     */
    void reconcileDeadLoserSlots();
}
