package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Participant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	@EntityGraph(attributePaths = { "tournament", "registration" })
	List<Participant> findByTournamentId(Long tournamentId);

	@EntityGraph(attributePaths = { "tournament", "registration" })
	List<Participant> findByTournamentIdAndStatus(Long tournamentId, String status);

	@EntityGraph(attributePaths = { "tournament", "registration" })
	java.util.Optional<Participant> findById(Long id);

	long countByTournamentIdAndStatus(Long tournamentId, String status);

	boolean existsByRegistrationId(Long registrationId);
}
