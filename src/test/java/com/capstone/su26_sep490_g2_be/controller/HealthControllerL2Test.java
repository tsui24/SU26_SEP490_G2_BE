package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — /api/v1/health là endpoint public (PublicEndpoints), dùng cho readiness probe / uptime
 * monitor bên ngoài nên KHÔNG được yêu cầu đăng nhập.
 */
class HealthControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void health_withoutToken_returnsUpAndDbConnectedTrue() throws Exception {
		// DB thật đang chạy cho toàn bộ L2 (@SpringBootTest) nên dbConnected phải luôn true ở đây.
		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("UP"))
				.andExpect(jsonPath("$.data.dbConnected").value(true));
	}

	@Test
	void health_withToken_stillWorks_notBlockedByRoleRules() throws Exception {
		mockMvc.perform(get("/api/v1/health")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("UP"));
	}
}
