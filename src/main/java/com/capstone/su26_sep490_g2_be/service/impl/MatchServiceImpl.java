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
import com.capstone.su26_sep490_g2_be.dto.response.StaffBriefResponse;
import com.capstone.su26_sep490_g2_be.repository.BilliardTableRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchScoreEventRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.MatchSchedulingService;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final TournamentRepository tournamentRepository;
    private final BilliardTableRepository billiardTableRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MailContextBuilder mailContextBuilder;
    private final MatchSchedulingService matchSchedulingService;

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
        Long oldStaffId = match.getAssignedStaff() != null ? match.getAssignedStaff().getId() : null;
        applyAssignment(match, request);
        // Chỉ validate khi gán giờ thủ công (không phải reset/clear)
        if (request.getScheduledAt() != null && !Boolean.TRUE.equals(request.getResetToAuto())) {
            validateManualSchedule(match, Boolean.TRUE.equals(request.getIgnoreTableConflict()));
        }
        // Validate trọng tài không trùng giờ / đang bận (khi thực sự gán trọng tài mới)
        boolean assigningReferee = request.getAssignedStaffId() != null
                && !Boolean.TRUE.equals(request.getResetToAuto())
                && !Boolean.TRUE.equals(request.getClearAssignedStaff());
        if (assigningReferee) {
            validateRefereeAvailability(match);
        }
        matchRepository.save(match);
        // Đồng bộ lại lịch xung quanh (tôn trọng trận đã khóa / trả về auto)
        matchSchedulingService.reschedule(match.getTournament().getId());
        // Thông báo cho trọng tài khi được phân công mới
        Long newStaffId = match.getAssignedStaff() != null ? match.getAssignedStaff().getId() : null;
        if (newStaffId != null && !newStaffId.equals(oldStaffId)) {
            publishRefereeAssignedEvent(match);
        }
        return matchRepository.findById(matchId).orElse(match);
    }

    /**
     * Trọng tài không thể giám sát 2 trận cùng lúc. Xét TOÀN CỤC mọi trận của trọng tài (mọi giải):
     * - Đang có trận IN_PROGRESS (kể cả đã quá giờ dự kiến) → chặn.
     * - Trùng khung giờ [start, end) với trận khác chưa kết thúc → chặn.
     */
    private void validateRefereeAvailability(Match match) {
        User staff = match.getAssignedStaff();
        if (staff == null) return;

        List<Match> refMatches = matchRepository.findByAssignedStaffId(staff.getId(), null, null, null);
        Instant mStart = match.getScheduledAt();
        Instant mEnd = matchEnd(match);

        for (Match other : refMatches) {
            if (other.getId().equals(match.getId())) continue;
            if (MatchStatus.valueOf(other.getStatus()).isResolved()) continue;

            // Đang điều hành trận dở → không rảnh
            if (MatchStatus.IN_PROGRESS.getValue().equals(other.getStatus())) {
                throw new BusinessException(ErrorCode.REFEREE_BUSY_ONGOING);
            }
            // Trùng khung giờ với trận PENDING khác
            if (mStart != null && mEnd != null) {
                Instant oStart = other.getScheduledAt();
                Instant oEnd = matchEnd(other);
                if (oStart != null && oEnd != null && mStart.isBefore(oEnd) && oStart.isBefore(mEnd)) {
                    throw new BusinessException(ErrorCode.REFEREE_TIME_CONFLICT);
                }
            }
        }
    }

    private void publishRefereeAssignedEvent(Match match) {
        User staff = match.getAssignedStaff();
        if (staff == null || staff.getEmail() == null) return;
        Map<String, Object> variables = new HashMap<>(mailContextBuilder.systemContext());
        mailContextBuilder.putMatch(variables, match);
        eventPublisher.publishEvent(MailDomainEvent.builder()
                .eventType(EmailEventType.MATCH_REFEREE_ASSIGNED)
                .tournamentId(match.getTournament().getId())
                .variables(variables)
                .explicitRecipients(List.of(new MailRecipient(staff.getId(), staff.getEmail())))
                .entityKey("MATCH-REFEREE-" + match.getId() + "-" + staff.getId())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffBriefResponse> getRefereesForTournament(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Long branchId = tournament.getBranch() != null ? tournament.getBranch().getId() : null;
        if (branchId == null) return List.of();
        return userRepository.findActiveStaffByBranch(branchId).stream()
                .map(u -> StaffBriefResponse.builder()
                        .id(u.getId())
                        .displayName(staffDisplayName(u))
                        .build())
                .toList();
    }

    private String staffDisplayName(User u) {
        try {
            if (u.getProfile() != null) {
                if (u.getProfile().getDisplayName() != null && !u.getProfile().getDisplayName().isBlank())
                    return u.getProfile().getDisplayName();
                if (u.getProfile().getFullName() != null && !u.getProfile().getFullName().isBlank())
                    return u.getProfile().getFullName();
            }
        } catch (Exception ignored) {}
        return u.getEmail();
    }

    /**
     * Validate khi gán bàn/giờ thủ công cho 1 trận:
     * - KHÔNG cho xếp trước khi các trận nguồn (feeder — VD tứ kết/bán kết) kết thúc.
     * - Cảnh báo (chặn trừ khi ignoreTableConflict) nếu trùng bàn + khung giờ với trận khác.
     */
    private void validateManualSchedule(Match match, boolean ignoreTableConflict) {
        Instant start = match.getScheduledAt();
        if (start == null) return;
        Instant end = match.getEstimatedEndAt() != null
                ? match.getEstimatedEndAt()
                : start.plusSeconds(matchSchedulingService.estimateDurationMinutes(match) * 60);

        // 0) Không được xếp trước giờ bắt đầu giải
        Instant tournamentStart = match.getTournament().getStartAt();
        if (tournamentStart != null && start.isBefore(tournamentStart)) {
            throw new BusinessException(ErrorCode.MATCH_SCHEDULE_BEFORE_START);
        }

        List<Match> all = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(
                match.getTournament().getId());

        // 1) Phụ thuộc: không được bắt đầu trước khi feeder kết thúc
        for (Match other : all) {
            if (other.getId().equals(match.getId())) continue;
            boolean feeder = (other.getNextMatchWin() != null && match.getId().equals(other.getNextMatchWin().getId()))
                    || (other.getNextMatchLose() != null && match.getId().equals(other.getNextMatchLose().getId()));
            if (!feeder) continue;
            Instant feederEnd = matchEnd(other);
            if (feederEnd != null && start.isBefore(feederEnd)) {
                throw new BusinessException(ErrorCode.MATCH_SCHEDULE_BEFORE_FEEDER);
            }
        }

        // 2) Trùng bàn + giờ (chồng khung giờ) — chỉ chặn nếu owner chưa xác nhận bỏ qua
        if (!ignoreTableConflict && match.getTableNo() != null) {
            for (Match other : all) {
                if (other.getId().equals(match.getId())) continue;
                if (MatchStatus.valueOf(other.getStatus()).isResolved()) continue;
                if (!Objects.equals(other.getTableNo(), match.getTableNo())) continue;
                Instant oStart = other.getScheduledAt();
                Instant oEnd = matchEnd(other);
                if (oStart == null || oEnd == null) continue;
                if (start.isBefore(oEnd) && oStart.isBefore(end)) { // [start,end) giao [oStart,oEnd)
                    throw new BusinessException(ErrorCode.MATCH_TABLE_TIME_CONFLICT);
                }
            }
        }
    }

    /** Giờ kết thúc (dự kiến hoặc suy ra) của 1 trận, dùng cho check phụ thuộc/trùng giờ. */
    private Instant matchEnd(Match m) {
        if (m.getEstimatedEndAt() != null) return m.getEstimatedEndAt();
        if (m.getScheduledAt() != null) {
            return m.getScheduledAt().plusSeconds(matchSchedulingService.estimateDurationMinutes(m) * 60);
        }
        return null;
    }

    @Override
    @Transactional
    public List<Match> bulkAssignMatches(List<Long> matchIds, AssignMatchRequest request, Long updatedByUserId) {
        getUser(updatedByUserId);
        List<Match> matches = matchRepository.findAllById(matchIds);
        List<Match> updated = new ArrayList<>();
        Long tournamentId = null;
        for (Match match : matches) {
            try {
                applyAssignment(match, request);
            } catch (BusinessException ex) {
                // Trận đã resolved (COMPLETED/WALKOVER/BYE) không cho đổi bàn/giờ — bỏ qua, không fail cả batch.
                continue;
            }
            updated.add(match);
            tournamentId = match.getTournament().getId();
        }
        List<Match> saved = matchRepository.saveAll(updated);
        if (tournamentId != null) {
            matchSchedulingService.reschedule(tournamentId);
        }
        return saved;
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
            // Trọng tài phải thuộc chi nhánh tổ chức giải (nếu giải có chi nhánh)
            Branch tBranch = match.getTournament().getBranch();
            if (tBranch != null) {
                Long staffBranchId = staff.getBranch() != null ? staff.getBranch().getId() : null;
                if (!tBranch.getId().equals(staffBranchId)) {
                    throw new BusinessException(ErrorCode.REFEREE_NOT_IN_BRANCH);
                }
            }
            match.setAssignedStaff(staff);
        }

        // Trả trận về chế độ tự động: bỏ khóa, auto-scheduler sẽ tính lại bàn/giờ.
        if (Boolean.TRUE.equals(request.getResetToAuto())) {
            match.setScheduleLocked(false);
            return;
        }

        boolean wantsTableChange = Boolean.TRUE.equals(request.getClearTable()) || request.getTableNo() != null;
        boolean wantsScheduleChange = Boolean.TRUE.equals(request.getClearScheduledAt())
                || request.getScheduledAt() != null;

        if ((wantsTableChange || wantsScheduleChange) && MatchStatus.valueOf(match.getStatus()).isResolved()) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }

        // Bàn: chỉ dùng số bàn 1..tableCount (không còn phụ thuộc pool BilliardTable)
        if (Boolean.TRUE.equals(request.getClearTable())) {
            match.setTableNo(null);
            match.setTable(null);
        } else if (request.getTableNo() != null) {
            Integer tableCount = match.getTournament().getTableCount();
            int max = (tableCount != null && tableCount > 0) ? tableCount : 1;
            if (request.getTableNo() < 1 || request.getTableNo() > max) {
                throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST);
            }
            match.setTableNo(request.getTableNo());
        }

        if (Boolean.TRUE.equals(request.getClearScheduledAt())) {
            match.setScheduledAt(null);
            match.setEstimatedEndAt(null);
        } else if (request.getScheduledAt() != null) {
            match.setScheduledAt(request.getScheduledAt());
            match.setEstimatedEndAt(request.getScheduledAt()
                    .plusSeconds(matchSchedulingService.estimateDurationMinutes(match) * 60));
        }

        // Chỉnh bàn/giờ thủ công → khóa lịch, auto không ghi đè
        if (wantsTableChange || wantsScheduleChange) {
            match.setScheduleLocked(true);
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

        // Bàn vừa trống + trận vừa đủ người → xếp lại giờ dự kiến (chỉ lùi, không nhảy sớm)
        matchSchedulingService.reschedule(match.getTournament().getId());

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
        matchSchedulingService.reschedule(match.getTournament().getId());
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
