package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class MatchScoreEventResponse {
    private Long id;
    private Long matchId;
    private String eventType;
    private Integer player1ScoreAfter;
    private Integer player2ScoreAfter;
    private String createdByName;
    private Instant createdAt;
}
