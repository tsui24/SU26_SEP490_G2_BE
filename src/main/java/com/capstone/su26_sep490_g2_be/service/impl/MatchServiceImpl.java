package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.AssignMatchRequest;
import com.capstone.su26_sep490_g2_be.entity.BilliardTable;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.MatchScoreEvent;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentFormat;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.BilliardTableRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchScoreEventRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final MatchScoreEventRepository scoreEventRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final BilliardTableRepository billiardTableRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MailContextBuilder mailContextBuilder;

    @Override
    public Match getById(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Match> getByStage(Long stageId) {
        return matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stageId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Match> getByTournament(Long tournamentId) {
        return matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournamentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Match> getByParticipant(Long participantId) {
        return matchRepository.findByParticipantId(participantId);
    }

    @Override
    public List<Match> getByTournamentAndStatus(Long tournamentId, String status) {
        return matchRepository.findByTournamentIdAndStatus(tournamentId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Match> getMatchesForReferee(Long refereeUserId, Long tournamentId, String status,
                                            String tournamentName) {
        getUser(refereeUserId);
        String statusParam = (status == null || status.isBlank()) ? null : status.trim();
        String nameParam = (tournamentName == null || tournamentName.isBlank()) ? null : tournamentName.trim();
        return matchRepository.findByAssignedStaffId(refereeUserId, tournamentId, statusParam, nameParam);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertStaffAssigned(Long matchId, Long staffUserId) {
        assertStaffAssignedOnMatch(getById(matchId), staffUserId);
    }

    private void assertStaffAssignedOnMatch(Match match, Long staffUserId) {
        if (match.getAssignedStaff() == null
                || !match.getAssignedStaff().getId().equals(staffUserId)) {
            throw new BusinessException(ErrorCode.MATCH_NOT_ASSIGNED);
        }
    }

    @Override
    @Transactional
    public Match assignMatch(Long matchId, AssignMatchRequest request, Long updatedByUserId) {
        Match match = getById(matchId);
        getUser(updatedByUserId);
        applyAssignment(match, request);
        return matchRepository.save(match);
    }

    @Override
    @Transactional
    public List<Match> bulkAssignMatches(List<Long> matchIds, AssignMatchRequest request, Long updatedByUserId) {
        getUser(updatedByUserId);
        List<Match> matches = matchRepository.findAllById(matchIds);
        List<Match> updated = new ArrayList<>();
        for (Match match : matches) {
            try {
                applyAssignment(match, request);
            } catch (BusinessException ex) {
                // Trận đã resolved (COMPLETED/WALKOVER/BYE) không cho đổi bàn/giờ — bỏ qua, không fail cả batch.
                continue;
            }
            updated.add(match);
        }
        return matchRepository.saveAll(updated);
    }

    /** Áp dụng thay đổi trọng tài/bàn/giờ lên match trong bộ nhớ — chưa save. Dùng chung cho assignMatch & bulkAssignMatches. */
    private void applyAssignment(Match match, AssignMatchRequest request) {
        if (Boolean.TRUE.equals(request.getClearAssignedStaff())) {
            match.setAssignedStaff(null);
        } else if (request.getAssignedStaffId() != null) {
            User staff = getUser(request.getAssignedStaffId());
            if (!"STAFF".equals(staff.getRole().getCode())) {
                throw new BusinessException(ErrorCode.INVALID_EMPLOYEE_ROLE);
            }
            match.setAssignedStaff(staff);
        }

        boolean wantsTableChange = Boolean.TRUE.equals(request.getClearTable())
                || request.getTableId() != null || request.getTableNo() != null;
        boolean wantsScheduleChange = Boolean.TRUE.equals(request.getClearScheduledAt())
                || request.getScheduledAt() != null;

        if ((wantsTableChange || wantsScheduleChange) && MatchStatus.valueOf(match.getStatus()).isResolved()) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }

        if (Boolean.TRUE.equals(request.getClearTable())) {
            match.setTable(null);
            match.setTableNo(null);
        } else if (request.getTableId() != null) {
            BilliardTable table = loadTableForMatch(match, request.getTableId());
            match.setTable(table);
            match.setTableNo(table.getTableNumber());
        } else if (request.getTableNo() != null) {
            match.setTableNo(request.getTableNo());
        }

        if (Boolean.TRUE.equals(request.getClearScheduledAt())) {
            match.setScheduledAt(null);
        } else if (request.getScheduledAt() != null) {
            match.setScheduledAt(request.getScheduledAt());
        }
    }

    /** Bàn phải thuộc cùng owner với chi nhánh tổ chức giải (nếu xác định được) — pool bàn không dùng chéo chuỗi. */
    private BilliardTable loadTableForMatch(Match match, Long tableId) {
        BilliardTable table = billiardTableRepository.findById(tableId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TABLE_NOT_FOUND));
        Branch branch = match.getTournament().getBranch();
        if (branch != null && branch.getOwner() != null
                && !branch.getOwner().getId().equals(table.getOwner().getId())) {
            throw new BusinessException(ErrorCode.TABLE_ACCESS_DENIED);
        }
        return table;
    }

    @Override
    @Transactional
    public Match startMatch(Long matchId, Long updatedByUserId) {
        Match match = getById(matchId);
        assertMatchPlayable(match);
        if (!MatchStatus.PENDING.getValue().equals(match.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
        if (match.getPlayer1() == null || match.getPlayer2() == null) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
        User updatedBy = getUser(updatedByUserId);

        match.setStatus(MatchStatus.IN_PROGRESS.getValue());
        scoreEventRepository.save(MatchScoreEvent.builder()
                .match(match)
                .player1ScoreAfter(0)
                .player2ScoreAfter(0)
                .eventType("MATCH_START")
                .createdBy(updatedBy)
                .build());

        return matchRepository.save(match);
    }

    @Override
    @Transactional
    public Match updateScore(Long matchId, Integer player1Score, Integer player2Score, Long updatedByUserId) {
        Match match = getById(matchId);
        assertMatchPlayable(match);
        if (MatchStatus.COMPLETED.getValue().equals(match.getStatus())
                || MatchStatus.BYE.getValue().equals(match.getStatus())
                || MatchStatus.WALKOVER.getValue().equals(match.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
        User updatedBy = getUser(updatedByUserId);

        match.setPlayer1Score(player1Score);
        match.setPlayer2Score(player2Score);
        match.setStatus(MatchStatus.IN_PROGRESS.getValue());

        scoreEventRepository.save(MatchScoreEvent.builder()
                .match(match)
                .player1ScoreAfter(player1Score)
                .player2ScoreAfter(player2Score)
                .eventType("SCORE_UPDATE")
                .createdBy(updatedBy)
                .build());

        return matchRepository.save(match);
    }

    @Override
    @Transactional
    public Match incrementScore(Long matchId, int playerSlot, int delta, Long updatedByUserId) {
        Match match = getByIdForUpdate(matchId);
        assertStaffAssignedOnMatch(match, updatedByUserId);
        assertMatchPlayable(match);
        assertMatchInProgress(match);
        if (playerSlot != 1 && playerSlot != 2) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST);
        }
        if (delta != 1 && delta != -1) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST);
        }

        User updatedBy = getUser(updatedByUserId);
        int raceTo = match.getRaceTo() != null ? match.getRaceTo() : Integer.MAX_VALUE;

        int p1 = match.getPlayer1Score() != null ? match.getPlayer1Score() : 0;
        int p2 = match.getPlayer2Score() != null ? match.getPlayer2Score() : 0;

        // Khóa cộng điểm khi đã có người đạt raceTo — vẫn cho trừ (hoàn tác)
        if (delta > 0 && (p1 >= raceTo || p2 >= raceTo)) {
            throw new BusinessException(ErrorCode.MATCH_SCORE_LOCKED);
        }

        if (playerSlot == 1) {
            p1 += delta;
        } else {
            p2 += delta;
        }

        if (p1 < 0 || p2 < 0 || p1 > raceTo || p2 > raceTo) {
            throw new BusinessException(ErrorCode.MATCH_SCORE_OUT_OF_RANGE);
        }

        match.setPlayer1Score(p1);
        match.setPlayer2Score(p2);

        scoreEventRepository.save(MatchScoreEvent.builder()
                .match(match)
                .player1ScoreAfter(p1)
                .player2ScoreAfter(p2)
                .eventType("SCORE_UPDATE")
                .createdBy(updatedBy)
                .build());

        return matchRepository.save(match);
    }

    @Override
    @Transactional
    public Match completeMatch(Long matchId, Long winnerParticipantId, Long updatedByUserId) {
        Match match = getById(matchId);
        assertMatchPlayable(match);
        if (MatchStatus.COMPLETED.getValue().equals(match.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
        Participant winner = getParticipant(winnerParticipantId);
        assertWinnerBelongsToMatch(match, winner);
        assertWinnerWhenRaceReached(match, winner);
        Participant loser = determineLoser(match, winner);
        User updatedBy = getUser(updatedByUserId);

        match.setWinner(winner);
        match.setLoser(loser);
        match.setStatus(MatchStatus.COMPLETED.getValue());

        scoreEventRepository.save(MatchScoreEvent.builder()
                .match(match)
                .player1ScoreAfter(match.getPlayer1Score())
                .player2ScoreAfter(match.getPlayer2Score())
                .eventType("MATCH_END")
                .createdBy(updatedBy)
                .build());

        matchRepository.save(match);

        // Auto-advance winner and loser
        advanceParticipants(match, winner, loser);
        publishMatchCompletedEvent(match);

        return match;
    }

    private void publishMatchCompletedEvent(Match match) {
        List<MailRecipient> recipients = Stream.of(match.getPlayer1(), match.getPlayer2())
                .filter(Objects::nonNull)
                .map(p -> p.getRegistration() != null ? p.getRegistration().getUser() : null)
                .filter(Objects::nonNull)
                .filter(u -> u.getEmail() != null)
                .distinct()
                .map(u -> new MailRecipient(u.getId(), u.getEmail()))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        Map<String, Object> variables = new HashMap<>(mailContextBuilder.systemContext());
        mailContextBuilder.putMatch(variables, match);
        eventPublisher.publishEvent(MailDomainEvent.builder()
                .eventType(EmailEventType.MATCH_COMPLETED)
                .tournamentId(match.getTournament().getId())
                .variables(variables)
                .explicitRecipients(recipients)
                .entityKey("MATCH-" + match.getId())
                .build());
    }

    @Override
    @Transactional
    public Match walkover(Long matchId, Long winnerParticipantId, Long updatedByUserId) {
        Match match = getById(matchId);
        assertMatchPlayable(match);
        if (MatchStatus.COMPLETED.getValue().equals(match.getStatus())
                || MatchStatus.WALKOVER.getValue().equals(match.getStatus())
                || MatchStatus.BYE.getValue().equals(match.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
        Participant winner = getParticipant(winnerParticipantId);
        Participant loser = determineLoser(match, winner);
        User updatedBy = getUser(updatedByUserId);

        match.setWinner(winner);
        match.setLoser(loser);
        match.setStatus(MatchStatus.WALKOVER.getValue());

        scoreEventRepository.save(MatchScoreEvent.builder()
                .match(match)
                .player1ScoreAfter(0)
                .player2ScoreAfter(0)
                .eventType(MatchStatus.WALKOVER.getValue())
                .createdBy(updatedBy)
                .build());

        matchRepository.save(match);
        advanceParticipants(match, winner, loser);
        return match;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchScoreEvent> getScoreEvents(Long matchId) {
        getById(matchId);
        return scoreEventRepository.findByMatchIdOrderByCreatedAtAsc(matchId);
    }

    /* ── Winner / loser advancement (UC-41) ── */

    private void advanceParticipants(Match match, Participant winner, Participant loser) {
        // Advance winner
        if (match.getNextMatchWin() != null && winner != null) {
            placeInNextMatch(match.getNextMatchWin(), match.getWinSlot(), winner);
        }
        // Advance loser (Double Elimination)
        if (match.getNextMatchLose() != null && loser != null) {
            placeInNextMatch(match.getNextMatchLose(), match.getLoseSlot(), loser);
        }
    }

    private void placeInNextMatch(Match nextMatch, String slot, Participant participant) {
        Match m = matchRepository.findById(nextMatch.getId()).orElse(nextMatch);
        if ("player1".equals(slot)) {
            m.setPlayer1(participant);
        } else {
            m.setPlayer2(participant);
        }
        // If both players assigned and both are BYE-less, match is ready
        matchRepository.save(m);
        log.info("Placed {} into match {} slot {}", participant.getDisplayName(), m.getId(), slot);
    }

    private Participant determineLoser(Match match, Participant winner) {
        if (match.getPlayer1() == null || match.getPlayer2() == null) return null;
        return winner.getId().equals(match.getPlayer1().getId())
                ? match.getPlayer2()
                : match.getPlayer1();
    }

    private void assertWinnerBelongsToMatch(Match match, Participant winner) {
        Long p1 = match.getPlayer1() != null ? match.getPlayer1().getId() : null;
        Long p2 = match.getPlayer2() != null ? match.getPlayer2().getId() : null;
        if (winner == null
                || (!winner.getId().equals(p1) && !winner.getId().equals(p2))) {
            throw new BusinessException(ErrorCode.MATCH_WINNER_NOT_IN_MATCH);
        }
    }

    /**
     * Khi đã có người đạt raceTo, chỉ cho chốt thắng đúng người đó
     * (nếu cả hai đạt thì cho người điểm cao hơn; hòa bỏ qua check này).
     */
    private void assertWinnerWhenRaceReached(Match match, Participant winner) {
        Integer raceTo = match.getRaceTo();
        if (raceTo == null) return;
        int p1 = match.getPlayer1Score() != null ? match.getPlayer1Score() : 0;
        int p2 = match.getPlayer2Score() != null ? match.getPlayer2Score() : 0;
        boolean p1Reached = p1 >= raceTo;
        boolean p2Reached = p2 >= raceTo;
        if (!p1Reached && !p2Reached) return;

        Long requiredId;
        if (p1Reached && !p2Reached) {
            requiredId = match.getPlayer1().getId();
        } else if (p2Reached && !p1Reached) {
            requiredId = match.getPlayer2().getId();
        } else if (p1 > p2) {
            requiredId = match.getPlayer1().getId();
        } else if (p2 > p1) {
            requiredId = match.getPlayer2().getId();
        } else {
            return; // cả hai đạt & điểm bằng nhau — trọng tài chọn
        }

        if (!winner.getId().equals(requiredId)) {
            throw new BusinessException(ErrorCode.MATCH_WINNER_MUST_BE_RACE_LEADER);
        }
    }

    private Participant getParticipant(Long id) {
        return participantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
    }

    private Match getByIdForUpdate(Long id) {
        return matchRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void assertMatchInProgress(Match match) {
        if (!MatchStatus.IN_PROGRESS.getValue().equals(match.getStatus())) {
            throw new BusinessException(ErrorCode.MATCH_NOT_IN_PROGRESS);
        }
    }

    /**
     * Trận đấu chỉ được thao tác (start/update/complete/walkover) khi giải đã thực sự bắt đầu.
     * Loại kép (DOUBLE_ELIMINATION) là ngoại lệ: các trận vòng DE thi đấu ngay khi bracket ở
     * DRAW_DONE (chưa có nút "Bắt đầu giải đấu" riêng cho pha này) và tiếp tục ở FINAL_BRACKET_READY
     * sau khi điền bracket loại trực tiếp (CUT_TO_SE). Các thể thức còn lại (SINGLE_ELIMINATION,
     * GROUP_PLAYOFF) bắt buộc phải chuyển sang IN_PROGRESS trước.
     */
    private void assertMatchPlayable(Match match) {
        Tournament tournament = match.getTournament();
        String status = tournament.getStatus();

        boolean allowed = TournamentStatus.IN_PROGRESS.getValue().equals(status)
                || TournamentStatus.FINAL_BRACKET_READY.getValue().equals(status)
                || (TournamentFormat.DOUBLE_ELIMINATION.getValue().equals(tournament.getFormat())
                        && TournamentStatus.DRAW_DONE.getValue().equals(status));

        if (!allowed) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
    }
}
