package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.RejectRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SubmitTournamentRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRegistrationResponse;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldValueRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.RegistrationFormService;
import com.capstone.su26_sep490_g2_be.service.RegistrationService;
import com.capstone.su26_sep490_g2_be.util.PageableUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

	private static final List<String> FULL_NAME_KEYS = List.of(
			"player_full_name", "player1_full_name", "full_name");
	private static final List<String> PHONE_KEYS = List.of(
			"player_phone", "player1_phone", "phone");

	private final RegistrationRepository registrationRepository;
	private final TournamentRepository tournamentRepository;
	private final UserRepository userRepository;
	private final RegistrationFormService registrationFormService;
	private final RegistrationFieldValueRepository fieldValueRepository;
	private final RegistrationFieldDefinitionRepository fieldDefinitionRepository;

	@Override
	@Transactional
	public Registration register(Long tournamentId, Long userId, Registration registration) {
		if (registrationRepository.existsByTournamentIdAndUserId(tournamentId, userId)) {
			throw new BusinessException(ErrorCode.REGISTRATION_ALREADY_EXISTS);
		}
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (!"OPEN_FOR_REGISTRATION".equals(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.REGISTRATION_NOT_OPEN);
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		registration.setTournament(tournament);
		registration.setUser(user);
		registration.setStatus("PENDING_PAYMENT");
		return registrationRepository.save(registration);
	}

	@Override
	@Transactional
	public TournamentRegistrationResponse submitRegistration(
			Long tournamentId, Long userId, SubmitTournamentRegistrationRequest request) {
		if (registrationRepository.existsByTournamentIdAndUserId(tournamentId, userId)) {
			throw new BusinessException(ErrorCode.REGISTRATION_ALREADY_EXISTS);
		}
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (!tournament.isRegister()) {
			throw new BusinessException(ErrorCode.INVALID_OPERATION);
		}
		if (!"OPEN_FOR_REGISTRATION".equals(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.REGISTRATION_NOT_OPEN);
		}
		if (tournament.getRegistrationFormTemplateId() == null) {
			throw new BusinessException(ErrorCode.REG_TEMPLATE_REQUIRED);
		}

		registrationFormService.loadActiveTemplate(tournament.getRegistrationFormTemplateId());
		Map<String, String> normalizedValues = registrationFormService.validateAndNormalizeFieldValues(
				tournament.getRegistrationFormTemplateId(), request.getFieldValues());

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		Registration registration = Registration.builder()
				.tournament(tournament)
				.user(user)
				.registrationType(request.getRegistrationType())
				.playerFullName(resolveValue(normalizedValues, FULL_NAME_KEYS, "N/A"))
				.playerPhone(resolveValue(normalizedValues, PHONE_KEYS, "N/A"))
				.note(request.getNote())
				.status("PENDING_PAYMENT")
				.build();
		registration = registrationRepository.save(registration);
		registrationFormService.saveFieldValues(
				registration, tournament.getRegistrationFormTemplateId(), normalizedValues);
		return toResponse(registration);
	}

	@Override
	@Transactional(readOnly = true)
	public TournamentRegistrationResponse getRegistrationDetail(
			Long registrationId, Long requestingUserId, boolean isStaff) {
		Registration registration = getById(registrationId);
		if (!isStaff) {
			if (requestingUserId == null || !registration.getUser().getId().equals(requestingUserId)) {
				throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
			}
		}
		return toResponse(registration);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<TournamentRegistrationResponse> getMyRegistrations(Long userId, int page, int size) {
		Pageable pageable = PageableUtil.create(page, size, "createdAt");
		return PageResponse.of(
				registrationRepository.findByUserId(userId, pageable),
				this::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<TournamentRegistrationResponse> getTournamentRegistrations(
			Long tournamentId, String status, int page, int size) {
		tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		Pageable pageable = PageableUtil.create(page, size, "createdAt");
		Page<Registration> result = (status == null || status.isBlank())
				? registrationRepository.findByTournamentId(tournamentId, pageable)
				: registrationRepository.findByTournamentIdAndStatus(tournamentId, status.trim(), pageable);
		return PageResponse.of(result, this::toResponse);
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
	public TournamentRegistrationResponse approve(Long registrationId, Long approvedByUserId) {
		Registration reg = getById(registrationId);
		User approver = userRepository.findById(approvedByUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		reg.setStatus("APPROVED");
		reg.setApprovedBy(approver);
		reg.setApprovedAt(Instant.now());
		return toResponse(registrationRepository.save(reg));
	}

	@Override
	@Transactional
	public TournamentRegistrationResponse reject(Long registrationId, RejectRegistrationRequest request) {
		Registration reg = getById(registrationId);
		reg.setStatus("REJECTED");
		reg.setRejectedReason(request.getReason());
		reg.setRejectedAt(Instant.now());
		return toResponse(registrationRepository.save(reg));
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

	private TournamentRegistrationResponse toResponse(Registration registration) {
		List<TournamentRegistrationResponse.FieldValueItem> fieldValues =
				fieldValueRepository.findByRegistrationIdOrderByIdAsc(registration.getId()).stream()
						.map(value -> {
							RegistrationFieldDefinition definition = value.getFieldDefinition();
							if (definition == null) {
								definition = fieldDefinitionRepository
										.findById(value.getId().getFieldKey()).orElse(null);
							}
							return TournamentRegistrationResponse.FieldValueItem.builder()
									.fieldKey(value.getId().getFieldKey())
									.label(definition != null ? definition.getLabel() : value.getId().getFieldKey())
									.value(value.getValue())
									.build();
						})
						.toList();

		return TournamentRegistrationResponse.builder()
				.id(registration.getId())
				.tournamentId(registration.getTournament().getId())
				.tournamentName(registration.getTournament().getName())
				.userId(registration.getUser().getId())
				.registrationType(registration.getRegistrationType())
				.playerFullName(registration.getPlayerFullName())
				.playerPhone(registration.getPlayerPhone())
				.status(registration.getStatus())
				.note(registration.getNote())
				.createdAt(registration.getCreatedAt())
				.fieldValues(fieldValues)
				.build();
	}

	private String resolveValue(Map<String, String> values, List<String> keys, String fallback) {
		for (String key : keys) {
			String value = values.get(key);
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return fallback;
	}
}
