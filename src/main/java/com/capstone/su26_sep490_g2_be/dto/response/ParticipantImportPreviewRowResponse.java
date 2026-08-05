package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParticipantImportPreviewRowResponse {
    private int rowNo;
    private String name1;
    private String phone1;
    private String name2;
    private String phone2;
    /** Hạng cơ thủ (BilliardRank.name()), null/UNKNOWN nếu chưa xếp hạng. */
    private String billiardRank;
    private boolean valid;
    private String error;
}
