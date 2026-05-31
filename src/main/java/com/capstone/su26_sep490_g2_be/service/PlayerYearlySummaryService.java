package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.PlayerYearlySummary;

import java.util.List;

public interface PlayerYearlySummaryService {

	List<PlayerYearlySummary> getByUser(Long userId);

	PlayerYearlySummary getByUserAndYear(Long userId, Integer year);

	PlayerYearlySummary updateAfterTournament(Long userId, Integer year, int finalRank, java.math.BigDecimal prizeAmount, int points);
}
