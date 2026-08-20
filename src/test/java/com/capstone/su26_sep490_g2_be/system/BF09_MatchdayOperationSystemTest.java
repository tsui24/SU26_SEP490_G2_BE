package com.capstone.su26_sep490_g2_be.system;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-09 Matchday Operation & Live Scoring.
 * Rows TC-SYS-BF09-001..017 in docs/Report_5.3_SystemTests_L3.md.
 */
@Transactional
class BF09_MatchdayOperationSystemTest extends SystemTestBase {

	/** TC-SYS-BF09-001..012 — main flow: 4-player Single Elimination played out to COMPLETED. */
	@Test
	void mainFlow_playOutBracket_tournamentCompletedWithRankings() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		String staffToken = login("staff1@gmail.com", "staff123");
		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createDrawnTournament(managerToken, branchId, 4);

		// TC-SYS-BF09-004
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"IN_PROGRESS"}
								"""))
				.andExpect(status().isOk());

		List<Map<String, Object>> semis = pairedMatches(managerToken, tournamentId);

		// TC-SYS-BF09-005/006/008 — referee-assigned semifinal
		Map<String, Object> semi1 = semis.get(0);
		mvc.perform(authed(patch("/api/v1/manager/matches/{id}/assignment", semi1.get("id")), managerToken)
						.content("""
								{"assignedStaffId":%s}
								""".formatted(staffAccountId())))
				.andExpect(status().isOk());
		mvc.perform(authed(patch("/api/v1/staff/matches/{id}/start", semi1.get("id")), staffToken))
				.andExpect(status().isOk());
		completeAsStaff(staffToken, semi1);

		// TC-SYS-BF09-009 — no-referee alt path: Manager scores directly
		Map<String, Object> semi2 = semis.get(1);
		mvc.perform(authed(patch("/api/v1/manager/matches/{id}/start", semi2.get("id")), managerToken))
				.andExpect(status().isOk());
		completeAsManager(managerToken, semi2);

		// TC-SYS-BF09-010 — remaining matches (final, and third-place since config enabled it).
		// Looped rather than hardcoded to a single "final" match, since third_place_match=true
		// creates a 4th match alongside the final that must also finish before completion.
		List<Map<String, Object>> remaining = pairedMatches(managerToken, tournamentId);
		while (!remaining.isEmpty()) {
			for (Map<String, Object> m : remaining) {
				mvc.perform(authed(patch("/api/v1/manager/matches/{id}/start", m.get("id")), managerToken))
						.andExpect(status().isOk());
				completeAsManager(managerToken, m);
			}
			remaining = pairedMatches(managerToken, tournamentId);
		}

		// TC-SYS-BF09-011
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"COMPLETED"}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF09-012 — End condition
		mvc.perform(get("/api/v1/tournaments/{id}/rankings", tournamentId))
				.andExpect(status().isOk());
	}

	/**
	 * TC-SYS-BF09-013..017 — exception path targeting the GB-12 / DEF-007 finding. Real execution
	 * REFUTES it for {@code ManagerController#start} too — both the Staff and Manager paths
	 * correctly return 403 for an out-of-scope actor. DEF-007 narrowed accordingly, same as
	 * DEF-006 was for BF-04.
	 */
	@Test
	void exceptionPath_staffBlocked_crossBranchManagerGb12() throws Exception {
		String managerAToken = login("manager@gmail.com", "manager123");
		String managerBToken = login("manager2@gmail.com", "manager123");
		String staffBToken = login("staff2@gmail.com", "staff123");
		Number branchAId = accessibleBranchId(managerAToken);
		Number tournamentId = createDrawnTournament(managerAToken, branchAId, 4);

		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerAToken)
						.content("""
								{"status":"IN_PROGRESS"}
								"""))
				.andExpect(status().isOk());

		Map<String, Object> match = pairedMatches(managerAToken, tournamentId).get(0);

		// TC-SYS-BF09-014 — Staff from Branch B, not assigned to this match: correctly blocked
		mvc.perform(authed(patch("/api/v1/staff/matches/{id}/start", match.get("id")), staffBToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MATCH_001"));

		// TC-SYS-BF09-015 — Manager2 (Branch B, not this tournament's Manager) attempts to start
		// the same match. Real result: 403 AUTH_006 — refutes the GB-12/DEF-007 claim for this
		// endpoint too (see DEF-006's correction for the equivalent BF-04 finding).
		mvc.perform(authed(patch("/api/v1/manager/matches/{id}/start", match.get("id")), managerBToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_006"));
	}

	@SuppressWarnings("unchecked")
	private void completeAsStaff(String staffToken, Map<String, Object> match) throws Exception {
		Map<String, Object> player1 = (Map<String, Object>) match.get("player1");
		mvc.perform(authed(post("/api/v1/staff/matches/{id}/complete", match.get("id")), staffToken)
						.content("""
								{"winnerParticipantId":%s,"confirmEarlyEnd":true}
								""".formatted(player1.get("id"))))
				.andExpect(status().isOk());
	}

	@SuppressWarnings("unchecked")
	private void completeAsManager(String managerToken, Map<String, Object> match) throws Exception {
		Map<String, Object> player1 = (Map<String, Object>) match.get("player1");
		mvc.perform(authed(post("/api/v1/manager/matches/{id}/complete", match.get("id")), managerToken)
						.content("""
								{"winnerParticipantId":%s,"confirmEarlyEnd":true}
								""".formatted(player1.get("id"))))
				.andExpect(status().isOk());
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> pairedMatches(String managerToken, Number tournamentId) throws Exception {
		MvcResult res = mvc.perform(authed(get("/api/v1/manager/tournaments/{id}/matches", tournamentId), managerToken))
				.andExpect(status().isOk())
				.andReturn();
		List<Map<String, Object>> all = read(bodyOf(res), "$.data");
		return all.stream()
				.filter(m -> "PENDING".equals(m.get("status"))
						&& m.get("player1") != null && m.get("player2") != null)
				.toList();
	}

	private Number staffAccountId() throws Exception {
		String adminToken = login("admin@gmail.com", "admin1");
		var res = mvc.perform(authed(get("/api/v1/admin/accounts").param("role", "STAFF").param("search", "staff1"), adminToken))
				.andExpect(status().isOk())
				.andReturn();
		List<Number> ids = read(bodyOf(res), "$.data.content[?(@.email=='staff1@gmail.com')].id");
		return ids.get(0);
	}

	private Number createDrawnTournament(String managerToken, Number branchId, int maxParticipants) throws Exception {
		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF09 Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
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

		for (int i = 1; i <= maxParticipants; i++) {
			mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/manual", tournamentId), managerToken)
							.content("""
									{"displayName":"QA BF09 Player %s","seedNo":%s}
									""".formatted(i, i)))
					.andExpect(status().isCreated());
		}

		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"REGISTRATION_CLOSED"}
								"""))
				.andExpect(status().isOk());

		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/draw", tournamentId), managerToken))
				.andExpect(status().isCreated());
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/draw/confirm", tournamentId), managerToken))
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
