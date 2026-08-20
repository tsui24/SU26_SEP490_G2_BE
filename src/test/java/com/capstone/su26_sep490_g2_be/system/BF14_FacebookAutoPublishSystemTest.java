package com.capstone.su26_sep490_g2_be.system;

import com.capstone.su26_sep490_g2_be.service.FacebookPublishService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-14 Facebook Auto-publish & Insights.
 * Rows TC-SYS-BF14-001..007 in docs/Report_5.3_SystemTests_L3.md.
 *
 * <p>{@code FacebookAutoPostListener.onTournamentPublished} is
 * {@code @Async @TransactionalEventListener(AFTER_COMMIT) @Transactional(REQUIRES_NEW)} — it only
 * fires once the tournament-publish transaction actually commits, and runs on a separate thread.
 * This class is deliberately NOT {@code @Transactional} (so the publish commits for real), and
 * assertions poll briefly for the async listener to finish. {@code FacebookPublishService} is
 * spied out to skip the real, unreachable Graph API call — same reasoning as BF-05's PayOS spy.
 */
class BF14_FacebookAutoPublishSystemTest extends SystemTestBase {

	@MockitoSpyBean
	FacebookPublishService facebookPublishService;

	/** TC-SYS-BF14-001..003 — main flow: publishing publicly triggers an auto-post, visible via API. */
	@Test
	void mainFlow_publishPublicly_facebookPostStored() throws Exception {
		doReturn("fake_fb_post_" + uniq()).when(facebookPublishService).publishTextPost(anyString(), anyString());

		String managerToken = login("manager@gmail.com", "manager123");
		String ownerToken = login("owner@gmail.com", "owner123");
		Number branchId = accessibleBranchId(managerToken);

		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF14 Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":8,"branchId":%s,"isRegister":false,
								 "isShowTournament":true}
								""".formatted(uniq(), branchId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"OPEN_FOR_REGISTRATION"}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF14-002/003 — wait briefly for the @Async AFTER_COMMIT listener to run
		waitForFacebookPost(ownerToken, tournamentId, true);
	}

	/**
	 * TC-SYS-BF14-004..007 — exception path: isShowTournament=false means no post is even
	 * attempted, though the tournament publish itself still fully succeeds.
	 */
	@Test
	void exceptionPath_notShownPublicly_noAutoPostAttempted() throws Exception {
		doReturn("fake_fb_post_" + uniq()).when(facebookPublishService).publishTextPost(anyString(), anyString());

		String managerToken = login("manager@gmail.com", "manager123");
		String ownerToken = login("owner@gmail.com", "owner123");
		Number branchId = accessibleBranchId(managerToken);

		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF14x Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":8,"branchId":%s,"isRegister":false,
								 "isShowTournament":false}
								""".formatted(uniq(), branchId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		// TC-SYS-BF14-005 — publish still fully succeeds regardless of the FB gate
		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"OPEN_FOR_REGISTRATION"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("OPEN_FOR_REGISTRATION"));

		// TC-SYS-BF14-006/007 — no post ever attempted
		waitForFacebookPost(ownerToken, tournamentId, false);
	}

	private void waitForFacebookPost(String ownerToken, Number tournamentId, boolean expectPresent) throws Exception {
		boolean found = false;
		for (int i = 0; i < 10 && !found; i++) {
			var res = mvc.perform(authed(get("/api/v1/shared/facebook/posts/tournament/{id}", tournamentId), ownerToken))
					.andExpect(status().isOk())
					.andReturn();
			java.util.List<?> posts = read(bodyOf(res), "$.data");
			found = !posts.isEmpty();
			if (found || !expectPresent) break;
			Thread.sleep(300);
		}
		if (expectPresent) {
			org.junit.jupiter.api.Assertions.assertTrue(found, "expected a Facebook post to be stored");
		} else {
			// Give the (non-existent) async path a brief window, then confirm it never posted.
			Thread.sleep(500);
			var res = mvc.perform(authed(get("/api/v1/shared/facebook/posts/tournament/{id}", tournamentId), ownerToken))
					.andExpect(status().isOk())
					.andReturn();
			java.util.List<?> posts = read(bodyOf(res), "$.data");
			org.junit.jupiter.api.Assertions.assertTrue(posts.isEmpty(), "expected no Facebook post to be stored");
		}
	}

	private Number accessibleBranchId(String managerToken) throws Exception {
		var res = mvc.perform(authed(get("/api/v1/manager/branches"), managerToken))
				.andExpect(status().isOk())
				.andReturn();
		return read(bodyOf(res), "$.data.content[0].id");
	}
}
