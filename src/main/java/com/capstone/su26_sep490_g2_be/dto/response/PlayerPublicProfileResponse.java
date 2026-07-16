package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerPublicProfileResponse {

    private Long participantId;
    private Long userId;
    private String displayName;
    private String accountName;
    private String avatarUrl;
    private Integer seedNo;
    private String billiardRank;
    private String bio;
    private List<TournamentAchievementEntry> achievements;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TournamentAchievementEntry {
        private Long tournamentId;
        private String tournamentName;
        private String rankLabel;
        private String note;
        private Integer finalRank;
        private BigDecimal prizeAmount;
        private Integer pointsEarned;
        private Boolean isOfficial;
    }
}
