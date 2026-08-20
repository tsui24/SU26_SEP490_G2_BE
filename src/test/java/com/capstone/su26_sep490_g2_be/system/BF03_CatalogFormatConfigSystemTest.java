package com.capstone.su26_sep490_g2_be.system;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-03 Platform Catalog & Tournament Format Configuration.
 * Rows TC-SYS-BF03-001..017 in docs/Report_5.3_SystemTests_L3.md.
 */
@Transactional
class BF03_CatalogFormatConfigSystemTest extends SystemTestBase {

	/** TC-SYS-BF03-001..010 — main flow: build a brand-new format + registration template end to end. */
	@Test
	void mainFlow_newFormatAndTemplate_readyForTournamentCreation() throws Exception {
		String adminToken = login("admin@gmail.com", "admin1");
		String managerToken = login("manager@gmail.com", "manager123");
		String suffix = uniq();
		String suffixUpper = suffix.toUpperCase();

		// TC-SYS-BF03-001
		String gameTypeCode = "QA_GAME_" + suffixUpper;
		mvc.perform(authed(post("/api/v1/admin/game-types"), adminToken)
						.content("""
								{"code":"%s","name":"QA Game %s","defaultRaceTo":5,"isActive":true}
								""".formatted(gameTypeCode, suffix)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.isActive").value(true));

		// TC-SYS-BF03-002
		String fieldKey = "qa_field_" + suffix;
		mvc.perform(authed(post("/api/v1/admin/config-field-catalog"), adminToken)
						.content("""
								{"fieldKey":"%s","label":"QA Field","dataType":"INT","fieldScope":"COMMON",
								 "uiComponent":"NUMBER","minValue":1,"maxValue":10,"isActive":true}
								""".formatted(fieldKey)))
				.andExpect(status().isCreated());

		// TC-SYS-BF03-003
		String formatCode = "QA_FORMAT_" + suffixUpper;
		mvc.perform(authed(post("/api/v1/admin/formats"), adminToken)
						.content("""
								{"code":"%s","name":"QA Format %s","description":"QA format for L3",
								 "handlerKey":"qa_handler_%s","isActive":false}
								""".formatted(formatCode, suffix, suffix)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.setupStatus").value("INFO_DONE"));

		// TC-SYS-BF03-004
		mvc.perform(authed(put("/api/v1/admin/formats/{code}/config-fields", formatCode), adminToken)
						.content("""
								{"fields":[{"fieldKey":"%s","defaultValue":"3","isRequired":true,"isVisibleToOwner":true}]}
								""".formatted(fieldKey)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.setupStatus").value("CONFIG_FIELDS_DONE"));

		// TC-SYS-BF03-005
		mvc.perform(authed(put("/api/v1/admin/formats/{code}/race-to-rules", formatCode), adminToken)
						.content("""
								{"rules":[{"roundKey":"final","label":"Final","bracketPhase":"KNOCKOUT","raceTo":5}]}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.setupStatus").value("RACE_TO_DONE"));

		// TC-SYS-BF03-006
		mvc.perform(authed(post("/api/v1/admin/formats/{code}/activate", formatCode), adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isActive").value(true));

		// TC-SYS-BF03-007
		String regFieldKey = "qa_reg_" + suffix;
		mvc.perform(authed(post("/api/v1/admin/registration-field-catalog"), adminToken)
						.content("""
								{"fieldKey":"%s","label":"QA Reg Field","dataType":"STRING","uiComponent":"TEXT","isActive":true}
								""".formatted(regFieldKey)))
				.andExpect(status().isCreated());

		// TC-SYS-BF03-008
		String templateCode = "QA_TEMPLATE_" + suffixUpper;
		var templateRes = mvc.perform(authed(post("/api/v1/admin/registration-form-templates"), adminToken)
						.content("""
								{"code":"%s","name":"QA Template %s","isActive":true}
								""".formatted(templateCode, suffix)))
				.andExpect(status().isCreated())
				.andReturn();
		Number templateId = read(bodyOf(templateRes), "$.data.id");

		// TC-SYS-BF03-009
		mvc.perform(authed(put("/api/v1/admin/registration-form-templates/{id}/fields", templateId), adminToken)
						.content("""
								{"fields":[{"fieldKey":"%s","isRequired":true,"sortOrder":1}]}
								""".formatted(regFieldKey)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isReady").value(true));

		// TC-SYS-BF03-010 — End condition: format visible to Manager for tournament creation
		mvc.perform(authed(get("/api/v1/manager/formats"), managerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.code=='" + formatCode + "')]").exists());
	}

	/**
	 * TC-SYS-BF03-011..017 — exception path: format config edit is blocked while a non-DRAFT
	 * tournament uses it (FORMAT_IN_USE_CANNOT_EDIT, 409); surfaces DEF-005 (shared error code
	 * FORMAT_004 with INVALID_FIELD_KEY). Remediated by cancelling the tournament.
	 */
	@Test
	void exceptionPath_formatInUse_editBlockedThenRemediated() throws Exception {
		String adminToken = login("admin@gmail.com", "admin1");
		String managerToken = login("manager@gmail.com", "manager123");
		String suffix = uniq();
		String suffixUpper = suffix.toUpperCase();

		// TC-SYS-BF03-011
		String formatCode = "QA_FORMAT2_" + suffixUpper;
		mvc.perform(authed(post("/api/v1/admin/formats"), adminToken)
						.content("""
								{"code":"%s","name":"QA Format2 %s","description":"QA format2",
								 "handlerKey":"qa_handler2_%s","isActive":false}
								""".formatted(formatCode, suffix, suffix)))
				.andExpect(status().isCreated());

		// TC-SYS-BF03-012 — config fields + race-to + activate (reuse an existing catalog field)
		mvc.perform(authed(put("/api/v1/admin/formats/{code}/config-fields", formatCode), adminToken)
						.content("""
								{"fields":[{"fieldKey":"break_rule","defaultValue":"ALTERNATE_BREAK","isRequired":true}]}
								"""))
				.andExpect(status().isOk());
		mvc.perform(authed(put("/api/v1/admin/formats/{code}/race-to-rules", formatCode), adminToken)
						.content("""
								{"rules":[{"roundKey":"final","label":"Final","bracketPhase":"KNOCKOUT","raceTo":5}]}
								"""))
				.andExpect(status().isOk());
		mvc.perform(authed(post("/api/v1/admin/formats/{code}/activate", formatCode), adminToken))
				.andExpect(status().isOk());

		// TC-SYS-BF03-013
		var branchRes = mvc.perform(authed(get("/api/v1/manager/branches"), managerToken))
				.andExpect(status().isOk())
				.andReturn();
		Number branchId = read(bodyOf(branchRes), "$.data.content[0].id");

		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF03 Tour %s","gameType":"9_BALL","format":"%s","participantType":"SINGLE",
								 "maxParticipants":4,"branchId":%s,"isRegister":false}
								""".formatted(suffix, formatCode, branchId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		// TC-SYS-BF03-014
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"OPEN_FOR_REGISTRATION"}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF03-015 — blocked while in use; surfaces DEF-005 (shared FORMAT_004 code)
		mvc.perform(authed(put("/api/v1/admin/formats/{code}/config-fields", formatCode), adminToken)
						.content("""
								{"fields":[{"fieldKey":"break_rule","defaultValue":"WINNER_BREAK","isRequired":true}]}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("FORMAT_004"));

		// TC-SYS-BF03-016 (remediation)
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"CANCELLED"}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF03-017 — edit succeeds now that no active tournament uses the format
		mvc.perform(authed(put("/api/v1/admin/formats/{code}/config-fields", formatCode), adminToken)
						.content("""
								{"fields":[{"fieldKey":"break_rule","defaultValue":"WINNER_BREAK","isRequired":true}]}
								"""))
				.andExpect(status().isOk());
	}
}
