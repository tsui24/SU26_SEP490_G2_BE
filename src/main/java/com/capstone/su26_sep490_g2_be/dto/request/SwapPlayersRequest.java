package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SwapPlayersRequest {

    /**
     * Tên ô hợp lệ. Bắt buộc kiểm tra vì {@code setSlot()} coi MỌI giá trị khác {@code player1}
     * là {@code player2} — gõ sai tên ô sẽ âm thầm thao tác nhầm người thay vì báo lỗi.
     */
    private static final String SLOT_PATTERN = "player1|player2";

    @NotNull(message = "matchId1 không được để trống")
    private Long matchId1;

    @NotBlank(message = "slot1 không được để trống")
    @Pattern(regexp = SLOT_PATTERN, message = "slot1 chỉ nhận giá trị player1 hoặc player2")
    private String slot1;

    @NotNull(message = "matchId2 không được để trống")
    private Long matchId2;

    @NotBlank(message = "slot2 không được để trống")
    @Pattern(regexp = SLOT_PATTERN, message = "slot2 chỉ nhận giá trị player1 hoặc player2")
    private String slot2;
}
