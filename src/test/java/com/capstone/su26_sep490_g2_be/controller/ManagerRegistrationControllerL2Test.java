package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.RejectRegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — Manager duyệt/từ chối đăng ký giải (UC-24/UC-25). Chuỗi đầy đủ "player đăng ký -> manager
 * duyệt" cần dựng registration thật, thuộc phạm vi L3 Business Flow — ở đây chỉ kiểm tra role-guard,
 * validate input, và 404/4xx cho registration không tồn tại.
 */
class ManagerRegistrationControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void listRegistrationFormTemplates_asManager_includesSeededBasic() throws Exception {
		mockMvc.perform(get("/api/v1/manager/registration-form-templates")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void listRegistrationFormTemplates_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/manager/registration-form-templates")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void previewRegistrationFormTemplate_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/manager/registration-form-templates/{id}/preview", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void listTournamentRegistrations_unknownTournament_rejected() throws Exception {
		mockMvc.perform(get("/api/v1/manager/tournaments/{id}/registrations", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void getRegistrationDetail_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/manager/registrations/{id}", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void approveRegistration_unknownId_rejected404() throws Exception {
		mockMvc.perform(post("/api/v1/manager/registrations/{id}/approve", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectRegistration_blankReason_rejected400() throws Exception {
		RejectRegistrationRequest req = new RejectRegistrationRequest();
		req.setReason("");

		mockMvc.perform(post("/api/v1/manager/registrations/{id}/reject", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectRegistration_withoutToken_rejected401() throws Exception {
		RejectRegistrationRequest req = new RejectRegistrationRequest();
		req.setReason("Thiếu giấy tờ");

		mockMvc.perform(post("/api/v1/manager/registrations/{id}/reject", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}
}
