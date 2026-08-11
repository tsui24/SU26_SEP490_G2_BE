package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.AdminDashboardStatsResponse;

public interface AdminDashboardService {

	/** Thống kê toàn hệ thống cho Admin — không scope theo owner/branch nào, chỉ ADMIN mới gọi được endpoint này. */
	AdminDashboardStatsResponse buildStats();
}
