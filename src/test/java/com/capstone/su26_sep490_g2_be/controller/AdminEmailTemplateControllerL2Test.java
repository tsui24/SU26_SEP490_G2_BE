package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplatePreviewRequest;
import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Admin quản lý mẫu email hệ thống (không thuộc riêng owner/branch nào). */
class AdminEmailTemplateControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void list_asAdmin_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/templates")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void list_asManager_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/templates")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void listVariables_asAdmin_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/templates/variables")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void getTemplate_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/templates/{id}", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void createThenUpdateThenToggleThenPreview_fullLifecycle() throws Exception {
		String adminAuth = bearerToken(TestAccounts.ADMIN_EMAIL);
		String code = "L2_TEST_TEMPLATE_" + System.nanoTime();

		EmailTemplateRequest create = new EmailTemplateRequest();
		create.setCode(code);
		create.setName("L2 Test Email");
		create.setCategory("SYSTEM");
		create.setSubjectTemplate("Xin chào {{user.fullName}}");
		create.setBodyHtmlTemplate("<p>Nội dung test cho {{user.fullName}}</p>");

		String createBody = mockMvc.perform(post("/api/v1/admin/email/templates")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.code").value(code))
				.andReturn().getResponse().getContentAsString();
		Long templateId = objectMapper.readTree(createBody).path("data").path("id").asLong();

		EmailTemplateRequest update = new EmailTemplateRequest();
		update.setCode(code);
		update.setName("L2 Test Email (updated)");
		update.setCategory("SYSTEM");
		update.setSubjectTemplate("Cập nhật {{user.fullName}}");
		update.setBodyHtmlTemplate("<p>Đã cập nhật</p>");

		mockMvc.perform(put("/api/v1/admin/email/templates/{id}", templateId)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("L2 Test Email (updated)"));

		mockMvc.perform(patch("/api/v1/admin/email/templates/{id}/active", templateId)
						.header("Authorization", adminAuth)
						.param("active", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isActive").value(false));

		EmailTemplatePreviewRequest previewReq = new EmailTemplatePreviewRequest();
		mockMvc.perform(post("/api/v1/admin/email/templates/{id}/preview", templateId)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(previewReq)))
				.andExpect(status().isOk());
	}

	@Test
	void create_blankSubjectTemplate_rejected400() throws Exception {
		EmailTemplateRequest req = new EmailTemplateRequest();
		req.setCode("L2_BAD_" + System.nanoTime());
		req.setName("Thiếu subject");
		req.setCategory("SYSTEM");
		req.setSubjectTemplate("");
		req.setBodyHtmlTemplate("<p>ok</p>");

		mockMvc.perform(post("/api/v1/admin/email/templates")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void create_withoutToken_rejected401() throws Exception {
		EmailTemplateRequest req = new EmailTemplateRequest();
		req.setCode("L2_NOAUTH_" + System.nanoTime());
		req.setName("x");
		req.setCategory("SYSTEM");
		req.setSubjectTemplate("x");
		req.setBodyHtmlTemplate("x");

		mockMvc.perform(post("/api/v1/admin/email/templates")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}
}
