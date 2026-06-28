package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.MatchScoreEvent;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchScoreEventRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final MatchScoreEventRepository scoreEventRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;

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
    @Transactional
    public Match startMatch(Long matchId, Long updatedByUserId) {
        Match match = getById(matchId);
        // DRAW_DONE = DE/Group matches; FINAL_BRACKET_READY = SE phase matches (CUT_TO_SE)
        String ts = match.getTournament().getStatus();
        if (!"DRAW_DONE".equals(ts) && !"FINAL_BRACKET_READY".equals(ts)) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
        if (!"PENDING".equals(match.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
        if (match.getPlayer1() == null || match.getPlayer2() == null) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
        User updatedBy = getUser(updatedByUserId);

        match.setStatus("IN_PROGRESS");
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
        if ("COMPLETED".equals(match.getStatus()) || "BYE".equals(match.getStatus()) || "WALKOVER".equals(match.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
        User updatedBy = getUser(updatedByUserId);

        match.setPlayer1Score(player1Score);
        match.setPlayer2Score(player2Score);
        match.setStatus("IN_PROGRESS");

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
    public Match completeMatch(Long matchId, Long winnerParticipantId, Long updatedByUserId) {
        Match match = getById(matchId);
        if ("COMPLETED".equals(match.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }
        Participant winner = getParticipant(winnerParticipantId);
        Participant loser = determineLoser(match, winner);
        User updatedBy = getUser(updatedByUserId);

        match.setWinner(winner);
        match.setLoser(loser);
        match.setStatus("COMPLETED");

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

        return match;
    }

    @Override
    @Transactional
    public Match walkover(Long matchId, Long winnerParticipantId, Long updatedByUserId) {
        Match match = getById(matchId);
        Participant winner = getParticipant(winnerParticipantId);
        Participant loser = determineLoser(match, winner);
        User updatedBy = getUser(updatedByUserId);

        match.setWinner(winner);
        match.setLoser(loser);
        match.setStatus("WALKOVER");

        scoreEventRepository.save(MatchScoreEvent.builder()
                .match(match)
                .player1ScoreAfter(0)
                .player2ScoreAfter(0)
                .eventType("WALKOVER")
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

    private Participant getParticipant(Long id) {
        return participantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
    }
}
