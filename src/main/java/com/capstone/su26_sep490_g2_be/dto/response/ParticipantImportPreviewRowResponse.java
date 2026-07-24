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
    private Integer seedNo;
    private boolean valid;
    private String error;
}
