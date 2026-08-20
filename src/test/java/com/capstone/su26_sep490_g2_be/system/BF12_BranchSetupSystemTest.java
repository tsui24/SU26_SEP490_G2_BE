package com.capstone.su26_sep490_g2_be.system;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-12 Branch Setup & Management.
 * Rows TC-SYS-BF12-001..010 in docs/Report_5.3_SystemTests_L3.md.
 */
@Transactional
class BF12_BranchSetupSystemTest extends SystemTestBase {

	/** TC-SYS-BF12-001..005 — main flow: create, update, assign a Manager, publicly listed. */
	@Test
	void mainFlow_createUpdateAssignManager_publiclyListed() throws Exception {
		String ownerToken = login("owner@gmail.com", "owner123");
		String name = "QA BF12 Branch " + uniq();

		// TC-SYS-BF12-001
		var bRes = mvc.perform(authed(post("/api/v1/owner/branches"), ownerToken)
						.content("""
								{"name":"%s","address":"123 QA St","phone":"0900000001"}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andReturn();
		Number branchId = read(bodyOf(bRes), "$.data.id");

		// TC-SYS-BF12-002
		mvc.perform(authed(put("/api/v1/owner/branches/{id}", branchId), ownerToken)
						.content("""
								{"name":"%s","address":"456 QA Ave Updated","phone":"0900000002"}
								""".formatted(name)))
				.andExpect(status().isOk());

		// TC-SYS-BF12-003
		String managerEmail = freshEmail("bf12_manager");
		mvc.perform(authed(post("/api/v1/owner/accounts/manager"), ownerToken)
						.content("""
								{"email":"%s","password":"Test@1234","phone":"%s","fullName":"QA BF12 Manager",
								 "manageAllBranches":false,"branchIds":[%s]}
								""".formatted(managerEmail, freshPhone(), branchId)))
				.andExpect(status().isCreated());

		// TC-SYS-BF12-004 — End condition: publicly browsable
		mvc.perform(get("/api/v1/branches").param("search", name))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.name=='" + name + "')]").exists());

		// TC-SYS-BF12-005
		String managerToken = login(managerEmail, "Test@1234");
		mvc.perform(authed(get("/api/v1/manager/branches"), managerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.id==" + branchId + ")]").exists());
	}

	/**
	 * TC-SYS-BF12-006..010 — exception path: INACTIVE branch is removed from the public catalog
	 * and blocks new tournament creation (correct), but a new Manager can still be assigned to it
	 * (DEF-011 — inconsistent status enforcement).
	 */
	@Test
	void exceptionPath_inactiveBranch_blocksTournamentsButNotManagerAssignment() throws Exception {
		String ownerToken = login("owner@gmail.com", "owner123");
		String name = "QA BF12x Branch " + uniq();

		var bRes = mvc.perform(authed(post("/api/v1/owner/branches"), ownerToken)
						.content("""
								{"name":"%s","address":"789 QA Blvd","phone":"0900000003"}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		Number branchId = read(bodyOf(bRes), "$.data.id");

		// TC-SYS-BF12-007
		mvc.perform(authed(patch("/api/v1/owner/branches/{id}/status", branchId), ownerToken)
						.content("""
								{"status":"INACTIVE"}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF12-008 — removed from public catalog
		mvc.perform(get("/api/v1/branches").param("search", name))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.name=='" + name + "')]").doesNotExist());

		// TC-SYS-BF12-009 — blocked from new tournament creation. Uses Owner (who owns this fresh
		// branch), not Manager — manager@gmail.com is only assigned to the seeded Branch A, not
		// this newly created one, which would fail with an unrelated 403 BRANCH_002 first.
		mvc.perform(authed(post("/api/v1/owner/tournaments"), ownerToken)
						.content("""
								{"name":"QA Should Fail %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":8,"branchId":%s,"isRegister":false}
								""".formatted(uniq(), branchId)))
				.andExpect(status().is(422));

		// TC-SYS-BF12-010 — DEF-011: Manager assignment to the same INACTIVE branch is NOT blocked
		String newManagerEmail = freshEmail("bf12x_manager");
		mvc.perform(authed(post("/api/v1/owner/accounts/manager"), ownerToken)
						.content("""
								{"email":"%s","password":"Test@1234","phone":"%s","fullName":"QA Inactive-Branch Manager",
								 "manageAllBranches":false,"branchIds":[%s]}
								""".formatted(newManagerEmail, freshPhone(), branchId)))
				.andExpect(status().isCreated());
	}
}
