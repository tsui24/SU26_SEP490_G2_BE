package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlayerRetentionResponse {
    /** % người chơi hoạt động ở kỳ TRƯỚC cũng hoạt động ở kỳ hiện tại — xem AnalyticsServiceImpl#buildPlayerRetention. */
    private Double periodReturnRatePct;
    private long previousPeriodActivePlayers;
    private long currentPeriodReturningPlayers;
    /** Phân bố số giải đấu cả đời (đăng ký APPROVED) / người chơi: "1 giải", "2-3 giải", "4-6 giải", "7+ giải". */
    private List<StatusCountItem> loyaltyDistribution;
    /** Ngưỡng ngày không hoạt động để tính là "rủi ro rời bỏ" (mặc định 90 ngày). */
    private int atRiskThresholdDays;
    private List<AtRiskPlayerItem> atRiskPlayers;
}
