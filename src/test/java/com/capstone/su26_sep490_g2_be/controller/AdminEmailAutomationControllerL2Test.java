package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.EmailAutomationRuleRequest;
import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Admin cấu hình quy tắc email tự động toàn hệ thống (kích hoạt bởi các event nghiệp vụ). */
class AdminEmailAutomationControllerL2Test extends AbstractControllerIntegrationTest {

	private Long createTemplate(String adminAuth) throws Exception {
		EmailTemplateRequest req = new EmailTemplateRequest();
		req.setCode("L2_AUTOMATION_TPL_" + System.nanoTime());
		req.setName("Template cho automation test");
		req.setCategory("SYSTEM");
		req.setSubjectTemplate("Subject");
		req.setBodyHtmlTemplate("<p>Body</p>");
		String body = mockMvc.perform(post("/api/v1/admin/email/templates")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).path("data").path("id").asLong();
	}

	@Test
	void list_asAdmin_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/automation-rules")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void list_asStaff_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/automation-rules")
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void createThenUpdateThenToggle_fullLifecycle() throws Exception {
		String adminAuth = bearerToken(TestAccounts.ADMIN_EMAIL);
		Long templateId = createTemplate(adminAuth);

		EmailAutomationRuleRequest create = new EmailAutomationRuleRequest();
		create.setCode("L2_RULE_" + System.nanoTime());
		create.setName("L2 Test Rule");
		create.setEventType("REGISTRATION_APPROVED");
		create.setTemplateId(templateId);
		create.setRecipientType("REGISTRATION_USER");

		String body = mockMvc.perform(post("/api/v1/admin/email/automation-rules")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.code").value(create.getCode()))
				.andReturn().getResponse().getContentAsString();
		Long ruleId = objectMapper.readTree(body).path("data").path("id").asLong();

		create.setName("L2 Test Rule (updated)");
		mockMvc.perform(put("/api/v1/admin/email/automation-rules/{id}", ruleId)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("L2 Test Rule (updated)"));

		mockMvc.perform(patch("/api/v1/admin/email/automation-rules/{id}/enabled", ruleId)
						.header("Authorization", adminAuth)
						.param("enabled", "false"))
				.andExpect(status().isOk());
	}

	@Test
	void create_missingTemplateId_rejected400() throws Exception {
		EmailAutomationRuleRequest req = new EmailAutomationRuleRequest();
		req.setCode("L2_BAD_RULE_" + System.nanoTime());
		req.setName("Thiếu templateId");
		req.setEventType("REGISTRATION_APPROVED");
		req.setRecipientType("REGISTRATION_USER");

		mockMvc.perform(post("/api/v1/admin/email/automation-rules")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void create_withoutToken_rejected401() throws Exception {
		EmailAutomationRuleRequest req = new EmailAutomationRuleRequest();
		req.setCode("L2_NOAUTH_" + System.nanoTime());
		req.setName("x");
		req.setEventType("REGISTRATION_APPROVED");
		req.setRecipientType("REGISTRATION_USER");
		req.setTemplateId(1L);

		mockMvc.perform(post("/api/v1/admin/email/automation-rules")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}
}
