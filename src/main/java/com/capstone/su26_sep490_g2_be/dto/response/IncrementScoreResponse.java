package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IncrementScoreResponse {
    private MatchResponse match;
    private Boolean suggestComplete;
    private Long suggestedWinnerId;
}
