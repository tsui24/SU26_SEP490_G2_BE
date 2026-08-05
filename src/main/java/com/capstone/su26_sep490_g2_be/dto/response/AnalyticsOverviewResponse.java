package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AnalyticsOverviewResponse {
    private BigDecimal totalRevenue;
    private BigDecimal revenuePrevPeriod;
    private Double revenueGrowthPct;
    private long totalTournaments;
    private Double avgFillRatePct;
    private long totalUniquePlayers;
    private String topTournamentName;
    private BigDecimal topTournamentRevenue;
    private String topBranchName;
    private int branchCount;

    /** Doanh thu trung bình / 1 người chơi duy nhất trong kỳ (ARPU) = totalRevenue / totalUniquePlayers. */
    private BigDecimal arpu;
    /** % giao dịch SUCCESS trên tổng số giao dịch phát sinh trong kỳ. */
    private Double paymentSuccessRatePct;
    /** Số người chơi có hoạt động trước đây nhưng không hoạt động > 90 ngày tính đến "to" — xem PlayerRetentionResponse. */
    private long atRiskPlayerCount;
    /** % người chơi hoạt động ở kỳ TRƯỚC cũng quay lại hoạt động ở kỳ hiện tại — xem PlayerRetentionResponse. */
    private Double periodReturnRatePct;
}
