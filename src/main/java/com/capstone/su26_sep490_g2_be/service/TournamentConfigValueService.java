package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TournamentConfigValueService {

	List<TournamentConfigValue> getByTournament(Long tournamentId);

	List<TournamentConfigValue> getByTournamentIds(List<Long> tournamentIds);

	Optional<TournamentConfigValue> getByTournamentAndField(Long tournamentId, String fieldKey);

	/**
	 * Upsert nhiều config value cùng lúc (key → value map).
	 * Dùng khi Owner submit form tạo/sửa config giải.
	 */
	List<TournamentConfigValue> saveAll(Long tournamentId, Map<String, String> values);

	TournamentConfigValue save(Long tournamentId, String fieldKey, String value);

	void deleteByTournament(Long tournamentId);
}
