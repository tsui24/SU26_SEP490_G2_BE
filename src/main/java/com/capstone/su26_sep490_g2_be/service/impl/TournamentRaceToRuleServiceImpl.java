package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentRaceToRule;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.FormatRaceToRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRaceToRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.TournamentRaceToRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TournamentRaceToRuleServiceImpl implements TournamentRaceToRuleService {

	private final TournamentRaceToRuleRepository ruleRepository;
	private final FormatRaceToRuleRepository formatRuleRepository;
	private final TournamentRepository tournamentRepository;

	@Override
	public List<TournamentRaceToRule> getByTournament(Long tournamentId) {
		return ruleRepository.findByTournamentId(tournamentId);
	}

	@Override
	@Transactional
	public TournamentRaceToRule upsert(TournamentRaceToRule rule) {
		return ruleRepository.findByTournamentIdAndRoundKey(
						rule.getTournament().getId(), rule.getRoundKey())
				.map(existing -> {
					existing.setRaceTo(rule.getRaceTo());
					existing.setBracketPhase(rule.getBracketPhase());
					return ruleRepository.save(existing);
				})
				.orElseGet(() -> ruleRepository.save(rule));
	}

	@Override
	public int resolveRaceTo(Long tournamentId, String formatCode, String roundKey) {
		// Ưu tiên 1: override theo giải
		return ruleRepository.findByTournamentIdAndRoundKey(tournamentId, roundKey)
				.map(TournamentRaceToRule::getRaceTo)
				// Ưu tiên 2: default của format
				.orElseGet(() -> formatRuleRepository.findByFormatCodeAndRoundKey(formatCode, roundKey)
						.map(r -> r.getRaceTo())
						.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
	}

	@Override
	@Transactional
	public void deleteByTournament(Long tournamentId) {
		ruleRepository.deleteByTournamentId(tournamentId);
	}

	@Override
	@Transactional
	public void deleteByTournamentAndRoundKey(Long tournamentId, String roundKey) {
		ruleRepository.deleteByTournamentIdAndRoundKey(tournamentId, roundKey);
	}
}
