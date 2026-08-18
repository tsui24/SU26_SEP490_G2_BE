package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.TournamentFinanceEntryRequest;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentFinanceEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentFinanceSummaryResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface TournamentFinanceService {

	/** Danh sách khoản thu/chi + tổng hợp của 1 giải — Owner/Manager có quyền trên chi nhánh giải đó. */
	TournamentFinanceSummaryResponse getSummary(Long tournamentId, Long actorUserId);

	TournamentFinanceEntryResponse create(Long tournamentId, Long actorUserId, TournamentFinanceEntryRequest request);

	TournamentFinanceEntryResponse update(Long tournamentId, Long entryId, Long actorUserId, TournamentFinanceEntryRequest request);

	void delete(Long tournamentId, Long entryId, Long actorUserId);

	/** Tổng thu khác / tổng chi cho nhiều giải cùng lúc — dùng bởi Analytics để tính lại net profit
	 * mà không phải query từng giải một. Giải không có khoản nào thì không có key trong map trả về. */
	Map<Long, FinanceTotals> sumByTournamentIds(List<Long> tournamentIds);

	record FinanceTotals(BigDecimal income, BigDecimal expense) {
	}
}
