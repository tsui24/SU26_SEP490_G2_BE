package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Dashboard tổng quan Owner/Manager (GB-05: dữ liệu lọc theo owner/branch scope). */
class DashboardControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void ownerStats_asOwner_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/dashboard/stats")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void ownerStats_asManager_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/owner/dashboard/stats")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void ownerStats_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/owner/dashboard/stats"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void managerStats_asManager_ok() throws Exception {
		mockMvc.perform(get("/api/v1/manager/dashboard/stats")
						.header("Authorization", bearerToken(TestAccounts.MANAGER2_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void managerStats_asAdmin_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/manager/dashboard/stats")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isForbidden());
	}
}
