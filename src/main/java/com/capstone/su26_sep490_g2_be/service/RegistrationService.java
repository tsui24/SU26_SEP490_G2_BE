package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.Registration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RegistrationService {

	Registration register(Long tournamentId, Long userId, Registration registration);

	Registration getById(Long id);

	Page<Registration> getByTournament(Long tournamentId, Pageable pageable);

	Page<Registration> getByTournamentAndStatus(Long tournamentId, String status, Pageable pageable);

	Page<Registration> getByUser(Long userId, Pageable pageable);

	Registration approve(Long registrationId, Long approvedByUserId);

	Registration reject(Long registrationId, String reason);

	void cancel(Long registrationId, Long requestingUserId);
}
