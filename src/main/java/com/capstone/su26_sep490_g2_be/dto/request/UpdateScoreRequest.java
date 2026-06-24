package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateScoreRequest {

    @NotNull
    @Min(0)
    private Integer player1Score;

    @NotNull
    @Min(0)
    private Integer player2Score;
}
