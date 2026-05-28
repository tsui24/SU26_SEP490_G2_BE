package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.PlayerYearlySummary;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.PlayerYearlySummaryRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.PlayerYearlySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerYearlySummaryServiceImpl implements PlayerYearlySummaryService {

	private final PlayerYearlySummaryRepository summaryRepository;
	private final UserRepository userRepository;

	@Override
	public List<PlayerYearlySummary> getByUser(Long userId) {
		return summaryRepository.findByUserIdOrderByYearDesc(userId);
	}

	@Override
	public PlayerYearlySummary getByUserAndYear(Long userId, Integer year) {
		return summaryRepository.findByUserIdAndYear(userId, year)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	@Transactional
	public PlayerYearlySummary updateAfterTournament(Long userId, Integer year, int finalRank, BigDecimal prizeAmount, int points) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		PlayerYearlySummary summary = summaryRepository.findByUserIdAndYear(userId, year)
				.orElseGet(() -> PlayerYearlySummary.builder().user(user).year(year).build());

		summary.setTournamentsPlayed(summary.getTournamentsPlayed() + 1);
		if (finalRank == 1) summary.setChampionCount(summary.getChampionCount() + 1);
		if (finalRank == 2) summary.setRunnerUpCount(summary.getRunnerUpCount() + 1);
		if (finalRank <= 3) summary.setTop3Count(summary.getTop3Count() + 1);
		summary.setTotalPrizeAmount(summary.getTotalPrizeAmount().add(prizeAmount));
		summary.setTotalPoints(summary.getTotalPoints() + points);

		return summaryRepository.save(summary);
	}
}
