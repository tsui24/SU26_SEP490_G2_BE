package com.capstone.su26_sep490_g2_be.service;

import java.util.Map;

public interface FacebookInsightsService {

	/**
	 * Lấy thống kê cơ bản: likes, comments, shares + permalink.
	 */
	Map<String, Object> getPostEngagement(String facebookPostId);

	/**
	 * Lấy insights nâng cao: impressions, reach, clicks, reactions by type.
	 * Cần quyền read_insights trên Meta App.
	 */
	Map<String, Object> getPostInsights(String facebookPostId);

	/**
	 * Debug token hiện tại — xem quyền thực tế mà Facebook cấp.
	 */
	Map<String, Object> debugCurrentToken();
}
