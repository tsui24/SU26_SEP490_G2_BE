package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class GameTypeBreakdownItem {
    private String code;
    private String label;
    private long tournamentCount;
    private BigDecimal totalRevenue;
    private Double avgFillRatePct;
}
