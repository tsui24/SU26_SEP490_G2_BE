package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.AnalyticsOverviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.GameTypeBreakdownItem;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PaymentHistoryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerGrowthResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerLeaderboardItem;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RevenueBreakdownResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SocialEngagementResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentAnalyticsDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentPerformanceItem;
import com.capstone.su26_sep490_g2_be.dto.response.TransactionStatsResponse;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

public interface AnalyticsService {

	AnalyticsOverviewResponse buildOverview(Long ownerId, Instant from, Instant to);

	RevenueBreakdownResponse buildRevenueBreakdown(Long ownerId, Instant from, Instant to, String granularity);

	List<TournamentPerformanceItem> buildTournamentPerformance(Long ownerId, Instant from, Instant to);

	List<PlayerLeaderboardItem> buildPlayerLeaderboard(Long ownerId, Instant from, Instant to);

	SocialEngagementResponse buildSocialEngagement(Long ownerId, Instant from, Instant to);

	RegistrationStatsResponse buildRegistrationFunnel(Long ownerId, Instant from, Instant to, String granularity);

	List<GameTypeBreakdownItem> buildGameTypeBreakdown(Long ownerId, Instant from, Instant to);

	PlayerGrowthResponse buildPlayerGrowth(Long ownerId, Instant from, Instant to, String granularity);

	/** Drill-down đầy đủ vòng đời 1 giải đấu — không giới hạn theo khoảng thời gian bộ lọc trang tổng. */
	TournamentAnalyticsDetailResponse buildTournamentDetail(Long ownerId, Long tournamentId);

	TransactionStatsResponse buildTransactionStats(Long ownerId, Instant from, Instant to, String granularity);

	/**
	 * Danh sách giao dịch phân trang, lọc theo trạng thái/1 giải đấu cụ thể/khoảng thời gian —
	 * tournamentId null = xem tất cả giao dịch của mọi giải thuộc owner (view tổng quan).
	 */
	PageResponse<PaymentHistoryResponse> listTransactions(
			Long ownerId, Long tournamentId, String status, Instant from, Instant to, int page, int size);

	/**
	 * Báo cáo doanh thu theo tháng trong khoảng [from,to] tùy chọn (có thể vắt qua nhiều năm) —
	 * không phụ thuộc bộ lọc khoảng thời gian trang tổng.
	 */
	MonthlyReportResponse buildMonthlyReport(Long ownerId, YearMonth from, YearMonth to);
}
