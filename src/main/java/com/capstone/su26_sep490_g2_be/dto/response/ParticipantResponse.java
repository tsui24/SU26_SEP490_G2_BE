package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ParticipantResponse {
    private Long id;
    private Long tournamentId;
    private String tournamentName;
    private Long registrationId;
    private Long userId;
    private String participantType;
    private String displayName;
    private String phone;
    /** Hạng cơ thủ (BilliardRank.name()), null/UNKNOWN nếu chưa xếp hạng. */
    private String billiardRank;
    private String status;
    private String source;
    private String avtarUrl;
    /** Chỉ có giá trị cho participant đôi/đội — danh sách thành viên trong participant. */
    private List<MemberItem> members;

    @Getter
    @Builder
    public static class MemberItem {
        private String fullName;
        private String phone;
        private String role;
    }
}
