package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StandingsEntryResponse {
    private Integer rank;
    private Long participantId;
    private String displayName;
    private Integer wins;
    private Integer losses;
    private Integer matchesPlayed;
    private Integer framesWon;
    private Integer framesLost;
    private Integer frameDiff;   // framesWon - framesLost
    private Boolean advancesToPlayoff;
}
