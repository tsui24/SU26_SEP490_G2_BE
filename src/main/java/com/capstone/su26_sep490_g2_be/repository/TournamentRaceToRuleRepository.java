package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.TournamentRaceToRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TournamentRaceToRuleRepository extends JpaRepository<TournamentRaceToRule, Long> {

	List<TournamentRaceToRule> findByTournamentId(Long tournamentId);

	Optional<TournamentRaceToRule> findByTournamentIdAndRoundKey(Long tournamentId, String roundKey);

	void deleteByTournamentId(Long tournamentId);
}
