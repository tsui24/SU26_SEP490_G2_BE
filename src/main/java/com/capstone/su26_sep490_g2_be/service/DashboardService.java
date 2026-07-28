package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.DashboardStatsResponse;

import java.util.List;

public interface DashboardService {

	/**
	 * Builds full dashboard stats scoped to tournaments created by ownerUserId.
	 * Callers must resolve the owning owner's id first (see {@code SecurityUtil.resolveCurrentUser}) —
	 * passing null returns system-wide data across every owner and should only be used intentionally.
	 * branchIds null = không lọc theo chi nhánh (Owner mặc định xem toàn chuỗi); khác null = chỉ tính
	 * các chi nhánh đó (dùng để giới hạn Manager về đúng chi nhánh họ được cấp quyền).
	 */
	DashboardStatsResponse buildStats(Long ownerUserId, List<Long> branchIds);
}
