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

    private Integer seedNo;
}
