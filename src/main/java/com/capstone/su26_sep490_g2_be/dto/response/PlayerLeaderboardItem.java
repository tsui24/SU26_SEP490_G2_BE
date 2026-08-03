package com.capstone.su26_sep490_g2_be.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class PlayerLeaderboardItem {
    private Long userId;
    private String playerName;
    /** Số giải đấu đã tham gia chính thức (đăng ký APPROVED) rơi vào kỳ lọc [from,to]. */
    private long tournamentsPlayed;
    private long championCount;
    private long top3Count;
    private BigDecimal totalPrizeAmount;
    private long totalPoints;

    /** Tổng chi tiêu (thanh toán SUCCESS) trong kỳ. */
    private BigDecimal totalSpend;
    /** Số trận đã đấu / thắng — tính trên toàn bộ giải đấu đang được lọc theo chi nhánh/loại bi/trạng thái,
     *  KHÔNG lọc thêm theo khoảng ngày (trận đấu không có mốc thời gian "diễn ra" đáng tin cậy riêng biệt). */
    private long matchesPlayed;
    private long matchesWon;
    private Double winRatePct;
    /** Số giải đấu tham gia chính thức CẢ ĐỜI với chủ sân này (không giới hạn theo kỳ lọc). */
    private long lifetimeTournaments;
    private Instant firstSeenAt;
    private Instant lastActivityAt;
    private Long daysSinceLastActivity;
    /** true nếu lần đăng ký ĐẦU TIÊN cả đời rơi vào kỳ lọc hiện tại. */
    @JsonProperty("isNewPlayer")
    private boolean isNewPlayer;
    /** true nếu có hoạt động trước kỳ lọc VÀ cũng có hoạt động trong kỳ lọc. */
    @JsonProperty("isReturning")
    private boolean isReturning;
}
