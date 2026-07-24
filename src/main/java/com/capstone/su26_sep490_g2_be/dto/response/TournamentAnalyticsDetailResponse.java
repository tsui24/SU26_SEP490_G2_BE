package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class TournamentAnalyticsDetailResponse {
    private Long id;
    private String name;
    private String branchName;
    private String gameTypeLabel;
    private String formatLabel;
    private String status;
    private String statusLabel;
    private BigDecimal entryFee;
    private BigDecimal prizePool;
    private String prizeDescription;
    private BigDecimal netProfit;
    private Integer maxParticipants;
    private Instant startAt;
    private Instant endAt;
    private Double fillRatePct;

    private TransactionStatsResponse transactionStats;
    private RegistrationStatsResponse registrationStats;
    private ParticipantStatsResponse participantStats;
    private MatchStatsResponse matchStats;
    private SocialEngagementResponse social;
}
