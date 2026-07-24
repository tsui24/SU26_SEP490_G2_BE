package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlayerGrowthResponse {
    /** Số người chơi có đăng ký ĐẦU TIÊN (tính trên toàn bộ lịch sử với owner này) rơi vào từng kỳ. */
    private List<TrendPointResponse> newPlayersTrend;
    private long activePlayerCount;
    private long returningPlayerCount;
    private Double repeatPlayerRatePct;
}
