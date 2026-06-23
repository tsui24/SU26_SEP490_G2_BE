package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CheckoutResponse {
    private Long paymentId;
    private Long registrationId;
    private Long orderCode;
    private BigDecimal amount;
    private String checkoutUrl;
    private String description;
}
