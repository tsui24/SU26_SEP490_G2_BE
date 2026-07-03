package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {

	private final TournamentRepository tournamentRepository;

	@Override
	@Transactional
	public Tournament create(Tournament tournament) {
		return tournamentRepository.save(tournament);
	}

	@Override
	public Tournament getById(Long id) {
		return tournamentRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	public Page<Tournament> listByStatus(String status, Pageable pageable) {
		return tournamentRepository.findByStatus(status, pageable);
	}

	@Override
	@Transactional
	public Tournament update(Long id, Tournament data) {
		Tournament existing = getById(id);
		existing.setName(data.getName());
		existing.setDescription(data.getDescription());
		existing.setThumbnailUrl(data.getThumbnailUrl());
		existing.setBannerUrl(data.getBannerUrl());
		existing.setMaxParticipants(data.getMaxParticipants());
		existing.setEntryFee(data.getEntryFee());
		existing.setPrizePool(data.getPrizePool());
		existing.setPrizeDescription(data.getPrizeDescription());
		existing.setRegistrationDeadline(data.getRegistrationDeadline());
		existing.setStartAt(data.getStartAt());
		existing.setEndAt(data.getEndAt());
		return tournamentRepository.save(existing);
	}

	@Override
	@Transactional
	public void updateStatus(Long id, String newStatus) {
		Tournament existing = getById(id);
		existing.setStatus(newStatus);
		tournamentRepository.save(existing);
	}

	@Override
	@Transactional
	public void delete(Long id) {
		Tournament existing = getById(id);
		if (!TournamentStatus.DRAFT.getValue().equals(existing.getStatus())
				&& !TournamentStatus.CANCELLED.getValue().equals(existing.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_OPERATION);
		}
		tournamentRepository.delete(existing);
	}
}
