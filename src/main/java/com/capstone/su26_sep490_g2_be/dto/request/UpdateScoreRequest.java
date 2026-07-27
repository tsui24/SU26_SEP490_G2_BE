package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateScoreRequest {

    @NotNull(message = "Điểm cơ thủ 1 không được để trống")
    @Min(value = 0, message = "Điểm cơ thủ 1 không được âm")
    private Integer player1Score;

    @NotNull(message = "Điểm cơ thủ 2 không được để trống")
    @Min(value = 0, message = "Điểm cơ thủ 2 không được âm")
    private Integer player2Score;
}
