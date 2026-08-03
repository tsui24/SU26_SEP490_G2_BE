package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class AnalyticsQueryResponse {

	@Getter
	@Builder
	public static class Row {
		/** tên dimension -> giá trị hiển thị (vd "BRANCH" -> "Chi nhánh Q1"). */
		private Map<String, String> dimensions;
		/** tên metric -> giá trị số (BigDecimal/Long/Double tùy metric). */
		private Map<String, Object> metrics;
	}

	@Getter
	@Builder
	public static class Meta {
		private String from;
		private String to;
		private String granularity;
		private String comparedFrom;
		private String comparedTo;
		private List<String> dimensions;
		private List<String> metrics;
		/** true nếu số dòng bị cắt bớt do vượt "limit" — FE nên gợi ý thu hẹp bộ lọc. */
		private boolean truncated;
	}

	private List<Row> rows;
	/** Tổng toàn bộ tập dữ liệu đã lọc (không group-by) — tên metric -> giá trị. */
	private Map<String, Object> totals;
	/** null nếu comparePreviousPeriod=false. */
	private Map<String, Object> previousPeriodTotals;
	private Meta meta;
}
