package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

	@Query("""
		SELECT m FROM Match m
		LEFT JOIN FETCH m.tournament
		LEFT JOIN FETCH m.stage
		LEFT JOIN FETCH m.player1
		LEFT JOIN FETCH m.player2
		LEFT JOIN FETCH m.winner
		LEFT JOIN FETCH m.loser
		LEFT JOIN FETCH m.nextMatchWin
		LEFT JOIN FETCH m.nextMatchLose
		WHERE m.stage.id = :stageId
		ORDER BY m.roundNo ASC, m.positionNo ASC
		""")
	List<Match> findByStageIdOrderByRoundNoAscPositionNoAsc(@Param("stageId") Long stageId);

	@Query("""
		SELECT m FROM Match m
		LEFT JOIN FETCH m.tournament
		LEFT JOIN FETCH m.stage
		LEFT JOIN FETCH m.player1
		LEFT JOIN FETCH m.player2
		LEFT JOIN FETCH m.winner
		LEFT JOIN FETCH m.loser
		LEFT JOIN FETCH m.nextMatchWin
		LEFT JOIN FETCH m.nextMatchLose
		WHERE m.tournament.id = :tournamentId
		ORDER BY m.roundNo ASC, m.positionNo ASC
		""")
	List<Match> findByTournamentIdOrderByRoundNoAscPositionNoAsc(@Param("tournamentId") Long tournamentId);

	List<Match> findByTournamentIdAndStatus(Long tournamentId, String status);

	List<Match> findByTournamentIdIn(List<Long> tournamentIds);

	@Query("SELECT m FROM Match m WHERE m.tournament.id = :tournamentId AND m.roundNo = :roundNo AND m.stage.id = :stageId ORDER BY m.positionNo ASC")
	List<Match> findByTournamentIdAndStageIdAndRoundNo(
			@Param("tournamentId") Long tournamentId,
			@Param("stageId") Long stageId,
			@Param("roundNo") Integer roundNo);

	@Query("""
		SELECT m FROM Match m
		LEFT JOIN FETCH m.tournament
		LEFT JOIN FETCH m.stage
		LEFT JOIN FETCH m.player1
		LEFT JOIN FETCH m.player2
		LEFT JOIN FETCH m.winner
		LEFT JOIN FETCH m.loser
		WHERE m.player1.id = :participantId OR m.player2.id = :participantId
		""")
	List<Match> findByParticipantId(@Param("participantId") Long participantId);

	@Query("""
		SELECT m FROM Match m
		LEFT JOIN FETCH m.tournament
		LEFT JOIN FETCH m.stage
		LEFT JOIN FETCH m.player1
		LEFT JOIN FETCH m.player2
		LEFT JOIN FETCH m.winner
		LEFT JOIN FETCH m.loser
		LEFT JOIN FETCH m.nextMatchWin
		LEFT JOIN FETCH m.nextMatchLose
		WHERE m.id = :id
		""")
	java.util.Optional<Match> findByIdWithDetails(@Param("id") Long id);
}
