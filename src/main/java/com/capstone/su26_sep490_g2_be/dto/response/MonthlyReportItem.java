package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MonthlyReportItem {
    private int year;
    private int month;
    private String monthLabel;
    private BigDecimal revenue;
    private long transactionCount;
    private long newTournaments;
    private long newRegistrations;
}
