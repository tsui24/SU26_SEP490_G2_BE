package com.capstone.su26_sep490_g2_be.system;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-11 News Publication & Category Management.
 * Rows TC-SYS-BF11-001..008 in docs/Report_5.3_SystemTests_L3.md.
 */
@Transactional
class BF11_NewsPublicationSystemTest extends SystemTestBase {

	/** TC-SYS-BF11-001..005 — main flow: category, tag, draft (tagged), publish, publicly readable. */
	@Test
	void mainFlow_draftThenPublish_publiclyReadable() throws Exception {
		String ownerToken = login("owner@gmail.com", "owner123");
		String suffix = uniq();

		// TC-SYS-BF11-001
		var catRes = mvc.perform(authed(post("/api/v1/owner/news/categories"), ownerToken)
						.content("""
								{"name":"QA Category %s","slug":"qa-category-%s"}
								""".formatted(suffix, suffix)))
				.andExpect(status().isCreated())
				.andReturn();
		Number categoryId = read(bodyOf(catRes), "$.data.id");

		// TC-SYS-BF11-002 (BF Step 1b)
		var tagRes = mvc.perform(authed(post("/api/v1/shared/news/tags"), ownerToken)
						.content("""
								{"name":"QA Tag %s","slug":"qa-tag-%s"}
								""".formatted(suffix, suffix)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.name").value("QA Tag " + suffix))
				.andReturn();
		Number tagId = read(bodyOf(tagRes), "$.data.id");

		// TC-SYS-BF11-003 — article created with the tag from Step 1b attached
		String slug = "qa-article-" + suffix;
		var postRes = mvc.perform(authed(post("/api/v1/owner/news"), ownerToken)
						.content("""
								{"title":"QA Article %s","slug":"%s","categoryId":%s,"content":"<p>QA content</p>",
								 "tagIds":[%s]}
								""".formatted(suffix, slug, categoryId, tagId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("DRAFT"))
				.andExpect(jsonPath("$.data.tagIds[0]").value(tagId.longValue()))
				.andReturn();
		Number postId = read(bodyOf(postRes), "$.data.id");

		// TC-SYS-BF11-004
		mvc.perform(authed(post("/api/v1/owner/news/{id}/publish", postId), ownerToken))
				.andExpect(status().isOk());

		// TC-SYS-BF11-005 — End condition
		mvc.perform(get("/api/v1/news/{slug}", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PUBLISHED"));
	}

	/**
	 * TC-SYS-BF11-006..008 — exception path: DRAFT is invisible to the public even by exact slug,
	 * PUBLISHED becomes visible, then HIDDEN goes invisible again — the full lifecycle only L3
	 * can demonstrate across multiple steps.
	 */
	@Test
	void exceptionPath_draftAndHiddenBothInvisible_publishedVisible() throws Exception {
		String ownerToken = login("owner@gmail.com", "owner123");
		String suffix = uniq();

		var catRes = mvc.perform(authed(post("/api/v1/owner/news/categories"), ownerToken)
						.content("""
								{"name":"QA Category2 %s","slug":"qa-category2-%s"}
								""".formatted(suffix, suffix)))
				.andExpect(status().isCreated())
				.andReturn();
		Number categoryId = read(bodyOf(catRes), "$.data.id");

		String slug = "qa-lifecycle-" + suffix;
		var postRes = mvc.perform(authed(post("/api/v1/owner/news"), ownerToken)
						.content("""
								{"title":"QA Lifecycle %s","slug":"%s","categoryId":%s,"content":"<p>QA</p>"}
								""".formatted(suffix, slug, categoryId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number postId = read(bodyOf(postRes), "$.data.id");

		// TC-SYS-BF11-006 — DRAFT invisible even by exact slug
		mvc.perform(get("/api/v1/news/{slug}", slug))
				.andExpect(status().isNotFound());

		mvc.perform(authed(post("/api/v1/owner/news/{id}/publish", postId), ownerToken))
				.andExpect(status().isOk());

		// TC-SYS-BF11-007 — now visible
		mvc.perform(get("/api/v1/news/{slug}", slug))
				.andExpect(status().isOk());

		mvc.perform(authed(post("/api/v1/owner/news/{id}/hide", postId), ownerToken))
				.andExpect(status().isOk());

		// TC-SYS-BF11-008 — HIDDEN invisible again
		mvc.perform(get("/api/v1/news/{slug}", slug))
				.andExpect(status().isNotFound());
	}
}
