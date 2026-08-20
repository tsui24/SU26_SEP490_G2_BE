package com.capstone.su26_sep490_g2_be.system;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-07 Tournament Draw & Bracket Confirmation.
 * Rows TC-SYS-BF07-001..011 in docs/Report_5.3_SystemTests_L3.md.
 */
@Transactional
class BF07_DrawBracketConfirmationSystemTest extends SystemTestBase {

	/** TC-SYS-BF07-001..006 — main flow: close registration, draw, confirm, publicly visible. */
	@Test
	void mainFlow_drawAndConfirm_officialBracketPublic() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createConfigurePublishTournament(managerToken, branchId, 4);
		addManualParticipants(managerToken, tournamentId, 4);

		// TC-SYS-BF07-002
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"REGISTRATION_CLOSED"}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF07-003
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/draw", tournamentId), managerToken))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.newStatus").value("DRAW_PREVIEW"));

		// TC-SYS-BF07-004
		mvc.perform(authed(get("/api/v1/manager/tournaments/{id}/stages", tournamentId), managerToken))
				.andExpect(status().isOk());

		// TC-SYS-BF07-005
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/draw/confirm", tournamentId), managerToken))
				.andExpect(status().isOk());

		// TC-SYS-BF07-006 — End condition: official bracket publicly visible
		mvc.perform(get("/api/v1/tournaments/{id}/stages", tournamentId))
				.andExpect(status().isOk());
		mvc.perform(authed(get("/api/v1/manager/tournaments/{id}", tournamentId), managerToken))
				.andExpect(jsonPath("$.data.status").value("DRAW_DONE"));
	}

	/**
	 * TC-SYS-BF07-007..011 — exception path: fewer than 2 participants blocks the draw;
	 * remediated by returning to BF-06 (adding a second participant).
	 */
	@Test
	void exceptionPath_notEnoughParticipants_remediatedViaBF06() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createConfigurePublishTournament(managerToken, branchId, 4);
		addManualParticipants(managerToken, tournamentId, 1);

		// TC-SYS-BF07-008
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"REGISTRATION_CLOSED"}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF07-009 — blocked, only 1 participant
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/draw", tournamentId), managerToken))
				.andExpect(status().isConflict());

		// TC-SYS-BF07-010 (remediation, back to BF-06)
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/manual", tournamentId), managerToken)
						.content("""
								{"displayName":"QA Second Player","seedNo":2}
								"""))
				.andExpect(status().isCreated());

		// TC-SYS-BF07-011 — succeeds now
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/draw", tournamentId), managerToken))
				.andExpect(status().isCreated());
	}

	private void addManualParticipants(String managerToken, Number tournamentId, int count) throws Exception {
		for (int i = 1; i <= count; i++) {
			mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/manual", tournamentId), managerToken)
							.content("""
									{"displayName":"QA BF07 Player %s","seedNo":%s}
									""".formatted(i, i)))
					.andExpect(status().isCreated());
		}
	}

	private Number createConfigurePublishTournament(String managerToken, Number branchId, int maxParticipants) throws Exception {
		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF07 Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":%s,"branchId":%s,"isRegister":false,
								 "isShowTournament":true,"isPublicRatio":true}
								""".formatted(uniq(), maxParticipants, branchId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		mvc.perform(authed(put("/api/v1/manager/tournaments/{id}/config", tournamentId), managerToken)
						.content("""
								{"seedingMethod":"RANK","raceToOverrides":[],"fields":[
								 {"fieldKey":"bracket_size","value":"8"},
								 {"fieldKey":"third_place_match","value":"true"},
								 {"fieldKey":"break_rule","value":"ALTERNATE_BREAK"},
								 {"fieldKey":"lag_for_break","value":"true"}
								]}
								"""))
				.andExpect(status().isOk());

		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"OPEN_FOR_REGISTRATION"}
								"""))
				.andExpect(status().isOk());

		return tournamentId;
	}

	private Number accessibleBranchId(String managerToken) throws Exception {
		var res = mvc.perform(authed(get("/api/v1/manager/branches"), managerToken))
				.andExpect(status().isOk())
				.andReturn();
		return read(bodyOf(res), "$.data.content[0].id");
	}
}
