package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class TournamentStatsResponse {
    private long total;
    private long active;
    private long openForRegistration;
    private long inProgress;
    private long completed;
    private long cancelled;
    private BigDecimal totalPrizePool;
    private List<StatusCountItem> byStatus;
    private List<StatusCountItem> byGameType;
    private List<TrendPointResponse> monthlyTrend;
}
