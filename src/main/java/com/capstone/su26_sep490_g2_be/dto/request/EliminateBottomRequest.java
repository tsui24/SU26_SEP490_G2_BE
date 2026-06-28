package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EliminateBottomRequest {

    @NotNull(message = "keepCount không được để trống")
    @Min(value = 2, message = "Phải giữ ít nhất 2 người chơi")
    private Integer keepCount;
}
