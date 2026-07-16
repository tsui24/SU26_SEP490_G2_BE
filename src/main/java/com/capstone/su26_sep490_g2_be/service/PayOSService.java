package com.capstone.su26_sep490_g2_be.service;

public interface PayOSService {

    /**
     * Tạo link thanh toán PayOS.
     * @return checkoutUrl
     */
    String createPaymentLink(long orderCode, long amountVnd, String description);

    /**
     * Truy vấn trạng thái hiện tại của 1 đơn hàng trên PayOS (PENDING/PAID/CANCELLED/EXPIRED...).
     * @return trạng thái từ PayOS, hoặc null nếu không truy vấn được (lỗi mạng, đơn không tồn tại...).
     */
    String getOrderStatus(long orderCode);

    /**
     * Xác minh chữ ký webhook từ PayOS.
     */
    boolean verifyWebhookSignature(String rawBody);
}
