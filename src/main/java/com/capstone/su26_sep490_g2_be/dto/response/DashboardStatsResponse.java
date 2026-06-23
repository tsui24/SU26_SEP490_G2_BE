package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DashboardStatsResponse {
    private long totalTournaments;
    private long activeTournaments;
    private long totalRegistrations;
    private long totalParticipants;
    private BigDecimal totalRevenue;
    private long pendingRegistrations;
    private long openTournaments;
}
