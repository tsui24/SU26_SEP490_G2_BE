package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParticipantBriefResponse {
    private Long id;
    private String displayName;
    /** Hạng cơ thủ (BilliardRank.name()), null/UNKNOWN nếu chưa xếp hạng. */
    private String billiardRank;
    private String avatarUrl;
}
