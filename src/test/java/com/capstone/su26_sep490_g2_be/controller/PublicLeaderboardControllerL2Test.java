package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — /api/v1/leaderboard công khai, không cần đăng nhập. */
class PublicLeaderboardControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void getLeaderboard_defaultPeriodAll_ok() throws Exception {
		mockMvc.perform(get("/api/v1/leaderboard"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void getLeaderboard_periodYear_ok() throws Exception {
		mockMvc.perform(get("/api/v1/leaderboard")
						.param("period", "YEAR")
						.param("year", "2026"))
				.andExpect(status().isOk());
	}

	@Test
	void getLeaderboard_unknownPeriod_fallsBackToAll() throws Exception {
		// LeaderboardPeriod.from() parse an toàn: chuỗi lạ/null đều rơi về ALL thay vì báo lỗi.
		mockMvc.perform(get("/api/v1/leaderboard").param("period", "NOT_A_REAL_PERIOD"))
				.andExpect(status().isOk());
	}
}
