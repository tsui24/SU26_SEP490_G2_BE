package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class TournamentFinanceEntryResponse {
    private Long id;
    private String entryType;
    private String entryTypeLabel;
    private String label;
    private BigDecimal amount;
    private String note;
    private Instant occurredAt;
    private Long createdByUserId;
    private String createdByName;
    private Instant createdAt;
}
