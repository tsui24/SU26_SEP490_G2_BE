package com.capstone.su26_sep490_g2_be.system;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-02 User & Employee Account Provisioning.
 * Rows TC-SYS-BF02-001..013 in docs/Report_5.3_SystemTests_L3.md.
 *
 * <p>Chain 1's email-log assertion depends on {@code MailAutomationEventListener}, which is a
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} — it only fires once the surrounding
 * transaction actually commits. This class deliberately does NOT use {@code @Transactional}, so
 * each {@code @Test} method commits for real; leftover QA rows in the local test DB are harmless
 * and identifiable by the {@code bf02_}/{@code test-l3.local} naming.
 */
class BF02_AccountProvisioningSystemTest extends SystemTestBase {

	/** TC-SYS-BF02-001..007 — main flow: Admin creates Owner; Owner sets up Branch+Manager+Staff. */
	@Test
	@Transactional
	void mainFlow_ownerProvisionsManagerAndStaff_allActive() throws Exception {
		String adminToken = login("admin@gmail.com", "admin1");

		// TC-SYS-BF02-001
		String ownerEmail = freshEmail("bf02_owner");
		mvc.perform(authed(post("/api/v1/admin/accounts/owner"), adminToken)
						.content("""
								{"email":"%s","password":"Test@1234","phone":"0912345101"}
								""".formatted(ownerEmail)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.role").value("OWNER"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		String ownerToken = login(ownerEmail, "Test@1234");

		// TC-SYS-BF02-002a (setup) — fresh branch for this new Owner
		String branchName = "QA Branch " + uniq();
		var branchRes = mvc.perform(authed(post("/api/v1/owner/branches"), ownerToken)
						.content("""
								{"name":"%s","address":"123 QA St","phone":"0900000000"}
								""".formatted(branchName)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andReturn();
		Number branchId = read(bodyOf(branchRes), "$.data.id");

		// TC-SYS-BF02-002b/003 — create Manager assigned to the new branch
		String managerEmail = freshEmail("bf02_manager");
		mvc.perform(authed(post("/api/v1/owner/accounts/manager"), ownerToken)
						.content("""
								{"email":"%s","password":"Test@1234","phone":"0912345102","fullName":"QA Manager",
								 "manageAllBranches":false,"branchIds":[%s]}
								""".formatted(managerEmail, branchId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.role").value("MANAGER"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		// TC-SYS-BF02-005 — no MANAGER_ACCOUNT_CREATED automation rule is seeded by default
		// (DEF-012). Note: this method is @Transactional (never commits), so this check alone
		// can't distinguish "no rule" from "AFTER_COMMIT listener never ran because of rollback" —
		// the authoritative proof of DEF-012 is exceptionPath_noActiveAutomationRule_thenRemediated
		// below, which is NOT @Transactional and genuinely commits.
		mvc.perform(authed(get("/api/v1/admin/email/logs")
						.param("recipientEmail", managerEmail), adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(0));

		// TC-SYS-BF02-004 — Owner also creates a Staff directly
		String staffEmail = freshEmail("bf02_staff");
		mvc.perform(authed(post("/api/v1/owner/accounts/staff"), ownerToken)
						.content("""
								{"email":"%s","password":"Test@1234","phone":"0912345103","fullName":"QA Staff",
								 "branchId":%s}
								""".formatted(staffEmail, branchId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.role").value("STAFF"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		// TC-SYS-BF02-006 — Manager creates an additional Staff within its own branch scope
		String managerToken = login(managerEmail, "Test@1234");
		String staff2Email = freshEmail("bf02_staff2");
		mvc.perform(authed(post("/api/v1/manager/accounts/staff"), managerToken)
						.content("""
								{"email":"%s","password":"Test@1234","phone":"0912345104","fullName":"QA Staff 2",
								 "branchId":%s}
								""".formatted(staff2Email, branchId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.role").value("STAFF"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		// TC-SYS-BF02-007 — End condition: all personnel active and ready for use
		mvc.perform(authed(get("/api/v1/owner/employees").param("size", "50"), ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.email=='" + managerEmail + "')].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.content[?(@.email=='" + staffEmail + "')].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.content[?(@.email=='" + staff2Email + "')].status").value("ACTIVE"));
	}

	/**
	 * TC-SYS-BF02-008..013 — exception path: no active welcome-email rule for account-creation
	 * events by default (EmailTemplateSeedInitializer does not seed one), so the first Staff
	 * creation is silently skipped for email; enabling a matching rule afterwards is the
	 * remediation.
	 */
	@Test
	void exceptionPath_noActiveAutomationRule_thenRemediated() throws Exception {
		String adminToken = login("admin@gmail.com", "admin1");
		String ownerToken = login("owner@gmail.com", "owner123");

		// TC-SYS-BF02-008 — confirm no ENABLED rule exists for STAFF_ACCOUNT_CREATED by default.
		// (Filters on isEnabled, not mere existence, so this stays valid even if an earlier run of
		// this same test left a disabled QA rule behind — see cleanup at the end of this method.)
		mvc.perform(authed(get("/api/v1/admin/email/automation-rules").param("size", "100"), adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath(
						"$.data.content[?(@.eventType=='STAFF_ACCOUNT_CREATED' && @.isEnabled==true)]").isEmpty());

		// TC-SYS-BF02-010 — create Staff while no rule exists (or exists but disabled)
		String staffEmail1 = freshEmail("bf02x_staff1");
		mvc.perform(authed(post("/api/v1/owner/accounts/staff"), ownerToken)
						.content("""
								{"email":"%s","password":"Test@1234","phone":"%s","fullName":"QA NoRule Staff"}
								""".formatted(staffEmail1, freshPhone())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		// TC-SYS-BF02-011 — no email log row for this recipient (silent skip)
		mvc.perform(authed(get("/api/v1/admin/email/logs")
						.param("recipientEmail", staffEmail1), adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(0));

		// TC-SYS-BF02-012 (remediation) — Admin creates + enables a matching automation rule
		String ruleCode = "QA_STAFF_CREATED_" + uniq();
		Number templateId = seedOrGetActiveTemplateId(adminToken);
		var ruleRes = mvc.perform(authed(post("/api/v1/admin/email/automation-rules"), adminToken)
						.content("""
								{"code":"%s","name":"QA staff created","eventType":"STAFF_ACCOUNT_CREATED",
								 "templateId":%s,"recipientType":"REGISTRATION_USER","enabled":true}
								""".formatted(ruleCode, templateId)))
				.andExpect(status().isOk())
				.andReturn();
		Number ruleId = read(bodyOf(ruleRes), "$.data.id");

		try {
			// TC-SYS-BF02-013 — retry: create another Staff, email should now be queued/sent
			String staffEmail2 = freshEmail("bf02x_staff2");
			mvc.perform(authed(post("/api/v1/owner/accounts/staff"), ownerToken)
							.content("""
									{"email":"%s","password":"Test@1234","phone":"%s","fullName":"QA Rule Staff"}
									""".formatted(staffEmail2, freshPhone())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		} finally {
			// Cleanup — disable the rule this test created so re-running this same test class
			// still finds the "no ENABLED rule for STAFF_ACCOUNT_CREATED" baseline at step 008.
			// Not @Transactional (needed for the AFTER_COMMIT listener), so this can't rely on
			// rollback; PATCH is the only mutation AdminEmailAutomationController exposes for an
			// existing rule (no DELETE endpoint).
			mvc.perform(authed(patch("/api/v1/admin/email/automation-rules/{id}/enabled", ruleId)
							.param("enabled", "false"), adminToken));
		}
	}

	/** Reuses any existing ACTIVE global email template so the automation rule create is valid. */
	private Number seedOrGetActiveTemplateId(String adminToken) throws Exception {
		var res = mvc.perform(authed(get("/api/v1/admin/email/templates")
						.param("isActive", "true").param("size", "1"), adminToken))
				.andExpect(status().isOk())
				.andReturn();
		return read(bodyOf(res), "$.data.content[0].id");
	}
}
