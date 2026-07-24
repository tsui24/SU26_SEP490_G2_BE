package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RevenueBreakdownResponse {
    private List<TrendPointResponse> trend;
    private List<LabeledAmountItem> byBranch;
    private List<LabeledAmountItem> byTournament;
    private List<StatusCountItem> byPaymentMethod;
}
