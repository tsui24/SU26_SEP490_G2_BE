package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DrawResultResponse {
    private Long tournamentId;
    private String tournamentFormat;
    private int participantsUsed;
    private int stagesCreated;
    private int matchesCreated;
    private String newStatus;
    private List<StageWithMatchesResponse> stages;
}
