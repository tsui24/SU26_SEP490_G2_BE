package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StatusCountItem {
    private String status;
    private String label;
    private long count;
}
