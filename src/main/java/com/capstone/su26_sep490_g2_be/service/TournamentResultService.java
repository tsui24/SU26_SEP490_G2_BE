package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.TournamentResult;

import java.util.List;

public interface TournamentResultService {

	List<TournamentResult> getByTournament(Long tournamentId);

	TournamentResult record(TournamentResult result);

	void finalizeTournamentResults(Long tournamentId, Long recordedByUserId);
}
