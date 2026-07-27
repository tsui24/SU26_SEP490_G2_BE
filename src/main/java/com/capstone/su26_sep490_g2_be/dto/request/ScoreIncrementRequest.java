package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScoreIncrementRequest {

    @NotNull(message = "Vị trí cơ thủ không được để trống")
    @Min(value = 1, message = "Vị trí cơ thủ phải là 1 hoặc 2")
    @Max(value = 2, message = "Vị trí cơ thủ phải là 1 hoặc 2")
    private Integer playerSlot;

    @NotNull(message = "Delta điểm không được để trống")
    @Min(value = -1, message = "Delta điểm chỉ được -1, 0 hoặc 1")
    @Max(value = 1, message = "Delta điểm chỉ được -1, 0 hoặc 1")
    private Integer delta;
}
