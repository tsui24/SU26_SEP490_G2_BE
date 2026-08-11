package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminDashboardStatsResponse {

    // Tài khoản
    private long totalAccounts;
    private long newAccounts30d;
    private long lockedAccounts;
    private List<StatusCountItem> accountsByRole;
    private List<TrendPointResponse> newAccountsTrend;

    // Chi nhánh / CLB
    private long totalBranches;
    private long activeBranches;

    // Giải đấu toàn hệ thống
    private long ongoingTournaments;
    private List<StatusCountItem> tournamentsByStatus;
    private List<TrendPointResponse> newTournamentsTrend;

    // Mức độ sử dụng catalog hệ thống (thể thức / loại bi / form đăng ký)
    private List<StatusCountItem> formatUsage;
    private List<StatusCountItem> gameTypeUsage;
    private List<StatusCountItem> registrationTemplateUsage;

    // Email & automation
    private long totalEmails30d;
    private List<StatusCountItem> emailsByStatus30d;
    private double emailSuccessRate30d;
    private long enabledAutomationRules;
    private long disabledAutomationRules;

    // Cảnh báo / hoạt động gần đây
    private List<DashboardAlertItem> recentAlerts;
}
