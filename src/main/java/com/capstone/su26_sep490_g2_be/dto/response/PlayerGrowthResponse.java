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
    /** @deprecated Định nghĩa cũ, thô: returning = tổng số đăng ký cả đời &gt; 1 tính trên người chơi hoạt động trong kỳ.
     *  Giữ lại để tương thích ngược; dùng {@link #periodReturnRatePct} cho số liệu chuẩn hơn. */
    @Deprecated
    private Double repeatPlayerRatePct;
    /** % người chơi hoạt động ở kỳ TRƯỚC (cùng độ dài, liền kề trước "from") cũng hoạt động ở kỳ hiện tại [from,to]. */
    private Double periodReturnRatePct;
}
