package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SocialEngagementResponse {
    private long totalPosts;
    private long totalLikes;
    private long totalComments;
    private long totalShares;
    private long totalReach;
    private String topPostTournamentName;
    private long topPostReach;
}
