package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Whitelist các metric hợp lệ cho endpoint truy vấn phân tích linh hoạt (AnalyticsService#runQuery).
 * Mỗi metric gắn với đúng 1 {@link FactKind} — grain dữ liệu nó được tính từ đó. Một truy vấn chỉ được
 * chọn các metric CÙNG FactKind (không trộn vd REVENUE theo giao dịch với AVG_FILL_RATE theo giải đấu
 * trong 1 lần group-by, vì sẽ đếm sai/không có ý nghĩa) — AnalyticsServiceImpl validate điều này.
 */
@Getter
@RequiredArgsConstructor
public enum AnalyticsMetric {
	REVENUE("Doanh thu (thanh toán SUCCESS)", FactKind.PAYMENT),
	REFUND_AMOUNT("Số tiền hoàn/hủy/thất bại", FactKind.PAYMENT),
	TRANSACTION_COUNT("Số giao dịch", FactKind.PAYMENT),
	AVG_TRANSACTION_VALUE("Giá trị TB / giao dịch thành công", FactKind.PAYMENT),
	PAYMENT_SUCCESS_RATE("Tỷ lệ thanh toán thành công (%)", FactKind.PAYMENT),

	TOURNAMENT_COUNT("Số giải đấu", FactKind.TOURNAMENT),
	AVG_FILL_RATE("Tỷ lệ lấp đầy TB (%)", FactKind.TOURNAMENT),
	COMPLETION_RATE("Tỷ lệ hoàn thành trận đấu (%)", FactKind.TOURNAMENT),
	PRIZE_POOL("Tổng tiền thưởng (cấu hình)", FactKind.TOURNAMENT),
	OTHER_INCOME("Thu khác (tài trợ, quyên góp...)", FactKind.TOURNAMENT),
	EXPENSE("Chi phí phát sinh", FactKind.TOURNAMENT),
	NET_PROFIT("Lợi nhuận ròng (doanh thu + thu khác - tiền thưởng - chi phí)", FactKind.TOURNAMENT),

	REGISTRATION_COUNT("Số lượt đăng ký", FactKind.REGISTRATION),
	APPROVAL_RATE("Tỷ lệ duyệt đăng ký (%)", FactKind.REGISTRATION),
	UNIQUE_PLAYERS("Số người chơi duy nhất", FactKind.REGISTRATION),
	NEW_PLAYERS("Số người chơi mới", FactKind.REGISTRATION),
	RETURNING_PLAYERS("Số người chơi quay lại", FactKind.REGISTRATION);

	private final String label;
	private final FactKind factKind;

	/** Grain dữ liệu nguồn dùng để nhóm (group-by) — xem AnalyticsServiceImpl#runQuery. */
	public enum FactKind { TOURNAMENT, PAYMENT, REGISTRATION }
}
