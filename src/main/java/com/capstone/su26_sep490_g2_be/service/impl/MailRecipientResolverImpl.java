package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.enums.EmailRecipientType;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.MailRecipientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailRecipientResolverImpl implements MailRecipientResolver {

	private final ParticipantRepository participantRepository;
	private final RegistrationRepository registrationRepository;
	private final UserRepository userRepository;
	private final TournamentRepository tournamentRepository;

	@Override
	@Transactional(readOnly = true)
	public List<MailRecipient> resolve(EmailRecipientType type, Long tournamentId, List<String> customEmails) {
		return switch (type) {
			case ALL_PARTICIPANTS -> resolveAllParticipants(tournamentId);
			case REGISTRATION_USER -> resolveApprovedRegistrations(tournamentId);
			case ROLE_STAFF -> resolveStaff(tournamentId);
			case CUSTOM_LIST -> resolveCustomList(customEmails);
			case PLAYER, MATCH_PLAYERS -> {
				log.warn("Recipient type {} phải được resolve tường minh tại nơi publish sự kiện", type);
				yield List.of();
			}
		};
	}

	private List<MailRecipient> resolveAllParticipants(Long tournamentId) {
		if (tournamentId == null) return List.of();
		List<Participant> participants = participantRepository
				.findByTournamentIdAndStatus(tournamentId, ParticipantStatus.ACTIVE.getValue());
		return participants.stream()
				.map(p -> p.getRegistration() != null ? p.getRegistration().getUser() : null)
				.filter(Objects::nonNull)
				.filter(u -> u.getEmail() != null)
				.distinct()
				.map(u -> new MailRecipient(u.getId(), u.getEmail()))
				.toList();
	}

	private List<MailRecipient> resolveApprovedRegistrations(Long tournamentId) {
		if (tournamentId == null) return List.of();
		List<Registration> registrations = registrationRepository
				.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.APPROVED.getValue(), Pageable.unpaged())
				.getContent();
		return registrations.stream()
				.map(Registration::getUser)
				.filter(Objects::nonNull)
				.filter(u -> u.getEmail() != null)
				.distinct()
				.map(u -> new MailRecipient(u.getId(), u.getEmail()))
				.toList();
	}

	private List<MailRecipient> resolveStaff(Long tournamentId) {
		if (tournamentId == null) return List.of();
		Long ownerId = tournamentRepository.findById(tournamentId)
				.map(t -> t.getCreatedBy().getId())
				.orElse(null);
		if (ownerId == null) return List.of();
		return userRepository.searchStaffsByManager(ownerId, null, Pageable.unpaged())
				.stream()
				.map(u -> new MailRecipient(u.getId(), u.getEmail()))
				.toList();
	}

	private List<MailRecipient> resolveCustomList(List<String> customEmails) {
		if (customEmails == null) return List.of();
		return customEmails.stream()
				.filter(e -> e != null && !e.isBlank())
				.distinct()
				.map(e -> new MailRecipient(null, e))
				.toList();
	}
}
