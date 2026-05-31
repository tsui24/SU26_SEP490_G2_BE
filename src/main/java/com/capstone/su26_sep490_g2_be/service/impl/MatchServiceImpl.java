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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
	public List<Match> getByStage(Long stageId) {
		return matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stageId);
	}

	@Override
	public List<Match> getByTournamentAndStatus(Long tournamentId, String status) {
		return matchRepository.findByTournamentIdAndStatus(tournamentId, status);
	}

	@Override
	@Transactional
	public Match updateScore(Long matchId, Integer player1Score, Integer player2Score, Long updatedByUserId) {
		Match match = getById(matchId);
		User updatedBy = getUser(updatedByUserId);

		match.setPlayer1Score(player1Score);
		match.setPlayer2Score(player2Score);
		match.setStatus("IN_PROGRESS");

		scoreEventRepository.save(MatchScoreEvent.builder()
				.match(match)
				.player1ScoreAfter(player1Score)
				.player2ScoreAfter(player2Score)
				.eventType("SCORE_ADD")
				.createdBy(updatedBy)
				.build());

		return matchRepository.save(match);
	}

	@Override
	@Transactional
	public Match completeMatch(Long matchId, Long winnerParticipantId, Long updatedByUserId) {
		Match match = getById(matchId);
		Participant winner = getParticipant(winnerParticipantId);
		Participant loser = winner.getId().equals(match.getPlayer1() != null ? match.getPlayer1().getId() : null)
				? match.getPlayer2()
				: match.getPlayer1();
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

		return matchRepository.save(match);
	}

	@Override
	@Transactional
	public Match walkover(Long matchId, Long winnerParticipantId, Long updatedByUserId) {
		Match match = getById(matchId);
		Participant winner = getParticipant(winnerParticipantId);
		User updatedBy = getUser(updatedByUserId);

		match.setWinner(winner);
		match.setStatus("WALKOVER");

		scoreEventRepository.save(MatchScoreEvent.builder()
				.match(match)
				.player1ScoreAfter(0)
				.player2ScoreAfter(0)
				.eventType("WALKOVER")
				.createdBy(updatedBy)
				.build());

		return matchRepository.save(match);
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
