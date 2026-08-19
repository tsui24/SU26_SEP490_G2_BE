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

/** L2 — Owner duyệt/từ chối đăng ký giải (biến thể Owner của ManagerRegistrationController). */
class OwnerRegistrationControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void listRegistrationFormTemplates_asOwner_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/registration-form-templates")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void listRegistrationFormTemplates_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/owner/registration-form-templates"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getRegistrationDetail_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/owner/registrations/{id}", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void approveRegistration_asManager_rejected403() throws Exception {
		// GB-04: /owner/** chỉ role OWNER.
		mockMvc.perform(post("/api/v1/owner/registrations/{id}/approve", 1L)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectRegistration_blankReason_rejected400() throws Exception {
		RejectRegistrationRequest req = new RejectRegistrationRequest();
		req.setReason("");

		mockMvc.perform(post("/api/v1/owner/registrations/{id}/reject", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}
}
