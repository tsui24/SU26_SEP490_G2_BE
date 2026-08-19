package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — GB-04: /api/v1/admin/dashboard/** chỉ ADMIN. Số liệu toàn hệ thống, không scope theo owner/branch. */
class AdminDashboardControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void stats_asAdmin_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/dashboard/stats")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void stats_asOwner_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/dashboard/stats")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void stats_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/admin/dashboard/stats"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void systemHealth_asAdmin_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/dashboard/system-health")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void systemHealth_asStaff_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/dashboard/system-health")
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL)))
				.andExpect(status().isForbidden());
	}
}
