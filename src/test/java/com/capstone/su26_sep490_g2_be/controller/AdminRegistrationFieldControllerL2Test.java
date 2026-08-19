package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateRegistrationFieldRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchRegistrationFieldActiveRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateRegistrationFieldRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Admin quản lý catalog field cho form đăng ký giải (khác catalog config giải ở AdminConfigFieldController). */
class AdminRegistrationFieldControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void getCatalog_asAdmin_includesSeededPlayerFullName() throws Exception {
		mockMvc.perform(get("/api/v1/admin/registration-field-catalog")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void getCatalog_asStaff_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/registration-field-catalog")
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void getCatalogItem_seeded_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/registration-field-catalog/{fieldKey}", "player_full_name")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.fieldKey").value("player_full_name"));
	}

	@Test
	void getCatalogItem_unknownKey_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/admin/registration-field-catalog/{fieldKey}", "not_a_real_field")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void createThenUpdateThenToggle_fullCrudLifecycle() throws Exception {
		String adminAuth = bearerToken(TestAccounts.ADMIN_EMAIL);
		String fieldKey = "l2_reg_field_" + System.nanoTime();

		CreateRegistrationFieldRequest create = new CreateRegistrationFieldRequest();
		create.setFieldKey(fieldKey);
		create.setLabel("L2 Registration Field");
		create.setDataType("STRING");
		create.setUiComponent("TEXT");
		create.setIsActive(true);

		mockMvc.perform(post("/api/v1/admin/registration-field-catalog")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.fieldKey").value(fieldKey));

		UpdateRegistrationFieldRequest update = new UpdateRegistrationFieldRequest();
		update.setLabel("L2 Registration Field (updated)");

		mockMvc.perform(put("/api/v1/admin/registration-field-catalog/{fieldKey}", fieldKey)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.label").value("L2 Registration Field (updated)"));

		PatchRegistrationFieldActiveRequest patchReq = new PatchRegistrationFieldActiveRequest();
		patchReq.setIsActive(false);

		mockMvc.perform(patch("/api/v1/admin/registration-field-catalog/{fieldKey}", fieldKey)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(patchReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isActive").value(false));
	}

	@Test
	void createField_duplicateKey_rejected409() throws Exception {
		CreateRegistrationFieldRequest req = new CreateRegistrationFieldRequest();
		req.setFieldKey("player_full_name"); // đã seed sẵn
		req.setLabel("Trùng key");
		req.setDataType("STRING");
		req.setUiComponent("TEXT");

		mockMvc.perform(post("/api/v1/admin/registration-field-catalog")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isConflict());
	}

	@Test
	void createField_fieldKeyTooLong_rejected400() throws Exception {
		CreateRegistrationFieldRequest req = new CreateRegistrationFieldRequest();
		req.setFieldKey("a".repeat(90)); // > 80 ký tự
		req.setLabel("Quá dài");
		req.setDataType("STRING");
		req.setUiComponent("TEXT");

		mockMvc.perform(post("/api/v1/admin/registration-field-catalog")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void patchFieldActive_missingIsActive_rejected400() throws Exception {
		PatchRegistrationFieldActiveRequest req = new PatchRegistrationFieldActiveRequest();

		mockMvc.perform(patch("/api/v1/admin/registration-field-catalog/{fieldKey}", "player_full_name")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}
}
