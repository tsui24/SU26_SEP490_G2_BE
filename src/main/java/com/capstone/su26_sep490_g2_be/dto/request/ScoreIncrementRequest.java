package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScoreIncrementRequest {

    @NotNull
    @Min(1)
    @Max(2)
    private Integer playerSlot;

    @NotNull
    @Min(-1)
    @Max(1)
    private Integer delta;
}
