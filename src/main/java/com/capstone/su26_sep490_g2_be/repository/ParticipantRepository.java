package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	List<Participant> findByTournamentId(Long tournamentId);

	List<Participant> findByTournamentIdAndStatus(Long tournamentId, String status);

	boolean existsByRegistrationId(Long registrationId);
}
