package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.NewsCategoryRequest;
import com.capstone.su26_sep490_g2_be.dto.request.NewsPostRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — News/Blog: public read (không cần auth) + Owner/Manager CMS (GB-05 chỉ thấy bài của owner mình). */
class NewsControllerL2Test extends AbstractControllerIntegrationTest {

	private Long createCategory(String auth) throws Exception {
		NewsCategoryRequest req = new NewsCategoryRequest();
		req.setName("L2 Category " + System.nanoTime());
		req.setSlug("l2-cat-" + System.nanoTime());
		String body = mockMvc.perform(post("/api/v1/owner/news/categories")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).path("data").path("id").asLong();
	}

	// ── Public ───────────────────────────────────────────────────────────

	@Test
	void listPublished_withoutToken_ok() throws Exception {
		mockMvc.perform(get("/api/v1/news"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void getBySlug_unknownSlug_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/news/{slug}", "khong-ton-tai-slug"))
				.andExpect(status().isNotFound());
	}

	@Test
	void listCategories_public_ok() throws Exception {
		mockMvc.perform(get("/api/v1/news/categories"))
				.andExpect(status().isOk());
	}

	// ── Owner CMS ────────────────────────────────────────────────────────

	@Test
	void ownerList_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/owner/news"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void ownerList_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/owner/news")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void fullLifecycle_create_publish_hide_delete() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long categoryId = createCategory(ownerAuth);

		NewsPostRequest create = new NewsPostRequest();
		create.setTitle("Bài viết L2 test");
		create.setSlug("bai-viet-l2-test-" + System.nanoTime());
		create.setCategoryId(categoryId);
		create.setContent("Nội dung test");

		String body = mockMvc.perform(post("/api/v1/owner/news")
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long postId = objectMapper.readTree(body).path("data").path("id").asLong();

		mockMvc.perform(get("/api/v1/owner/news/{id}", postId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/owner/news/{id}/publish", postId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/owner/news/{id}/hide", postId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/owner/news/{id}", postId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk());
	}

	@Test
	void ownerCreate_blankTitle_rejected400() throws Exception {
		NewsPostRequest req = new NewsPostRequest();
		req.setTitle("");
		req.setSlug("slug-x");
		req.setCategoryId(1L);
		req.setContent("content");

		mockMvc.perform(post("/api/v1/owner/news")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ownerCategoryCrud_updateThenStatus() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long categoryId = createCategory(ownerAuth);

		NewsCategoryRequest update = new NewsCategoryRequest();
		update.setName("L2 Category (updated)");
		update.setSlug("l2-cat-updated-" + System.nanoTime());

		mockMvc.perform(put("/api/v1/owner/news/categories/{id}", categoryId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("L2 Category (updated)"));

		mockMvc.perform(patch("/api/v1/owner/news/categories/{id}/status", categoryId)
						.header("Authorization", ownerAuth)
						.param("status", "INACTIVE"))
				.andExpect(status().isOk());
	}

	// ── Manager CMS (mirror) ────────────────────────────────────────────

	@Test
	void managerList_asManager_ok() throws Exception {
		mockMvc.perform(get("/api/v1/manager/news")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk());
	}

	@Test
	void managerList_asOwner_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/manager/news")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isForbidden());
	}
}
