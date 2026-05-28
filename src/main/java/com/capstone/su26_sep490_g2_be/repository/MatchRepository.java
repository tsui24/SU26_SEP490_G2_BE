package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

	List<Match> findByStageIdOrderByRoundNoAscPositionNoAsc(Long stageId);

	List<Match> findByTournamentIdAndStatus(Long tournamentId, String status);

	@Query("SELECT m FROM Match m WHERE m.tournament.id = :tournamentId AND m.roundNo = :roundNo AND m.stage.id = :stageId ORDER BY m.positionNo ASC")
	List<Match> findByTournamentIdAndStageIdAndRoundNo(
			@Param("tournamentId") Long tournamentId,
			@Param("stageId") Long stageId,
			@Param("roundNo") Integer roundNo);

	@Query("SELECT m FROM Match m WHERE m.player1.id = :participantId OR m.player2.id = :participantId")
	List<Match> findByParticipantId(@Param("participantId") Long participantId);
}
