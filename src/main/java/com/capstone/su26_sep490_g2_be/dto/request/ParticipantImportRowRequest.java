package com.capstone.su26_sep490_g2_be.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParticipantImportRowRequest {

    private String name1;

    private String phone1;

    private String name2;

    private String phone2;
    /** Hạng cơ thủ (BilliardRank.name()), null/UNKNOWN nếu chưa xếp hạng. */
    private String billiardRank;

    /** Số hạt giống — chỉ có ý nghĩa khi giải chọn seedingMethod = SEED. */
    private Integer seedNo;
}
