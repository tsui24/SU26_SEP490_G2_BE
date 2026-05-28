package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.FormatRaceToRule;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.FormatRaceToRuleRepository;
import com.capstone.su26_sep490_g2_be.service.FormatRaceToRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FormatRaceToRuleServiceImpl implements FormatRaceToRuleService {

	private final FormatRaceToRuleRepository repository;

	@Override
	public List<FormatRaceToRule> getByFormat(String formatCode) {
		return repository.findByFormatCodeOrderBySortOrderAsc(formatCode);
	}

	@Override
	public Optional<FormatRaceToRule> findByFormatAndRoundKey(String formatCode, String roundKey) {
		return repository.findByFormatCodeAndRoundKey(formatCode, roundKey);
	}

	@Override
	@Transactional
	public FormatRaceToRule upsert(FormatRaceToRule rule) {
		return repository.findByFormatCodeAndRoundKey(rule.getFormatCode(), rule.getRoundKey())
				.map(existing -> {
					existing.setRaceTo(rule.getRaceTo());
					existing.setBracketPhase(rule.getBracketPhase());
					existing.setSortOrder(rule.getSortOrder());
					return repository.save(existing);
				})
				.orElseGet(() -> repository.save(rule));
	}

	@Override
	@Transactional
	public void delete(Long id) {
		FormatRaceToRule rule = repository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		repository.delete(rule);
	}
}
