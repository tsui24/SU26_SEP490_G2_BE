package com.capstone.su26_sep490_g2_be.system;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-04 Tournament Creation & Publication.
 * Rows TC-SYS-BF04-001..008 in docs/Report_5.3_SystemTests_L3.md.
 */
@Transactional
class BF04_TournamentCreationPublicationSystemTest extends SystemTestBase {

	/** TC-SYS-BF04-001..004 — main flow: DRAFT → configured → OPEN_FOR_REGISTRATION → publicly visible. */
	@Test
	void mainFlow_createConfigurePublish_publiclyVisible() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);

		// TC-SYS-BF04-001
		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF04 Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":8,"branchId":%s,"isRegister":false,
								 "isShowTournament":true}
								""".formatted(uniq(), branchId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("DRAFT"))
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		// TC-SYS-BF04-002 — SINGLE_ELIMINATION requires bracket_size/third_place_match/break_rule/
		// lag_for_break (DatabaseSeedData.formatConfigFields()); scoring_unit is optional.
		mvc.perform(authed(put("/api/v1/manager/tournaments/{id}/config", tournamentId), managerToken)
						.content("""
								{"seedingMethod":"RANDOM","raceToOverrides":[],"fields":[
								 {"fieldKey":"bracket_size","value":"8"},
								 {"fieldKey":"third_place_match","value":"true"},
								 {"fieldKey":"break_rule","value":"ALTERNATE_BREAK"},
								 {"fieldKey":"lag_for_break","value":"true"}
								]}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF04-003
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"OPEN_FOR_REGISTRATION"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("OPEN_FOR_REGISTRATION"));

		// TC-SYS-BF04-004 — End condition: published and open for registration, publicly visible
		mvc.perform(get("/api/v1/tournaments/{id}", tournamentId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("OPEN_FOR_REGISTRATION"));
	}

	/**
	 * TC-SYS-BF04-005..008 — exception path: cross-branch Manager isolation, targeting the GB-05 /
	 * DEF-006 finding. Real execution against {@code ManagerTournamentController} REFUTES the
	 * original defect claim for these two endpoints: both {@code GET} and {@code PUT} correctly
	 * return 403 {@code AUTH_006} for a Manager outside the tournament's branch. DEF-006 has been
	 * narrowed accordingly — kept here as a negative-result regression check.
	 */
	@Test
	void exceptionPath_crossBranchManagerIsolation_gb05() throws Exception {
		String managerAToken = login("manager@gmail.com", "manager123");
		String managerBToken = login("manager2@gmail.com", "manager123");
		Number branchAId = accessibleBranchId(managerAToken);

		// TC-SYS-BF04-005
		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerAToken)
						.content("""
								{"name":"QA BF04x Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":8,"branchId":%s,"isRegister":false}
								""".formatted(uniq(), branchAId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		// TC-SYS-BF04-006 — Manager2 (Branch B) reads Branch A's DRAFT tournament.
		// Real result: 403 AUTH_006 — ManagerTournamentController.getTournament DOES enforce
		// ownership correctly. This refutes DEF-006 for this specific endpoint; see the narrowed
		// defect-log correction made after this run.
		mvc.perform(authed(get("/api/v1/manager/tournaments/{id}", tournamentId), managerBToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_006"));

		// TC-SYS-BF04-007 — Manager2 attempts to edit it. Real result: 403 AUTH_006 — the write
		// path IS correctly ownership-checked, unlike the read at Step 006 above. This narrows
		// DEF-006 to read-only endpoints; see the correction logged after this run.
		mvc.perform(authed(put("/api/v1/manager/tournaments/{id}", tournamentId), managerBToken)
						.content("""
								{"name":"Hijacked by Branch B QA","version":0}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_006"));

		// TC-SYS-BF04-008 — verification: rightful Manager (Branch A) still controls the resource
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerAToken)
						.content("""
								{"status":"CANCELLED"}
								"""))
				.andExpect(status().isOk());
	}

	private Number accessibleBranchId(String managerToken) throws Exception {
		var res = mvc.perform(authed(get("/api/v1/manager/branches"), managerToken))
				.andExpect(status().isOk())
				.andReturn();
		return read(bodyOf(res), "$.data.content[0].id");
	}
}
