package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class RevenueStatsResponse {
    private BigDecimal totalRevenue;
    private long successCount;
    private long pendingCount;
    private long failedCount;
    private BigDecimal avgTicketValue;
    private List<StatusCountItem> byStatus;
    private List<TrendPointResponse> monthlyTrend;
}
