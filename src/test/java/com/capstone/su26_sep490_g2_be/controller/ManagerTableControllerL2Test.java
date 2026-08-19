package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Manager xem danh sách bàn (chỉ đọc, dùng để gán lịch trận đấu). */
class ManagerTableControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void listActiveTables_asManager_ok() throws Exception {
		mockMvc.perform(get("/api/v1/manager/tables")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void listActiveTables_asStaff_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/manager/tables")
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void listActiveTables_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/manager/tables"))
				.andExpect(status().isUnauthorized());
	}
}
