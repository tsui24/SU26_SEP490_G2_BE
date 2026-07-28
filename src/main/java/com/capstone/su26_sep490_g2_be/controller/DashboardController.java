package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.DashboardStatsResponse;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.DashboardService;
import com.capstone.su26_sep490_g2_be.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Dashboard", description = "Thống kê tổng quan")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DashboardController {

    private static final String BRANCH_ID_DESC =
            "Lọc theo 1 chi nhánh cụ thể — Owner có thể chọn bất kỳ chi nhánh nào của mình (bỏ trống = toàn chuỗi); " +
            "Manager luôn bị giới hạn về (các) chi nhánh mình được cấp quyền dù có truyền hay không.";

    private final DashboardService dashboardService;
    private final SecurityUtil securityUtil;
    private final BranchAccessService branchAccessService;

    @Operation(summary = "Thống kê tổng quan — Owner")
    @GetMapping("/owner/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> ownerStats(
            Authentication authentication,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        Long userId = extractUserId(authentication);
        List<Long> branchIds = branchAccessService.resolveOwnerBranchFilter(userId, branchId);
        return ResponseEntity.ok(ApiResponse.success(dashboardService.buildStats(userId, branchIds)));
    }

    @Operation(summary = "Thống kê tổng quan — Manager")
    @GetMapping("/manager/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> managerStats(
            Authentication authentication,
            @Parameter(description = BRANCH_ID_DESC) @RequestParam(required = false) Long branchId) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        if (manager.getOwner() == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
        List<Long> branchIds = branchAccessService.resolveManagerBranchFilter(manager, branchId);
        return ResponseEntity.ok(ApiResponse.success(dashboardService.buildStats(manager.getOwner().getId(), branchIds)));
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || !(auth.getCredentials() instanceof Long)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return (Long) auth.getCredentials();
    }
}
