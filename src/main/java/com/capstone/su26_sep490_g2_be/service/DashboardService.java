package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.DashboardStatsResponse;

public interface DashboardService {

	/**
	 * Builds full dashboard stats scoped to tournaments created by ownerUserId.
	 * Callers must resolve the owning owner's id first (see {@code SecurityUtil.resolveCurrentUser}) —
	 * passing null returns system-wide data across every owner and should only be used intentionally.
	 */
	DashboardStatsResponse buildStats(Long ownerUserId);
}
