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

/**
 * L2 — Manager gửi email cho người tham gia giải (theo giải cụ thể — 404 khi giải không tồn tại vì
 * chưa scope được owner để kiểm tra quyền).
 */
class ManagerEmailControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void listTemplates_asManager_ok() throws Exception {
		mockMvc.perform(get("/api/v1/manager/email/templates")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void listTemplates_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/manager/email/templates")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void listAutomationRules_unknownTournament_rejected() throws Exception {
		mockMvc.perform(get("/api/v1/manager/tournaments/{id}/email/automation-rules", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void listLogs_unknownTournament_rejected() throws Exception {
		mockMvc.perform(get("/api/v1/manager/tournaments/{id}/email/logs", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void sendManual_missingRecipientType_rejected400() throws Exception {
		ManualSendEmailRequest req = new ManualSendEmailRequest();
		req.setTemplateId(1L);

		mockMvc.perform(post("/api/v1/manager/tournaments/{id}/email/send-manual", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void sendManual_withoutToken_rejected401() throws Exception {
		ManualSendEmailRequest req = new ManualSendEmailRequest();
		req.setTemplateId(1L);
		req.setRecipientType("ALL_PARTICIPANTS");

		mockMvc.perform(post("/api/v1/manager/tournaments/{id}/email/send-manual", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}
}
