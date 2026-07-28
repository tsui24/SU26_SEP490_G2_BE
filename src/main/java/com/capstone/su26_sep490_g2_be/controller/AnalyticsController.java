package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.AnalyticsOverviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.GameTypeBreakdownItem;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PaymentHistoryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerGrowthResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerLeaderboardItem;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RevenueBreakdownResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SocialEngagementResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentAnalyticsDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentPerformanceItem;
import com.capstone.su26_sep490_g2_be.dto.response.TransactionStatsResponse;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.AnalyticsExcelService;
import com.capstone.su26_sep490_g2_be.service.AnalyticsService;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Tag(name = "Analytics", description = "Thống kê & phân tích chi tiết — Owner/Manager")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalyticsController {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String BRANCH_ID_DESC =
            "Lọc theo 1 chi nhánh cụ thể — Owner có thể chọn bất kỳ chi nhánh nào của mình (bỏ trống = toàn chuỗi); " +
            "Manager luôn bị giới hạn về (các) chi nhánh mình được cấp quyền dù có truyền hay không.";

    private final AnalyticsService analyticsService;
    private final AnalyticsExcelService analyticsExcelService;
    private final SecurityUtil securityUtil;
    private final BranchAccessService branchAccessService;

    // ──────────────────────────── Owner ────────────────────────────

    @Operation(summary = "Tổng quan thống kê — Owner")
    @GetMapping("/owner/analytics/overview")
    public ResponseEntity<ApiResponse<AnalyticsOverviewResponse>> ownerOverview(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildOverview(ownerId, parseFrom(from), parseTo(to), branchIds)));
    }

    @Operation(summary = "Phân tích doanh thu — Owner")
    @GetMapping("/owner/analytics/revenue")
    public ResponseEntity<ApiResponse<RevenueBreakdownResponse>> ownerRevenue(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildRevenueBreakdown(ownerId, parseFrom(from), parseTo(to), granularity, branchIds)));
    }

    @Operation(summary = "Hiệu suất giải đấu — Owner")
    @GetMapping("/owner/analytics/tournaments")
    public ResponseEntity<ApiResponse<List<TournamentPerformanceItem>>> ownerTournaments(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildTournamentPerformance(ownerId, parseFrom(from), parseTo(to), branchIds)));
    }

    @Operation(summary = "Bảng xếp hạng cơ thủ — Owner")
    @GetMapping("/owner/analytics/players")
    public ResponseEntity<ApiResponse<List<PlayerLeaderboardItem>>> ownerPlayers(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildPlayerLeaderboard(ownerId, parseFrom(from), parseTo(to), branchIds)));
    }

    @Operation(summary = "Hiệu quả truyền thông Facebook — Owner")
    @GetMapping("/owner/analytics/social")
    public ResponseEntity<ApiResponse<SocialEngagementResponse>> ownerSocial(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildSocialEngagement(ownerId, parseFrom(from), parseTo(to), branchIds)));
    }

    @Operation(summary = "Phễu đăng ký — Owner")
    @GetMapping("/owner/analytics/funnel")
    public ResponseEntity<ApiResponse<RegistrationStatsResponse>> ownerFunnel(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildRegistrationFunnel(ownerId, parseFrom(from), parseTo(to), granularity, branchIds)));
    }

    @Operation(summary = "Phân bố theo loại bi — Owner")
    @GetMapping("/owner/analytics/game-types")
    public ResponseEntity<ApiResponse<List<GameTypeBreakdownItem>>> ownerGameTypes(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildGameTypeBreakdown(ownerId, parseFrom(from), parseTo(to), branchIds)));
    }

    @Operation(summary = "Tăng trưởng người chơi — Owner")
    @GetMapping("/owner/analytics/player-growth")
    public ResponseEntity<ApiResponse<PlayerGrowthResponse>> ownerPlayerGrowth(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildPlayerGrowth(ownerId, parseFrom(from), parseTo(to), granularity, branchIds)));
    }

    @Operation(summary = "Chi tiết 1 giải đấu — Owner")
    @GetMapping("/owner/analytics/tournaments/{id}")
    public ResponseEntity<ApiResponse<TournamentAnalyticsDetailResponse>> ownerTournamentDetail(
            Authentication authentication, @PathVariable Long id) {
        Long ownerId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.buildTournamentDetail(ownerId, id, null)));
    }

    @Operation(summary = "Thống kê giao dịch & thanh toán — Owner")
    @GetMapping("/owner/analytics/transactions")
    public ResponseEntity<ApiResponse<TransactionStatsResponse>> ownerTransactions(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildTransactionStats(ownerId, parseFrom(from), parseTo(to), granularity, branchIds)));
    }

    @Operation(summary = "Danh sách giao dịch (phân trang, lọc) — Owner", description = "tournamentId bỏ trống = xem tất cả giao dịch của mọi giải")
    @GetMapping("/owner/analytics/transactions/list")
    public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryResponse>>> ownerTransactionsList(
            Authentication authentication,
            @RequestParam(required = false) Long tournamentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.listTransactions(
                ownerId, tournamentId, status, parseFrom(from), parseTo(to), page, size, branchIds)));
    }

    @Operation(summary = "Xuất báo cáo Excel — Owner")
    @GetMapping("/owner/analytics/export")
    public ResponseEntity<byte[]> ownerExport(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) throws IOException {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return buildExportResponse(ownerId, parseFrom(from), parseTo(to), branchIds);
    }

    @Operation(summary = "Xuất báo cáo chi tiết 1 giải đấu — Owner")
    @GetMapping("/owner/analytics/tournaments/{id}/export")
    public ResponseEntity<byte[]> ownerExportTournament(
            Authentication authentication, @PathVariable Long id) throws IOException {
        Long ownerId = extractUserId(authentication);
        return buildTournamentExportResponse(ownerId, id, null);
    }

    @Operation(summary = "Báo cáo doanh thu theo tháng (khoảng tùy chọn) — Owner")
    @GetMapping("/owner/analytics/monthly-report")
    public ResponseEntity<ApiResponse<MonthlyReportResponse>> ownerMonthlyReport(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildMonthlyReport(ownerId, parseYearMonth(from), parseYearMonth(to), branchIds)));
    }

    @Operation(summary = "Xuất báo cáo doanh thu theo tháng (khoảng tùy chọn) — Owner")
    @GetMapping("/owner/analytics/monthly-report/export")
    public ResponseEntity<byte[]> ownerExportMonthlyReport(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) throws IOException {
        Long ownerId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(ownerId, branchId);
        return buildMonthlyReportExportResponse(ownerId, parseYearMonth(from), parseYearMonth(to), branchIds);
    }

    // ──────────────────────────── Manager ────────────────────────────

    @Operation(summary = "Tổng quan thống kê — Manager")
    @GetMapping("/manager/analytics/overview")
    public ResponseEntity<ApiResponse<AnalyticsOverviewResponse>> managerOverview(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildOverview(ownerId, parseFrom(from), parseTo(to), branchIds)));
    }

    @Operation(summary = "Phân tích doanh thu — Manager")
    @GetMapping("/manager/analytics/revenue")
    public ResponseEntity<ApiResponse<RevenueBreakdownResponse>> managerRevenue(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildRevenueBreakdown(ownerId, parseFrom(from), parseTo(to), granularity, branchIds)));
    }

    @Operation(summary = "Hiệu suất giải đấu — Manager")
    @GetMapping("/manager/analytics/tournaments")
    public ResponseEntity<ApiResponse<List<TournamentPerformanceItem>>> managerTournaments(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildTournamentPerformance(ownerId, parseFrom(from), parseTo(to), branchIds)));
    }

    @Operation(summary = "Bảng xếp hạng cơ thủ — Manager")
    @GetMapping("/manager/analytics/players")
    public ResponseEntity<ApiResponse<List<PlayerLeaderboardItem>>> managerPlayers(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildPlayerLeaderboard(ownerId, parseFrom(from), parseTo(to), branchIds)));
    }

    @Operation(summary = "Hiệu quả truyền thông Facebook — Manager")
    @GetMapping("/manager/analytics/social")
    public ResponseEntity<ApiResponse<SocialEngagementResponse>> managerSocial(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildSocialEngagement(ownerId, parseFrom(from), parseTo(to), branchIds)));
    }

    @Operation(summary = "Phễu đăng ký — Manager")
    @GetMapping("/manager/analytics/funnel")
    public ResponseEntity<ApiResponse<RegistrationStatsResponse>> managerFunnel(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildRegistrationFunnel(ownerId, parseFrom(from), parseTo(to), granularity, branchIds)));
    }

    @Operation(summary = "Phân bố theo loại bi — Manager")
    @GetMapping("/manager/analytics/game-types")
    public ResponseEntity<ApiResponse<List<GameTypeBreakdownItem>>> managerGameTypes(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildGameTypeBreakdown(ownerId, parseFrom(from), parseTo(to), branchIds)));
    }

    @Operation(summary = "Tăng trưởng người chơi — Manager")
    @GetMapping("/manager/analytics/player-growth")
    public ResponseEntity<ApiResponse<PlayerGrowthResponse>> managerPlayerGrowth(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildPlayerGrowth(ownerId, parseFrom(from), parseTo(to), granularity, branchIds)));
    }

    @Operation(summary = "Chi tiết 1 giải đấu — Manager")
    @GetMapping("/manager/analytics/tournaments/{id}")
    public ResponseEntity<ApiResponse<TournamentAnalyticsDetailResponse>> managerTournamentDetail(
            Authentication authentication, @PathVariable Long id) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, null);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.buildTournamentDetail(ownerId, id, branchIds)));
    }

    @Operation(summary = "Thống kê giao dịch & thanh toán — Manager")
    @GetMapping("/manager/analytics/transactions")
    public ResponseEntity<ApiResponse<TransactionStatsResponse>> managerTransactions(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildTransactionStats(ownerId, parseFrom(from), parseTo(to), granularity, branchIds)));
    }

    @Operation(summary = "Danh sách giao dịch (phân trang, lọc) — Manager", description = "tournamentId bỏ trống = xem tất cả giao dịch của mọi giải")
    @GetMapping("/manager/analytics/transactions/list")
    public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryResponse>>> managerTransactionsList(
            Authentication authentication,
            @RequestParam(required = false) Long tournamentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(analyticsService.listTransactions(
                ownerId, tournamentId, status, parseFrom(from), parseTo(to), page, size, branchIds)));
    }

    @Operation(summary = "Xuất báo cáo Excel — Manager")
    @GetMapping("/manager/analytics/export")
    public ResponseEntity<byte[]> managerExport(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) throws IOException {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return buildExportResponse(ownerId, parseFrom(from), parseTo(to), branchIds);
    }

    @Operation(summary = "Xuất báo cáo chi tiết 1 giải đấu — Manager")
    @GetMapping("/manager/analytics/tournaments/{id}/export")
    public ResponseEntity<byte[]> managerExportTournament(
            Authentication authentication, @PathVariable Long id) throws IOException {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, null);
        return buildTournamentExportResponse(ownerId, id, branchIds);
    }

    @Operation(summary = "Báo cáo doanh thu theo tháng (khoảng tùy chọn) — Manager")
    @GetMapping("/manager/analytics/monthly-report")
    public ResponseEntity<ApiResponse<MonthlyReportResponse>> managerMonthlyReport(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.buildMonthlyReport(ownerId, parseYearMonth(from), parseYearMonth(to), branchIds)));
    }

    @Operation(summary = "Xuất báo cáo doanh thu theo tháng (khoảng tùy chọn) — Manager")
    @GetMapping("/manager/analytics/monthly-report/export")
    public ResponseEntity<byte[]> managerExportMonthlyReport(
            Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) throws IOException {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = resolveManagerOwnerId(manager);
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return buildMonthlyReportExportResponse(ownerId, parseYearMonth(from), parseYearMonth(to), branchIds);
    }

    // ──────────────────────────── helpers ────────────────────────────

    private ResponseEntity<byte[]> buildTournamentExportResponse(Long ownerId, Long tournamentId, List<Long> branchIds) throws IOException {
        byte[] workbook = analyticsExcelService.buildTournamentReport(ownerId, tournamentId, branchIds);
        String filename = "bao-cao-giai-dau-" + tournamentId + "-" + FILE_DATE_FMT.format(LocalDate.now()) + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }

    private ResponseEntity<byte[]> buildMonthlyReportExportResponse(Long ownerId, YearMonth from, YearMonth to, List<Long> branchIds)
            throws IOException {
        byte[] workbook = analyticsExcelService.buildMonthlyReport(ownerId, from, to, branchIds);
        String suffix = (from != null ? from : "auto") + "_" + (to != null ? to : "auto");
        String filename = "bao-cao-doanh-thu-" + suffix + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }

    private ResponseEntity<byte[]> buildExportResponse(Long ownerId, Instant from, Instant to, List<Long> branchIds) throws IOException {
        byte[] workbook = analyticsExcelService.buildReport(ownerId, from, to, branchIds);
        String filename = "bao-cao-thong-ke-" + FILE_DATE_FMT.format(LocalDate.now()) + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }

    private Long resolveManagerOwnerId(User manager) {
        if (manager.getOwner() == null) {
            // Manager không gắn Owner (dữ liệu lỗi/orphan) — KHÔNG được coi là "không lọc" rồi trả
            // về dữ liệu của mọi chủ sân khác, phải từ chối thẳng.
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
        return manager.getOwner().getId();
    }

    private Instant parseFrom(String from) {
        if (from == null || from.isBlank()) return null;
        return LocalDate.parse(from.trim()).atStartOfDay(ZONE).toInstant();
    }

    private Instant parseTo(String to) {
        if (to == null || to.isBlank()) return null;
        return LocalDate.parse(to.trim()).atTime(LocalTime.MAX).atZone(ZONE).toInstant();
    }

    private YearMonth parseYearMonth(String value) {
        if (value == null || value.isBlank()) return null;
        return YearMonth.parse(value.trim());
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || !(auth.getCredentials() instanceof Long)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return (Long) auth.getCredentials();
    }
}
