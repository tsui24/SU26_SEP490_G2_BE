package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.TournamentStage;

import java.util.List;

public interface TournamentStageService {

	List<TournamentStage> getByTournament(Long tournamentId);

	TournamentStage getById(Long id);

	TournamentStage create(TournamentStage stage);

	TournamentStage updateStatus(Long id, String status);
}
