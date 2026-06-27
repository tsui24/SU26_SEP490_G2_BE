package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManualAddParticipantRequest {

    @NotBlank(message = "Tên hiển thị không được để trống")
    @Size(max = 255)
    private String displayName;

    @Size(max = 20)
    private String phone;

    @Min(value = 1, message = "Hạt giống phải từ 1 trở lên")
    private Integer seedNo;

    @Size(max = 500)
    private String note;
}
