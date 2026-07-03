package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentResultRepository extends JpaRepository<TournamentResult, Long> {

	List<TournamentResult> findByTournamentIdOrderByFinalRankAsc(Long tournamentId);

	Optional<TournamentResult> findByTournamentIdAndParticipantId(Long tournamentId, Long participantId);

	boolean existsByTournamentIdAndParticipantId(Long tournamentId, Long participantId);

	@Query("""
		SELECT tr FROM TournamentResult tr
		JOIN FETCH tr.tournament
		WHERE tr.participant.id IN (
		    SELECT p.id FROM Participant p
		    JOIN p.registration r
		    WHERE r.user.id = :userId
		)
		ORDER BY tr.finalRank ASC
		""")
	List<TournamentResult> findByParticipantRegistrationUserId(@Param("userId") Long userId);
}
