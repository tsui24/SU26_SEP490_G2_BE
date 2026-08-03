package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Whitelist các chiều (GROUP BY) hợp lệ cho endpoint truy vấn phân tích linh hoạt
 * (AnalyticsService#runQuery). Grain của các chiều này là giải đấu/thanh toán/đăng ký —
 * KHÔNG bao gồm chiều "người chơi" (đã có endpoint riêng /players/leaderboard, /players/retention
 * tối ưu hơn cho việc đó, tránh nhân bản logic và tránh bùng nổ số dòng kết quả).
 */
@Getter
@RequiredArgsConstructor
public enum AnalyticsDimension {
	TIME("Thời gian (theo granularity)"),
	BRANCH("Chi nhánh"),
	TOURNAMENT("Giải đấu"),
	TOURNAMENT_STATUS("Trạng thái giải đấu"),
	GAME_TYPE("Loại bi"),
	PAYMENT_METHOD("Phương thức thanh toán"),
	PAYMENT_STATUS("Trạng thái thanh toán"),
	REGISTRATION_STATUS("Trạng thái đăng ký"),
	/**
	 * Chiều "Nhóm xếp hạng" (Vô địch/Top 3/Khác) trong đề bài KHÔNG được đưa vào whitelist truy vấn
	 * linh hoạt này — grain của rank bucket là 1 kết quả/người chơi/giải, khác hẳn 3 grain
	 * (TOURNAMENT/PAYMENT/REGISTRATION) engine này hỗ trợ, thêm 1 grain riêng chỉ cho 1 chiều là không
	 * đáng — phân tích theo Vô địch/Top 3 đã có sẵn qua bộ lọc segment=CHAMPION của /players/leaderboard.
	 */
	NEW_VS_RETURNING("Người chơi mới / quay lại");

	private final String label;
}
