package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

	private final RegistrationRepository registrationRepository;
	private final TournamentRepository tournamentRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional
	public Registration register(Long tournamentId, Long userId, Registration registration) {
		if (registrationRepository.existsByTournamentIdAndUserId(tournamentId, userId)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
		}
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (!"OPEN_FOR_REGISTRATION".equals(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_OPERATION);
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		registration.setTournament(tournament);
		registration.setUser(user);
		registration.setStatus("PENDING_PAYMENT");
		return registrationRepository.save(registration);
	}

	@Override
	public Registration getById(Long id) {
		return registrationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	public Page<Registration> getByTournament(Long tournamentId, Pageable pageable) {
		return registrationRepository.findByTournamentId(tournamentId, pageable);
	}

	@Override
	public Page<Registration> getByTournamentAndStatus(Long tournamentId, String status, Pageable pageable) {
		return registrationRepository.findByTournamentIdAndStatus(tournamentId, status, pageable);
	}

	@Override
	public Page<Registration> getByUser(Long userId, Pageable pageable) {
		return registrationRepository.findByUserId(userId, pageable);
	}

	@Override
	@Transactional
	public Registration approve(Long registrationId, Long approvedByUserId) {
		Registration reg = getById(registrationId);
		User approver = userRepository.findById(approvedByUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		reg.setStatus("APPROVED");
		reg.setApprovedBy(approver);
		reg.setApprovedAt(Instant.now());
		return registrationRepository.save(reg);
	}

	@Override
	@Transactional
	public Registration reject(Long registrationId, String reason) {
		Registration reg = getById(registrationId);
		reg.setStatus("REJECTED");
		reg.setRejectedReason(reason);
		reg.setRejectedAt(Instant.now());
		return registrationRepository.save(reg);
	}

	@Override
	@Transactional
	public void cancel(Long registrationId, Long requestingUserId) {
		Registration reg = getById(registrationId);
		if (!reg.getUser().getId().equals(requestingUserId)) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		reg.setStatus("CANCELLED");
		registrationRepository.save(reg);
	}
}
