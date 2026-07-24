package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class MatchResponse {
    private Long id;
    private String matchCode;
    private Long tournamentId;
    private String tournamentName;
    private Long stageId;
    private String stageName;
    private String stageType;
    private String bracketType;
    private Integer roundNo;
    private Integer positionNo;
    private Integer raceTo;
    private String status;
    private Boolean isBye;
    private Instant scheduledAt;
    private Instant estimatedEndAt;
    private Boolean scheduleLocked;

    private ParticipantBriefResponse player1;
    private ParticipantBriefResponse player2;
    private Integer player1Score;
    private Integer player2Score;
    private ParticipantBriefResponse winner;
    private ParticipantBriefResponse loser;

    private Long nextMatchWinId;
    private Long nextMatchLoseId;
    private String winSlot;
    private String loseSlot;

    private Integer tableNo;
    private Long tableId;
    private String tableName;
    private Integer tableNumber;
    private StaffBriefResponse assignedStaff;
}
