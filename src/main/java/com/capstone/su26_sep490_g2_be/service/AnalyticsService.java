package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.AnalyticsQueryRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SavedViewRequest;
import com.capstone.su26_sep490_g2_be.dto.response.AnalyticsOverviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.AnalyticsQueryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.GameTypeBreakdownItem;
import com.capstone.su26_sep490_g2_be.dto.response.InsightItem;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PaymentHistoryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerAnalyticsDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerGrowthResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerLeaderboardItem;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerRetentionResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RevenueBreakdownResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SavedViewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SocialEngagementResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentAnalyticsDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentPerformanceItem;
import com.capstone.su26_sep490_g2_be.dto.response.TransactionStatsResponse;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

public interface AnalyticsService {

	/**
	 * branchIds null = không lọc (toàn bộ chi nhánh của owner); rỗng/khác null = chỉ tính các chi nhánh đó.
	 * gameTypes/statuses null hoặc rỗng = không lọc theo loại bi / trạng thái giải đấu.
	 */
	AnalyticsOverviewResponse buildOverview(Long ownerId, Instant from, Instant to, List<Long> branchIds,
			List<String> gameTypes, List<String> statuses);

	RevenueBreakdownResponse buildRevenueBreakdown(Long ownerId, Instant from, Instant to, String granularity,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses);

	List<TournamentPerformanceItem> buildTournamentPerformance(Long ownerId, Instant from, Instant to,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses);

	/**
	 * sortBy: PRIZE|POINTS|TOURNAMENTS|SPEND|WINS|MATCHES|RECENCY (mặc định PRIZE — giữ hành vi cũ).
	 * segment: ALL|NEW|RETURNING|CHAMPION|AT_RISK (mặc định ALL). search: lọc theo tên (contains, không phân biệt hoa/thường).
	 */
	List<PlayerLeaderboardItem> buildPlayerLeaderboard(Long ownerId, Instant from, Instant to, List<Long> branchIds,
			List<String> gameTypes, List<String> statuses, String sortBy, Integer limit, String segment, String search);

	SocialEngagementResponse buildSocialEngagement(Long ownerId, Instant from, Instant to, List<Long> branchIds);

	RegistrationStatsResponse buildRegistrationFunnel(Long ownerId, Instant from, Instant to, String granularity,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses);

	List<GameTypeBreakdownItem> buildGameTypeBreakdown(Long ownerId, Instant from, Instant to, List<Long> branchIds);

	PlayerGrowthResponse buildPlayerGrowth(Long ownerId, Instant from, Instant to, String granularity,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses);

	/**
	 * Retention/loyalty nâng cao — period return rate, phân bố lòng trung thành, danh sách rủi ro rời bỏ.
	 * Xem công thức chi tiết trong AnalyticsServiceImpl#buildPlayerRetention.
	 */
	PlayerRetentionResponse buildPlayerRetention(Long ownerId, Instant from, Instant to, List<Long> branchIds,
			List<String> gameTypes, List<String> statuses);

	/**
	 * Drill-down lịch sử 1 người chơi dưới owner này (đăng ký, thanh toán, kết quả) — không giới hạn theo kỳ lọc trang tổng.
	 * branchIds null = không giới hạn chi nhánh (Owner); khác null = chỉ tính hoạt động trong (các) chi nhánh đó (Manager).
	 */
	PlayerAnalyticsDetailResponse buildPlayerDetail(Long ownerId, Long userId, List<Long> branchIds);

	/**
	 * Drill-down đầy đủ vòng đời 1 giải đấu — không giới hạn theo khoảng thời gian bộ lọc trang tổng.
	 * branchIds null = không giới hạn chi nhánh (Owner); khác null = giải đấu phải thuộc 1 trong các
	 * chi nhánh đó, nếu không sẽ bị từ chối (dùng để enforce Manager chỉ xem giải đấu ở chi nhánh mình).
	 */
	TournamentAnalyticsDetailResponse buildTournamentDetail(Long ownerId, Long tournamentId, List<Long> branchIds);

	TransactionStatsResponse buildTransactionStats(Long ownerId, Instant from, Instant to, String granularity,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses);

	/**
	 * Danh sách giao dịch phân trang, lọc theo trạng thái/1 giải đấu cụ thể/khoảng thời gian —
	 * tournamentId null = xem tất cả giao dịch của mọi giải thuộc owner (view tổng quan).
	 */
	PageResponse<PaymentHistoryResponse> listTransactions(
			Long ownerId, Long tournamentId, String status, Instant from, Instant to, int page, int size,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses);

	/**
	 * Báo cáo doanh thu theo tháng trong khoảng [from,to] tùy chọn (có thể vắt qua nhiều năm) —
	 * không phụ thuộc bộ lọc khoảng thời gian trang tổng.
	 */
	MonthlyReportResponse buildMonthlyReport(Long ownerId, YearMonth from, YearMonth to, List<Long> branchIds);

	/**
	 * Truy vấn phân tích linh hoạt (chọn dimension/metric tùy ý trong whitelist) — xem AnalyticsDimension/AnalyticsMetric.
	 * actorAccessibleBranchIds: null nếu actor (Owner) không bị giới hạn chi nhánh; khác null = danh sách chi nhánh
	 * actor (Manager) được cấp quyền, dùng để validate request.branchIds().
	 */
	AnalyticsQueryResponse runQuery(Long ownerId, List<Long> actorAccessibleBranchIds, AnalyticsQueryRequest request);

	/** Chip insight tính từ dữ liệu thật (không có "AI" giả) — xem AnalyticsServiceImpl#buildInsights. */
	List<InsightItem> buildInsights(Long ownerId, Instant from, Instant to, List<Long> branchIds);

	// ──────────────────────────── saved views ────────────────────────────
	// Thuộc về đúng người dùng đang thao tác (userId = actor Owner/Manager), không phải ownerId —
	// Owner và Manager có view riêng của mình, không dùng chung.

	List<SavedViewResponse> listSavedViews(Long userId);

	SavedViewResponse createSavedView(Long userId, SavedViewRequest request);

	void deleteSavedView(Long userId, Long viewId);
}
