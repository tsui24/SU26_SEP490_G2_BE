package com.capstone.su26_sep490_g2_be.system;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-01 User Registration & Authentication.
 * Rows TC-SYS-BF01-001..009 in docs/Report_5.3_SystemTests_L3.md.
 */
@Transactional
class BF01_UserRegistrationAuthenticationSystemTest extends SystemTestBase {

	/** TC-SYS-BF01-001..003 — main flow: register, login, /me proves role-scoped session. */
	@Test
	void mainFlow_registerLoginMe_sessionScopedToPlayerRole() throws Exception {
		String email = freshEmail("bf01_player");
		String password = "Test@1234";

		// TC-SYS-BF01-001
		mvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"%s","phone":"0912345001"}
								""".formatted(email, password)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.user.role").value("PLAYER"))
				.andExpect(jsonPath("$.data.user.status").value("ACTIVE"));

		// TC-SYS-BF01-002
		var loginRes = mvc.perform(post("/api/v1/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		String token = read(bodyOf(loginRes), "$.data.token");

		// TC-SYS-BF01-003 — End condition: authenticated session scoped to the correct role
		mvc.perform(authed(get("/api/v1/auth/me"), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("PLAYER"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}

	/**
	 * TC-SYS-BF01-004..009 — exception path: account LOCKED mid-session.
	 * Spans Owner + Staff; proves the stateless JWT is rejected per-request after lock, not only
	 * at login (JwtAuthenticationFilter re-checks DB status on every call).
	 */
	@Test
	void exceptionPath_lockedMidSession_oldTokenRejectedOnNextRequest() throws Exception {
		String ownerToken = login("owner@gmail.com", "owner123");
		String staffEmail = freshEmail("bf01_staff");

		// TC-SYS-BF01-004
		var createRes = mvc.perform(authed(post("/api/v1/owner/accounts/staff"), ownerToken)
						.content("""
								{"email":"%s","password":"Test@1234","phone":"0912345002","fullName":"QA Staff BF01"}
								""".formatted(staffEmail)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andReturn();
		Number staffId = read(bodyOf(createRes), "$.data.id");

		// TC-SYS-BF01-005
		String staffToken = login(staffEmail, "Test@1234");

		// TC-SYS-BF01-006 — session healthy before lock
		mvc.perform(authed(get("/api/v1/auth/me"), staffToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		// TC-SYS-BF01-007 — Owner locks the Staff account (cross-actor step)
		mvc.perform(authed(put("/api/v1/owner/employees/{id}/deactivate", staffId), ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("LOCKED"));

		// TC-SYS-BF01-008 — old token still unexpired, but must now be rejected.
		// AUTH_ACCOUNT_LOCKED carries a single fixed HttpStatus.FORBIDDEN (ErrorCode.java) used by
		// every call site (login, /me, and this mid-session re-check) — not 401 as originally
		// assumed during endpoint research; corrected here after real execution.
		mvc.perform(authed(get("/api/v1/auth/me"), staffToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_008"));

		// TC-SYS-BF01-009 — fresh login attempt also rejected
		mvc.perform(post("/api/v1/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"Test@1234"}
								""".formatted(staffEmail)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_008"));
	}
}
