package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class PaymentHistoryResponse {
    private Long id;
    private Long registrationId;
    private Long tournamentId;
    private String tournamentName;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private String transactionCode;
    private String checkoutUrl;
    private Instant paidAt;
    private Instant createdAt;
}
