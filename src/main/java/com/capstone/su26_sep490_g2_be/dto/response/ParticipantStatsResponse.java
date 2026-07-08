package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ParticipantStatsResponse {
    private long total;
    private long active;
    private long withdrawn;
    private List<StatusCountItem> byStatus;
}
