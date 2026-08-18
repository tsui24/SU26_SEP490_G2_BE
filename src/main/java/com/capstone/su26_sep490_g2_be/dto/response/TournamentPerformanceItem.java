package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class TournamentPerformanceItem {
    private Long id;
    private String name;
    private String branchName;
    private long participants;
    private Integer maxParticipants;
    private Double fillRatePct;
    private BigDecimal revenue;
    private BigDecimal prizePool;
    private BigDecimal otherIncome;
    private BigDecimal expense;
    private BigDecimal netProfit;
    private String status;
    private String statusLabel;
    private Double completionRatePct;
}
