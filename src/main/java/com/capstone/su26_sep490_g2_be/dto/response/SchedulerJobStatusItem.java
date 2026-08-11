package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SchedulerJobStatusItem {
    private String name;
    private Instant lastRunAt;
    private boolean success;
    private long lastDurationMs;
    private String lastError;
}
