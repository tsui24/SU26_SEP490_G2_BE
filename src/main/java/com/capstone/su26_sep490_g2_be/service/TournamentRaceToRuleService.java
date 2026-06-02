package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.FormatRaceToRule;
import com.capstone.su26_sep490_g2_be.entity.TournamentRaceToRule;

import java.util.List;

public interface TournamentRaceToRuleService {

	List<TournamentRaceToRule> getByTournament(Long tournamentId);

	TournamentRaceToRule upsert(TournamentRaceToRule rule);

	/**
	 * Lookup race-to cho một trận đấu.
	 * Ưu tiên: tournament_race_to_rules → fallback format_race_to_rules.
	 */
	int resolveRaceTo(Long tournamentId, String formatCode, String roundKey);

	void deleteByTournament(Long tournamentId);

	void deleteByTournamentAndRoundKey(Long tournamentId, String roundKey);
}
