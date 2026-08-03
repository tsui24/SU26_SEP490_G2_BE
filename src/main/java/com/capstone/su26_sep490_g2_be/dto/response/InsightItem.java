package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

/** Chip insight tính từ số liệu thật (ngưỡng/so sánh đơn giản) — KHÔNG phải AI, xem AnalyticsServiceImpl#buildInsights. */
@Getter
@Builder
public class InsightItem {
	/** POSITIVE|WARNING|INFO — FE dùng để chọn màu chip. */
    private String severity;
    private String message;
}
