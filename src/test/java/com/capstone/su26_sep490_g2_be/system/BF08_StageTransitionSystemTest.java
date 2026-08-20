package com.capstone.su26_sep490_g2_be.system;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-08 Tournament Stage Transition (Progressive Round Robin).
 * Rows TC-SYS-BF08-001..012 in docs/Report_5.3_SystemTests_L3.md.
 */
@Transactional
class BF08_StageTransitionSystemTest extends SystemTestBase {

	/** TC-SYS-BF08-001..007 — main flow: finish Stage 1, advance into the playoff stage. */
	@Test
	void mainFlow_advanceStage_playoffPopulated() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createDrawnProgressiveTournament(managerToken, branchId);

		// TC-SYS-BF08-004
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"IN_PROGRESS"}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF08-005 — finish every Stage-1 match (early-end declared winner)
		completeAllPendingMatches(managerToken, tournamentId);

		// TC-SYS-BF08-006
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/advance-stage", tournamentId), managerToken))
				.andExpect(status().isOk());

		// TC-SYS-BF08-007 — End condition
		mvc.perform(authed(get("/api/v1/manager/tournaments/{id}/stages", tournamentId), managerToken))
				.andExpect(status().isOk());
	}

	/**
	 * TC-SYS-BF08-008..012 — exception path: unfinished stage blocks advance-stage; remediated by
	 * finishing the last match.
	 */
	@Test
	void exceptionPath_unfinishedStageBlocksAdvance_thenRemediated() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createDrawnProgressiveTournament(managerToken, branchId);

		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"IN_PROGRESS"}
								"""))
				.andExpect(status().isOk());

		List<Map<String, Object>> matches = pendingMatches(managerToken, tournamentId);
		// TC-SYS-BF08-009 — complete all but one
		for (int i = 0; i < matches.size() - 1; i++) {
			completeMatch(managerToken, matches.get(i));
		}

		// TC-SYS-BF08-010 — blocked, one match still unfinished
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/advance-stage", tournamentId), managerToken))
				.andExpect(status().isConflict());

		// TC-SYS-BF08-011 (remediation)
		completeMatch(managerToken, matches.get(matches.size() - 1));

		// TC-SYS-BF08-012 — succeeds now
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/advance-stage", tournamentId), managerToken))
				.andExpect(status().isOk());
	}

	@SuppressWarnings("unchecked")
	private void completeAllPendingMatches(String managerToken, Number tournamentId) throws Exception {
		for (Map<String, Object> match : pendingMatches(managerToken, tournamentId)) {
			completeMatch(managerToken, match);
		}
	}

	@SuppressWarnings("unchecked")
	private void completeMatch(String managerToken, Map<String, Object> match) throws Exception {
		Object matchId = match.get("id");
		Map<String, Object> player1 = (Map<String, Object>) match.get("player1");
		Object winnerId = player1.get("id");
		mvc.perform(authed(patch("/api/v1/manager/matches/{id}/start", matchId), managerToken))
				.andExpect(status().isOk());
		mvc.perform(authed(post("/api/v1/manager/matches/{id}/complete", matchId), managerToken)
						.content("""
								{"winnerParticipantId":%s,"confirmEarlyEnd":true}
								""".formatted(winnerId)))
				.andExpect(status().isOk());
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> pendingMatches(String managerToken, Number tournamentId) throws Exception {
		MvcResult res = mvc.perform(authed(get("/api/v1/manager/tournaments/{id}/matches", tournamentId), managerToken))
				.andExpect(status().isOk())
				.andReturn();
		List<Map<String, Object>> all = read(bodyOf(res), "$.data");
		// Excludes not-yet-populated playoff-placeholder matches (status=PENDING but player1/2
		// are null until advance-stage seeds them) — only fully-paired Stage-1 matches are playable.
		return all.stream()
				.filter(m -> "PENDING".equals(m.get("status"))
						&& m.get("player1") != null && m.get("player2") != null)
				.toList();
	}

	private Number createDrawnProgressiveTournament(String managerToken, Number branchId) throws Exception {
		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF08 Tour %s","gameType":"9_BALL","format":"PROGRESSIVE_ROUND_ROBIN",
								 "participantType":"SINGLE","maxParticipants":6,"branchId":%s,"isRegister":false,
								 "isShowTournament":true,"isPublicRatio":true}
								""".formatted(uniq(), branchId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		mvc.perform(authed(put("/api/v1/manager/tournaments/{id}/config", tournamentId), managerToken)
						.content("""
								{"seedingMethod":"RANK","raceToOverrides":[],"fields":[
								 {"fieldKey":"pe_survivors_per_stage","value":"4"},
								 {"fieldKey":"final_playoff_size","value":"4"},
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

		for (int i = 1; i <= 6; i++) {
			mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/manual", tournamentId), managerToken)
							.content("""
									{"displayName":"QA BF08 Player %s","seedNo":%s}
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
