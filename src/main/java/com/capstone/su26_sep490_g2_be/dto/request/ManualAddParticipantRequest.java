package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Min(value = 1, message = "Số hạt giống phải từ 1 trở lên")
    @Schema(description = "Số hạt giống (1 = mạnh nhất) — chỉ có ý nghĩa khi giải chọn phương thức 'Theo hạt giống'. Để trống nếu không xếp.")
    private Integer seedNo;

    @Size(max = 20)
    private String phone;

    @Size(max = 255)
    private String partnerFullName;

    @Size(max = 20)
    private String partnerPhone;

    @Schema(description = "Hạng cơ thủ theo hệ bi-a Việt Nam", example = "B", allowableValues = {"CHAMPION","A","B","C","D","E","F","G","H","I","J","K","L","UNKNOWN"})
    private String billiardRank;

    @Size(max = 500)
    private String note;
}
