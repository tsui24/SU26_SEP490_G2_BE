package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TournamentResultRepository extends JpaRepository<TournamentResult, Long> {

	List<TournamentResult> findByTournamentIdOrderByFinalRankAsc(Long tournamentId);

	Optional<TournamentResult> findByTournamentIdAndParticipantId(Long tournamentId, Long participantId);

	boolean existsByTournamentIdAndParticipantId(Long tournamentId, Long participantId);
}
