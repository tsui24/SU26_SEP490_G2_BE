package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.DashboardStatsResponse;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.DashboardService;
import com.capstone.su26_sep490_g2_be.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Thống kê tổng quan")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final SecurityUtil securityUtil;

    @Operation(summary = "Thống kê tổng quan — Owner")
    @GetMapping("/owner/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> ownerStats(Authentication authentication) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(dashboardService.buildStats(userId)));
    }

    @Operation(summary = "Thống kê tổng quan — Manager")
    @GetMapping("/manager/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> managerStats(Authentication authentication) {
        User manager = securityUtil.resolveCurrentUser(authentication);
        Long ownerId = manager.getOwner() != null ? manager.getOwner().getId() : null;
        return ResponseEntity.ok(ApiResponse.success(dashboardService.buildStats(ownerId)));
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || !(auth.getCredentials() instanceof Long)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return (Long) auth.getCredentials();
    }
}
