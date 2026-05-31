package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.TournamentStage;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.TournamentStageRepository;
import com.capstone.su26_sep490_g2_be.service.TournamentStageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TournamentStageServiceImpl implements TournamentStageService {

	private final TournamentStageRepository stageRepository;

	@Override
	public List<TournamentStage> getByTournament(Long tournamentId) {
		return stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
	}

	@Override
	public TournamentStage getById(Long id) {
		return stageRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	@Transactional
	public TournamentStage create(TournamentStage stage) {
		return stageRepository.save(stage);
	}

	@Override
	@Transactional
	public TournamentStage updateStatus(Long id, String status) {
		TournamentStage stage = getById(id);
		stage.setStatus(status);
		return stageRepository.save(stage);
	}
}
