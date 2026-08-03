package com.capstone.su26_sep490_g2_be.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body cho endpoint truy vấn phân tích linh hoạt POST /{scope}/analytics/query.
 * Xem AnalyticsDimension/AnalyticsMetric cho whitelist hợp lệ, và
 * AnalyticsServiceImpl#runQuery cho logic nhóm/validate.
 */
@Getter
@Setter
public class AnalyticsQueryRequest {

	/** yyyy-MM-dd, bỏ trống = 12 tháng gần nhất (giống các endpoint cố định khác). */
	private String from;
	private String to;

	/** day|week|month — chỉ có ý nghĩa khi dimensions chứa TIME. Mặc định "month". */
	private String granularity;

	/** Bỏ trống/null = không giới hạn (Owner: toàn chuỗi; Manager: toàn bộ chi nhánh được cấp quyền). */
	private List<Long> branchIds;

	private Filters filters;

	/** Tên các AnalyticsDimension muốn group-by, ít nhất 1 phần tử. */
	private List<String> dimensions;

	/** Tên các AnalyticsMetric muốn tính, ít nhất 1 phần tử, phải cùng FactKind. */
	private List<String> metrics;

	/** Tên 1 metric trong "metrics" dùng để sort kết quả (mặc định metric đầu tiên). */
	private String sortBy;

	/** ASC|DESC, mặc định DESC. */
	private String sortDir;

	/** Số dòng tối đa trả về, mặc định 50, tối đa 500. */
	private Integer limit;

	/** true = tính thêm totals của kỳ liền trước (cùng độ dài) để so sánh. */
	private boolean comparePreviousPeriod;

	@Getter
	@Setter
	public static class Filters {
		private List<String> gameTypes;
		private List<String> tournamentStatuses;
		private List<String> paymentStatuses;
		private List<String> paymentMethods;
		private List<String> registrationStatuses;
		private List<Long> tournamentIds;
		/** ALL|NEW|RETURNING — chỉ áp dụng khi FactKind của các metric đang chọn là REGISTRATION. */
		private String playerSegment;
		private BigDecimal minRevenue;
		private BigDecimal maxRevenue;
	}
}
