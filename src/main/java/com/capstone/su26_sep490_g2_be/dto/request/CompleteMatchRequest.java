package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteMatchRequest {

    @NotNull(message = "Winner participant ID không được để trống")
    private Long winnerParticipantId;
}
