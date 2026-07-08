package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MatchStatsResponse {
    private long total;
    private long completed;
    private long inProgress;
    private long pending;
    private double completionRate;
    private List<StatusCountItem> byStatus;
}
