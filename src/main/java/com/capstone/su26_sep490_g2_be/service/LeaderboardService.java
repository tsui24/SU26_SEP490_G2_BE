package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.LeaderboardEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.enums.LeaderboardPeriod;

/**
 * Bảng xếp hạng điểm tích lũy cơ thủ — gộp {@code tournament_results.points_earned} theo kỳ.
 */
public interface LeaderboardService {

	/**
	 * Xếp hạng cơ thủ theo tổng điểm trong một kỳ.
	 *
	 * @param period  kỳ thống kê (tháng/quý/năm/tất cả)
	 * @param year    năm áp dụng, null = năm hiện tại
	 * @param quarter quý 1-4 (chỉ dùng khi period = QUARTER), null = quý hiện tại
	 * @param month   tháng 1-12 (chỉ dùng khi period = MONTH), null = tháng hiện tại
	 */
	PageResponse<LeaderboardEntryResponse> getLeaderboard(
			LeaderboardPeriod period, Integer year, Integer quarter, Integer month, int page, int size);

	/**
	 * Tính lại {@code points_earned} cho toàn bộ kết quả của các giải đã COMPLETED.
	 *
	 * <p>Cần thiết vì các giải chốt trước khi có công thức điểm đang lưu 0. Idempotent.
	 *
	 * @return số dòng đã cập nhật
	 */
	int recalculatePoints();
}
