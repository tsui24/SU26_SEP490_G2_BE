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
}
