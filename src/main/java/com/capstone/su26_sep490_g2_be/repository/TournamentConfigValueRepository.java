package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValue;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValueId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TournamentConfigValueRepository extends JpaRepository<TournamentConfigValue, TournamentConfigValueId> {

	List<TournamentConfigValue> findByIdTournamentId(Long tournamentId);

	Optional<TournamentConfigValue> findByIdTournamentIdAndIdFieldKey(Long tournamentId, String fieldKey);

	void deleteByIdTournamentId(Long tournamentId);

	void deleteByIdFieldKeyIn(List<String> fieldKeys);
}
