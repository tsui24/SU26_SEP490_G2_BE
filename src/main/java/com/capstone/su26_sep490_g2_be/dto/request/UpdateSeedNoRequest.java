package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateSeedNoRequest {

    @Min(value = 1, message = "Số hạt giống phải từ 1 trở lên")
    @Schema(description = "Số hạt giống mới (1 = mạnh nhất). Để null để bỏ hạt giống, trả người này về nhóm bốc thăm ngẫu nhiên.")
    private Integer seedNo;
}
