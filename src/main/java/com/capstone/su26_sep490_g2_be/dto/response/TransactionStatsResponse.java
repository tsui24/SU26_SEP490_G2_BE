package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class TransactionStatsResponse {
    private long totalTransactions;
    private long successCount;
    private long pendingCount;
    private long failedCount;
    private long cancelledCount;
    private Double successRatePct;
    private BigDecimal totalAmount;
    private BigDecimal avgTransactionValue;
    /** Thời gian trung bình từ lúc tạo giao dịch tới lúc thanh toán thành công (phút). */
    private Double avgConversionMinutes;
    private List<StatusCountItem> byStatus;
    private List<StatusCountItem> byMethod;
    private List<TrendPointResponse> trend;
}
