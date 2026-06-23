package com.capstone.su26_sep490_g2_be.service;

public interface PayOSService {

    /**
     * Tạo link thanh toán PayOS.
     * @return checkoutUrl
     */
    String createPaymentLink(long orderCode, long amountVnd, String description);

    /**
     * Xác minh chữ ký webhook từ PayOS.
     */
    boolean verifyWebhookSignature(String rawBody);
}
