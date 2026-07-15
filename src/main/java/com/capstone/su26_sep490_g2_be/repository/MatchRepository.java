package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Match;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
		LEFT JOIN FETCH m.assignedStaff staff
		LEFT JOIN FETCH staff.profile
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
		LEFT JOIN FETCH m.assignedStaff staff
		LEFT JOIN FETCH staff.profile
		WHERE m.tournament.id = :tournamentId
		ORDER BY m.roundNo ASC, m.positionNo ASC
		""")
	List<Match> findByTournamentIdOrderByRoundNoAscPositionNoAsc(@Param("tournamentId") Long tournamentId);

	List<Match> findByTournamentIdAndStatus(Long tournamentId, String status);

	boolean existsByTournamentIdAndStatusNotIn(Long tournamentId, List<String> statuses);

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
		LEFT JOIN FETCH m.assignedStaff staff
		LEFT JOIN FETCH staff.profile
		WHERE m.id = :id
		""")
	java.util.Optional<Match> findByIdWithDetails(@Param("id") Long id);

	@Query("""
		SELECT m FROM Match m
		LEFT JOIN FETCH m.tournament
		LEFT JOIN FETCH m.player1 p1
		LEFT JOIN FETCH p1.registration r1
		LEFT JOIN FETCH r1.user
		LEFT JOIN FETCH m.player2 p2
		LEFT JOIN FETCH p2.registration r2
		LEFT JOIN FETCH r2.user
		WHERE m.status = :status AND m.scheduledAt BETWEEN :from AND :to
		""")
	List<Match> findByStatusAndScheduledAtBetween(
			@Param("status") String status, @Param("from") Instant from, @Param("to") Instant to);

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
		LEFT JOIN FETCH m.assignedStaff staff
		LEFT JOIN FETCH staff.profile
		WHERE m.assignedStaff.id = :staffId
		AND (:tournamentId IS NULL OR m.tournament.id = :tournamentId)
		AND (:status IS NULL OR m.status = :status)
		AND (:tournamentName IS NULL OR LOWER(m.tournament.name) LIKE LOWER(CONCAT('%', :tournamentName, '%')))
		ORDER BY m.tableNo ASC, m.roundNo ASC, m.positionNo ASC
		""")
	List<Match> findByAssignedStaffId(
			@Param("staffId") Long staffId,
			@Param("tournamentId") Long tournamentId,
			@Param("status") String status,
			@Param("tournamentName") String tournamentName);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT m FROM Match m
		LEFT JOIN FETCH m.tournament
		LEFT JOIN FETCH m.assignedStaff
		WHERE m.id = :id
		""")
	java.util.Optional<Match> findByIdForUpdate(@Param("id") Long id);
}
