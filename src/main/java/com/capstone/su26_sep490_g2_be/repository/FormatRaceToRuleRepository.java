package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.FormatRaceToRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormatRaceToRuleRepository extends JpaRepository<FormatRaceToRule, Long> {

	List<FormatRaceToRule> findByFormatCodeOrderBySortOrderAsc(String formatCode);

	Optional<FormatRaceToRule> findByFormatCodeAndRoundKey(String formatCode, String roundKey);

	Optional<FormatRaceToRule> findByFormatCodeAndBracketPhaseOrderBySortOrderAsc(String formatCode, String bracketPhase);
}
