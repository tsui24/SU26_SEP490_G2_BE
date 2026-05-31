package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Registration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

	boolean existsByTournamentIdAndUserId(Long tournamentId, Long userId);

	Optional<Registration> findByTournamentIdAndUserId(Long tournamentId, Long userId);

	Page<Registration> findByTournamentId(Long tournamentId, Pageable pageable);

	Page<Registration> findByUserId(Long userId, Pageable pageable);

	Page<Registration> findByTournamentIdAndStatus(Long tournamentId, String status, Pageable pageable);
}
