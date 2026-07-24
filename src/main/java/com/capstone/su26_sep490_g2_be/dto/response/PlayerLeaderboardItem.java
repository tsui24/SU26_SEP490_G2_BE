package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PlayerLeaderboardItem {
    private Long userId;
    private String playerName;
    private long tournamentsPlayed;
    private long championCount;
    private long top3Count;
    private BigDecimal totalPrizeAmount;
    private long totalPoints;
}
