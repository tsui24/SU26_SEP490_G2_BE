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

	/** branchIds null = không lọc (toàn bộ chi nhánh của owner); rỗng/khác null = chỉ tính các chi nhánh đó. */
	AnalyticsOverviewResponse buildOverview(Long ownerId, Instant from, Instant to, List<Long> branchIds);

	RevenueBreakdownResponse buildRevenueBreakdown(Long ownerId, Instant from, Instant to, String granularity, List<Long> branchIds);

	List<TournamentPerformanceItem> buildTournamentPerformance(Long ownerId, Instant from, Instant to, List<Long> branchIds);

	List<PlayerLeaderboardItem> buildPlayerLeaderboard(Long ownerId, Instant from, Instant to, List<Long> branchIds);

	SocialEngagementResponse buildSocialEngagement(Long ownerId, Instant from, Instant to, List<Long> branchIds);

	RegistrationStatsResponse buildRegistrationFunnel(Long ownerId, Instant from, Instant to, String granularity, List<Long> branchIds);

	List<GameTypeBreakdownItem> buildGameTypeBreakdown(Long ownerId, Instant from, Instant to, List<Long> branchIds);

	PlayerGrowthResponse buildPlayerGrowth(Long ownerId, Instant from, Instant to, String granularity, List<Long> branchIds);

	/**
	 * Drill-down đầy đủ vòng đời 1 giải đấu — không giới hạn theo khoảng thời gian bộ lọc trang tổng.
	 * branchIds null = không giới hạn chi nhánh (Owner); khác null = giải đấu phải thuộc 1 trong các
	 * chi nhánh đó, nếu không sẽ bị từ chối (dùng để enforce Manager chỉ xem giải đấu ở chi nhánh mình).
	 */
	TournamentAnalyticsDetailResponse buildTournamentDetail(Long ownerId, Long tournamentId, List<Long> branchIds);

	TransactionStatsResponse buildTransactionStats(Long ownerId, Instant from, Instant to, String granularity, List<Long> branchIds);

	/**
	 * Danh sách giao dịch phân trang, lọc theo trạng thái/1 giải đấu cụ thể/khoảng thời gian —
	 * tournamentId null = xem tất cả giao dịch của mọi giải thuộc owner (view tổng quan).
	 */
	PageResponse<PaymentHistoryResponse> listTransactions(
			Long ownerId, Long tournamentId, String status, Instant from, Instant to, int page, int size, List<Long> branchIds);

	/**
	 * Báo cáo doanh thu theo tháng trong khoảng [from,to] tùy chọn (có thể vắt qua nhiều năm) —
	 * không phụ thuộc bộ lọc khoảng thời gian trang tổng.
	 */
	MonthlyReportResponse buildMonthlyReport(Long ownerId, YearMonth from, YearMonth to, List<Long> branchIds);
}
