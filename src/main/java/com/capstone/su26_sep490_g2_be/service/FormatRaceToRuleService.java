package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.FormatRaceToRule;

import java.util.List;
import java.util.Optional;

public interface FormatRaceToRuleService {

	List<FormatRaceToRule> getByFormat(String formatCode);

	Optional<FormatRaceToRule> findByFormatAndRoundKey(String formatCode, String roundKey);

	FormatRaceToRule upsert(FormatRaceToRule rule);

	void delete(Long id);
}
