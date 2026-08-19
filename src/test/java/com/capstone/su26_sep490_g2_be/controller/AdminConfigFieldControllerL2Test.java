package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateConfigFieldCatalogRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchFormatActiveRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateConfigFieldCatalogRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Admin quản lý catalog field cấu hình giải (DC-04: format-specific config fields). */
class AdminConfigFieldControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void getCatalog_asAdmin_includesSeededBracketSize() throws Exception {
		mockMvc.perform(get("/api/v1/admin/config-field-catalog")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.param("isActive", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void getCatalog_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/config-field-catalog")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void getCatalogItem_unknownKey_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/admin/config-field-catalog/{fieldKey}", "not_a_real_field")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void createThenUpdateThenToggle_fullCrudLifecycle() throws Exception {
		String adminAuth = bearerToken(TestAccounts.ADMIN_EMAIL);
		String fieldKey = "l2_test_field_" + System.nanoTime();

		CreateConfigFieldCatalogRequest create = new CreateConfigFieldCatalogRequest();
		create.setFieldKey(fieldKey);
		create.setLabel("L2 Test Field");
		create.setDataType("BOOLEAN");
		create.setFieldScope("COMMON");
		create.setUiComponent("CHECKBOX");
		create.setIsActive(true);

		mockMvc.perform(post("/api/v1/admin/config-field-catalog")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.fieldKey").value(fieldKey));

		UpdateConfigFieldCatalogRequest update = new UpdateConfigFieldCatalogRequest();
		update.setLabel("L2 Test Field (updated)");
		update.setUiComponent("CHECKBOX");

		mockMvc.perform(put("/api/v1/admin/config-field-catalog/{fieldKey}", fieldKey)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.label").value("L2 Test Field (updated)"));

		PatchFormatActiveRequest patchReq = new PatchFormatActiveRequest();
		patchReq.setIsActive(false);

		mockMvc.perform(patch("/api/v1/admin/config-field-catalog/{fieldKey}/active", fieldKey)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(patchReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isActive").value(false));
	}

	@Test
	void createCatalogItem_duplicateKey_rejected409() throws Exception {
		CreateConfigFieldCatalogRequest req = new CreateConfigFieldCatalogRequest();
		req.setFieldKey("bracket_size"); // đã seed sẵn
		req.setLabel("Trùng key");
		req.setDataType("INT");
		req.setFieldScope("KNOCKOUT");
		req.setUiComponent("NUMBER");

		mockMvc.perform(post("/api/v1/admin/config-field-catalog")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isConflict());
	}

	@Test
	void createCatalogItem_camelCaseKey_rejected400() throws Exception {
		CreateConfigFieldCatalogRequest req = new CreateConfigFieldCatalogRequest();
		req.setFieldKey("CamelCaseNotAllowed");
		req.setLabel("Sai định dạng");
		req.setDataType("BOOLEAN");
		req.setFieldScope("COMMON");
		req.setUiComponent("CHECKBOX");

		mockMvc.perform(post("/api/v1/admin/config-field-catalog")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}
}
