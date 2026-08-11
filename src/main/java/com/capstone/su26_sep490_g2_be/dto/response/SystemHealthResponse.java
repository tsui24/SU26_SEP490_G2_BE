package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SystemHealthResponse {

    // Trạng thái chung
    private String appStatus; // UP | DEGRADED
    private boolean dbConnected;
    private long uptimeSeconds;
    private String javaVersion;
    private String activeProfile;

    // JVM
    private double heapUsedBytes;
    private double heapMaxBytes;
    private double heapUsedPercent;
    private long threadCount;
    private double cpuUsagePercent; // -1 nếu không đo được trên môi trường hiện tại

    // DB pool & kích thước dữ liệu
    private double dbPoolActive;
    private double dbPoolIdle;
    private double dbPoolMax;
    private long dbSizeBytes;
    private long dbTableCount;

    // HTTP traffic — tích lũy từ lúc tiến trình khởi động (in-memory, mất khi restart)
    private long httpTotalRequests;
    private long httpErrorRequests;
    private double httpErrorRatePercent;
    private double httpAvgLatencyMs;
    private double httpMaxLatencyMs;
    private List<StatusCountItem> topErrorEndpoints;
    private List<StatusCountItem> httpRequestsByStatusClass;

    // Bảo mật / đăng nhập — tích lũy từ lúc tiến trình khởi động
    private List<StatusCountItem> authFailuresByReason;

    // Background jobs
    private List<SchedulerJobStatusItem> schedulerJobs;

    // Email pipeline
    private double mailQueueAvgLatencyMs;
}
