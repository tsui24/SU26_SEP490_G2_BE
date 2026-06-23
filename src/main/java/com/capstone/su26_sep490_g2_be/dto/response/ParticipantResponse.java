package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParticipantResponse {
    private Long id;
    private Long tournamentId;
    private String tournamentName;
    private Long registrationId;
    private String participantType;
    private String displayName;
    private String phone;
    private Integer seedNo;
    private String status;
    private String source;
}
