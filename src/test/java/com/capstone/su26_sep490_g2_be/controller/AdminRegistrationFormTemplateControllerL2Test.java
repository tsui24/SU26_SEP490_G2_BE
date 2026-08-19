package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateRegistrationFormTemplateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchRegistrationFormTemplateActiveRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateRegistrationFormTemplateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpsertRegistrationFormTemplateFieldsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Admin dựng template form đăng ký (dùng bởi UC-23 Player registration). */
class AdminRegistrationFormTemplateControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void listTemplates_asAdmin_includesSeededBasicTemplate() throws Exception {
		mockMvc.perform(get("/api/v1/admin/registration-form-templates")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void listTemplates_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/admin/registration-form-templates"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getTemplate_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/admin/registration-form-templates/{id}", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void fullLifecycle_create_addField_patch_deleteField() throws Exception {
		String adminAuth = bearerToken(TestAccounts.ADMIN_EMAIL);

		CreateRegistrationFormTemplateRequest create = new CreateRegistrationFormTemplateRequest();
		String code = "L2_TEST_" + System.nanoTime();
		create.setCode(code);
		create.setName("L2 Test Template");
		create.setIsActive(true);

		String createBody = mockMvc.perform(post("/api/v1/admin/registration-form-templates")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long templateId = objectMapper.readTree(createBody).path("data").path("id").asLong();

		UpdateRegistrationFormTemplateRequest update = new UpdateRegistrationFormTemplateRequest();
		update.setName("L2 Test Template (updated)");

		mockMvc.perform(put("/api/v1/admin/registration-form-templates/{id}", templateId)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("L2 Test Template (updated)"));

		UpsertRegistrationFormTemplateFieldsRequest.TemplateFieldItemRequest fieldItem =
				new UpsertRegistrationFormTemplateFieldsRequest.TemplateFieldItemRequest();
		fieldItem.setFieldKey("player_full_name");
		fieldItem.setIsRequired(true);
		fieldItem.setSortOrder(1);
		UpsertRegistrationFormTemplateFieldsRequest fieldsReq = new UpsertRegistrationFormTemplateFieldsRequest();
		fieldsReq.setFields(List.of(fieldItem));

		mockMvc.perform(put("/api/v1/admin/registration-form-templates/{id}/fields", templateId)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(fieldsReq)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/admin/registration-form-templates/{id}/fields", templateId)
						.header("Authorization", adminAuth))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/admin/registration-form-templates/{id}/preview", templateId)
						.header("Authorization", adminAuth))
				.andExpect(status().isOk());

		PatchRegistrationFormTemplateActiveRequest patchReq = new PatchRegistrationFormTemplateActiveRequest();
		patchReq.setIsActive(false);
		mockMvc.perform(patch("/api/v1/admin/registration-form-templates/{id}", templateId)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(patchReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isActive").value(false));

		mockMvc.perform(delete("/api/v1/admin/registration-form-templates/{id}/fields/{fieldKey}",
						templateId, "player_full_name")
						.header("Authorization", adminAuth))
				.andExpect(status().isOk());
	}

	@Test
	void createTemplate_duplicateCode_rejected409() throws Exception {
		CreateRegistrationFormTemplateRequest req = new CreateRegistrationFormTemplateRequest();
		req.setCode("PLAYER_REG_BASIC"); // đã seed sẵn
		req.setName("Trùng code");

		mockMvc.perform(post("/api/v1/admin/registration-form-templates")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isConflict());
	}

	@Test
	void createTemplate_lowercaseCode_rejected400() throws Exception {
		CreateRegistrationFormTemplateRequest req = new CreateRegistrationFormTemplateRequest();
		req.setCode("lowercase_not_allowed");
		req.setName("Sai định dạng");

		mockMvc.perform(post("/api/v1/admin/registration-form-templates")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createTemplate_asOwner_rejected403() throws Exception {
		CreateRegistrationFormTemplateRequest req = new CreateRegistrationFormTemplateRequest();
		req.setCode("L2_BLOCKED_" + System.nanoTime());
		req.setName("Blocked");

		mockMvc.perform(post("/api/v1/admin/registration-form-templates")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isForbidden());
	}
}
