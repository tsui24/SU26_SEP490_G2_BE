package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.AdminDashboardStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SystemHealthResponse;
import com.capstone.su26_sep490_g2_be.service.AdminDashboardService;
import com.capstone.su26_sep490_g2_be.service.SystemHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Dashboard", description = "Thống kê tổng quan toàn hệ thống — requires ADMIN role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

	private final AdminDashboardService adminDashboardService;
	private final SystemHealthService systemHealthService;

	@Operation(summary = "Thống kê tổng quan hệ thống — Admin",
			description = "Số liệu toàn hệ thống (tài khoản, chi nhánh, giải đấu, catalog, email) — không thuộc về một quán/chi nhánh cụ thể nào.")
	@GetMapping("/stats")
	public ResponseEntity<ApiResponse<AdminDashboardStatsResponse>> stats() {
		return ResponseEntity.ok(ApiResponse.success(adminDashboardService.buildStats()));
	}

	@Operation(summary = "Sức khỏe hệ thống — Admin",
			description = "JVM/CPU/DB pool/kích thước DB, traffic & error rate HTTP, đăng nhập thất bại, trạng thái background job — " +
					"số liệu traffic/error/job chỉ lưu trong bộ nhớ (mất khi restart server), phản ánh tình trạng từ lúc khởi động.")
	@GetMapping("/system-health")
	public ResponseEntity<ApiResponse<SystemHealthResponse>> systemHealth() {
		return ResponseEntity.ok(ApiResponse.success(systemHealthService.build()));
	}
}
