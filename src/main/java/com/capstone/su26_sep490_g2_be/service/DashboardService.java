package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.DashboardStatsResponse;

public interface DashboardService {

	/**
	 * Builds full dashboard stats scoped to tournaments created by ownerUserId,
	 * or system-wide when ownerUserId is null (Manager view).
	 */
	DashboardStatsResponse buildStats(Long ownerUserId);
}
