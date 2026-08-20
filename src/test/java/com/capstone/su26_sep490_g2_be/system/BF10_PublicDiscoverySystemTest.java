package com.capstone.su26_sep490_g2_be.system;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-10 Public Tournament Discovery.
 * Rows TC-SYS-BF10-001..008 in docs/Report_5.3_SystemTests_L3.md.
 */
@Transactional
class BF10_PublicDiscoverySystemTest extends SystemTestBase {

	/** TC-SYS-BF10-001..004 — main flow: freshly created+published tournament is publicly discoverable. */
	@Test
	void mainFlow_publishedTournament_discoverableByGuest() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);
		String name = "QA BF10 Tour " + uniq();

		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"%s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":8,"branchId":%s,"isRegister":false,
								 "isShowTournament":true,"isPublicRatio":true}
								""".formatted(name, branchId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"OPEN_FOR_REGISTRATION"}
								"""))
				.andExpect(status().isOk());

		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/manual", tournamentId), managerToken)
						.content("""
								{"displayName":"QA BF10 Participant","seedNo":1}
								"""))
				.andExpect(status().isCreated());

		// TC-SYS-BF10-002
		mvc.perform(get("/api/v1/tournaments").param("search", name))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.name=='" + name + "')]").exists());

		// TC-SYS-BF10-003
		mvc.perform(get("/api/v1/tournaments/{id}", tournamentId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("OPEN_FOR_REGISTRATION"));

		// TC-SYS-BF10-004 — End condition
		mvc.perform(get("/api/v1/tournaments/{id}/participants", tournamentId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.displayName=='QA BF10 Participant')]").exists());
	}

	/**
	 * TC-SYS-BF10-005..008 — exception path: a tournament with {@code isShowTournament=false} is
	 * unavailable via the public endpoint, then becomes visible after the Manager flips visibility.
	 */
	@Test
	void exceptionPath_notPublicUntilVisibilityEnabled() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);

		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF10x Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":8,"branchId":%s,"isRegister":false,
								 "isShowTournament":false}
								""".formatted(uniq(), branchId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"OPEN_FOR_REGISTRATION"}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF10-006
		mvc.perform(get("/api/v1/tournaments/{id}", tournamentId))
				.andExpect(status().isNotFound());

		// TC-SYS-BF10-007 (remediation)
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/visibility", tournamentId), managerToken)
						.content("""
								{"isShowTournament":true}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF10-008
		mvc.perform(get("/api/v1/tournaments/{id}", tournamentId))
				.andExpect(status().isOk());
	}

	private Number accessibleBranchId(String managerToken) throws Exception {
		var res = mvc.perform(authed(get("/api/v1/manager/branches"), managerToken))
				.andExpect(status().isOk())
				.andReturn();
		return read(bodyOf(res), "$.data.content[0].id");
	}
}
