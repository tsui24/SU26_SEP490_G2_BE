package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class LabeledAmountItem {
    private Long id;
    private String label;
    private BigDecimal amount;
    private long count;
}
