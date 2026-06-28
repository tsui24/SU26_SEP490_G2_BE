package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SwapPlayersRequest {

    @NotNull(message = "matchId1 không được để trống")
    private Long matchId1;

    @NotBlank(message = "slot1 không được để trống")
    private String slot1;

    @NotNull(message = "matchId2 không được để trống")
    private Long matchId2;

    @NotBlank(message = "slot2 không được để trống")
    private String slot2;
}
