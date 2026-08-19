package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.ManualSendEmailRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Owner gửi email cho người tham gia giải (biến thể Owner của ManagerEmailController). */
class OwnerEmailControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void listTemplates_asOwner_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/email/templates")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void listTemplates_asStaff_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/owner/email/templates")
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void listAutomationRules_unknownTournament_rejected() throws Exception {
		mockMvc.perform(get("/api/v1/owner/tournaments/{id}/email/automation-rules", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void sendManual_missingTemplateId_rejected400() throws Exception {
		ManualSendEmailRequest req = new ManualSendEmailRequest();
		req.setRecipientType("ALL_PARTICIPANTS");

		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/email/send-manual", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listLogs_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/owner/tournaments/{id}/email/logs", 1L))
				.andExpect(status().isUnauthorized());
	}
}
