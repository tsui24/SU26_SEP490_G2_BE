package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — /api/v1/tournaments/** công khai, không cần đăng nhập (PublicEndpoints). */
class PublicTournamentControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void listTournaments_withoutToken_ok() throws Exception {
		mockMvc.perform(get("/api/v1/tournaments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void listTournaments_withSearchAndStatusFilter_ok() throws Exception {
		mockMvc.perform(get("/api/v1/tournaments")
						.param("status", "OPEN_FOR_REGISTRATION")
						.param("search", "khong-ton-tai"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isEmpty());
	}

	@Test
	void getTournament_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/tournaments/{id}", 999_999_999L))
				.andExpect(status().isNotFound());
	}
}
