package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RegistrationStatsResponse {
    private long total;
    private long pending;
    private long approved;
    private long rejected;
    private long cancelled;
    private List<StatusCountItem> byStatus;
    private List<TrendPointResponse> monthlyTrend;
}
