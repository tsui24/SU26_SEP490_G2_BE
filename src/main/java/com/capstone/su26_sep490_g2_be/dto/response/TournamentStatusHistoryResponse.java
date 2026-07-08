package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class TournamentStatusHistoryResponse {
    private Long id;
    private String fromStatus;
    private String fromStatusLabel;
    private String toStatus;
    private String toStatusLabel;
    private String changeType;
    private String changeTypeLabel;
    private Long changedByUserId;
    private String changedByName;
    private String note;
    private Instant createdAt;
}
