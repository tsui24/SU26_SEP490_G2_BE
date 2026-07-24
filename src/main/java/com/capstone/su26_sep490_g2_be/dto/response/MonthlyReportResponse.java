package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class MonthlyReportResponse {
    private BigDecimal totalRevenue;
    private long totalTransactions;
    private long totalNewTournaments;
    private long totalNewRegistrations;
    private List<MonthlyReportItem> months;
}
