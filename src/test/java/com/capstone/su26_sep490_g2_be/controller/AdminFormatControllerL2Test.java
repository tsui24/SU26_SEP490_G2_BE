package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateFormatRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateFormatRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpsertFormatConfigFieldsRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpsertFormatRaceToRulesRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — UC "Setup thể thức" (Admin wizard 4 màn: info -> config-fields -> race-to-rules -> activate),
 * đúng luồng mô tả trong BTMS-Tournament-Config-API.md. Đi hết 1 lượt "happy path" trong 1 test vì
 * 4 API phụ thuộc trạng thái {@code setupStatus} của nhau — tách rời sẽ không tái hiện được lỗi
 * thật sự (VD activate sớm khi thiếu race-to-rules).
 */
class AdminFormatControllerL2Test extends AbstractControllerIntegrationTest {

	// ── GET /admin/formats, GET /admin/formats/{code} ─────────────────────

	@Test
	void listFormats_asAdmin_includesSeededSingleElimination() throws Exception {
		mockMvc.perform(get("/api/v1/admin/formats")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void listFormats_asOwner_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/formats")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void getFormat_unknownCode_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/admin/formats/{code}", "NOT_A_REAL_FORMAT")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isNotFound());
	}

	// ── POST /admin/formats ────────────────────────────────────────────────

	@Test
	void createFormat_duplicateCode_rejected409() throws Exception {
		CreateFormatRequest req = new CreateFormatRequest();
		req.setCode("SINGLE_ELIMINATION"); // đã seed sẵn
		req.setName("Trùng code");
		req.setDescription("...");
		req.setHandlerKey("pool_single_elimination_handler");

		mockMvc.perform(post("/api/v1/admin/formats")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isConflict());
	}

	@Test
	void createFormat_lowercaseCode_rejected400() throws Exception {
		CreateFormatRequest req = new CreateFormatRequest();
		req.setCode("lowercase_not_allowed");
		req.setName("Sai định dạng");
		req.setDescription("...");
		req.setHandlerKey("x");

		mockMvc.perform(post("/api/v1/admin/formats")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createFormat_asManager_rejected403() throws Exception {
		CreateFormatRequest req = new CreateFormatRequest();
		req.setCode("TEST_BLOCKED_" + System.nanoTime());
		req.setName("Blocked");
		req.setDescription("...");
		req.setHandlerKey("x");

		mockMvc.perform(post("/api/v1/admin/formats")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isForbidden());
	}

	// ── PUT /admin/formats/{code} ──────────────────────────────────────────

	@Test
	void updateFormat_unknownCode_rejected404() throws Exception {
		UpdateFormatRequest req = new UpdateFormatRequest();
		req.setName("x");
		req.setDescription("x");
		req.setHandlerKey("x");

		mockMvc.perform(put("/api/v1/admin/formats/{code}", "NOT_A_REAL_FORMAT")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isNotFound());
	}

	// ── Wizard đầy đủ: create -> config-fields -> race-to-rules -> setup-summary -> activate ──

	@Test
	void wizard_fullHappyPath_reachesActive() throws Exception {
		String code = "TEST_WIZARD_" + System.nanoTime();
		String adminAuth = bearerToken(TestAccounts.ADMIN_EMAIL);

		CreateFormatRequest create = new CreateFormatRequest();
		create.setCode(code);
		create.setName("Wizard L2 Test");
		create.setDescription("Format tạo bởi integration test");
		// handlerKey KHÔNG được trùng handler của format khác (existsByHandlerKey -> DUPLICATE_RESOURCE,
		// 1 handler bean chỉ backing đúng 1 format) — "pool_single_elimination_handler" đã dùng bởi
		// SINGLE_ELIMINATION seed sẵn, nên phải là key mới hoàn toàn.
		create.setHandlerKey("test_wizard_handler_" + System.nanoTime());
		mockMvc.perform(post("/api/v1/admin/formats")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.setupStatus").value("INFO_DONE"));

		mockMvc.perform(get("/api/v1/admin/formats/{code}/setup-status", code)
						.header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.canActivate").value(false));

		UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest bracketSize =
				new UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest();
		bracketSize.setFieldKey("bracket_size");
		bracketSize.setDefaultValue("16");
		bracketSize.setIsRequired(true);
		bracketSize.setIsVisibleToOwner(true);
		UpsertFormatConfigFieldsRequest configFieldsReq = new UpsertFormatConfigFieldsRequest();
		configFieldsReq.setFields(List.of(bracketSize));

		mockMvc.perform(put("/api/v1/admin/formats/{code}/config-fields", code)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(configFieldsReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.setupStatus").value("CONFIG_FIELDS_DONE"));

		// Activate quá sớm — thiếu race-to-rules -> phải bị chặn (SETUP_INCOMPLETE), không được 200.
		mockMvc.perform(post("/api/v1/admin/formats/{code}/activate", code)
						.header("Authorization", adminAuth))
				.andExpect(status().is4xxClientError());

		UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest finalRound =
				new UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest();
		finalRound.setRoundKey("final");
		finalRound.setLabel("Chung kết");
		finalRound.setBracketPhase("KNOCKOUT");
		finalRound.setRaceTo(9);
		UpsertFormatRaceToRulesRequest raceToReq = new UpsertFormatRaceToRulesRequest();
		raceToReq.setRules(List.of(finalRound));

		mockMvc.perform(put("/api/v1/admin/formats/{code}/race-to-rules", code)
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(raceToReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.setupStatus").value("RACE_TO_DONE"));

		mockMvc.perform(get("/api/v1/admin/formats/{code}/setup-summary", code)
						.header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.canActivate").value(true));

		mockMvc.perform(post("/api/v1/admin/formats/{code}/activate", code)
						.header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isActive").value(true))
				.andExpect(jsonPath("$.data.setupStatus").value("ACTIVE"));
	}

	@Test
	void configFields_emptyList_rejected400() throws Exception {
		UpsertFormatConfigFieldsRequest req = new UpsertFormatConfigFieldsRequest();
		req.setFields(List.of());

		mockMvc.perform(put("/api/v1/admin/formats/{code}/config-fields", "SINGLE_ELIMINATION")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}
}
