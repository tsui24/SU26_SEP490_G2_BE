package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StageWithMatchesResponse {
    private Long id;
    private Long tournamentId;
    private String name;
    private String stageType;
    private Integer orderNo;
    private String status;
    private Integer peRoundNo;
    private Integer peActiveCount;
    private Integer peEliminateCount;
    private List<MatchResponse> matches;
}
