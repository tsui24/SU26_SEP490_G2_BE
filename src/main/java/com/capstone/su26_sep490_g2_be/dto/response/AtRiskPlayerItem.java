package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class AtRiskPlayerItem {
    private Long userId;
    private String playerName;
    private Instant lastActivityAt;
    private long daysSinceLastActivity;
    private long lifetimeTournaments;
    private BigDecimal totalSpend;
}
