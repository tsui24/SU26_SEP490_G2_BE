package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class DashboardAlertItem {
    private String type;
    private String title;
    private String detail;
    private Instant occurredAt;
}
