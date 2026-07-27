package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteMatchRequest {

    @NotNull(message = "Winner participant ID không được để trống")
    private Long winnerParticipantId;

    /** Bắt buộc = true khi kết thúc trận trước khi ai đạt race-to (bỏ cuộc/chấn thương…) — tránh chốt nhầm trận 0-0. */
    private Boolean confirmEarlyEnd;
}
