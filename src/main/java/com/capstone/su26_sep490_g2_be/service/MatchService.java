package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.MatchScoreEvent;

import java.util.List;

public interface MatchService {

    Match getById(Long id);

    List<Match> getByStage(Long stageId);

    List<Match> getByTournament(Long tournamentId);

    List<Match> getByTournamentAndStatus(Long tournamentId, String status);

    List<Match> getByParticipant(Long participantId);

    Match startMatch(Long matchId, Long updatedByUserId);

    Match updateScore(Long matchId, Integer player1Score, Integer player2Score, Long updatedByUserId);

    Match completeMatch(Long matchId, Long winnerParticipantId, Long updatedByUserId);

    Match walkover(Long matchId, Long winnerParticipantId, Long updatedByUserId);

    List<MatchScoreEvent> getScoreEvents(Long matchId);
}
