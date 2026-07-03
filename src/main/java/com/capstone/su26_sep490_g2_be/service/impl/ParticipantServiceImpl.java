package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParticipantServiceImpl implements ParticipantService {

	private final ParticipantRepository participantRepository;

	@Override
	@Transactional
	public Participant create(Participant participant) {
		return participantRepository.save(participant);
	}

	@Override
	public Participant getById(Long id) {
		return participantRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	public List<Participant> getByTournament(Long tournamentId) {
		return participantRepository.findByTournamentId(tournamentId);
	}

	@Override
	public List<Participant> getActiveByTournament(Long tournamentId) {
		return participantRepository.findByTournamentIdAndStatus(tournamentId, ParticipantStatus.ACTIVE.getValue());
	}

	@Override
	@Transactional
	public Participant updateStatus(Long id, String status) {
		Participant participant = getById(id);
		participant.setStatus(status);
		return participantRepository.save(participant);
	}

	@Override
	@Transactional
	public void assignSeedNumbers(Long tournamentId) {
		List<Participant> participants = participantRepository.findByTournamentIdAndStatus(tournamentId, ParticipantStatus.ACTIVE.getValue());
		for (int i = 0; i < participants.size(); i++) {
			participants.get(i).setSeedNo(i + 1);
		}
		participantRepository.saveAll(participants);
	}
}
