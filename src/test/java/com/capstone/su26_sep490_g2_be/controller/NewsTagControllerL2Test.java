package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.NewsTagRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — /api/v1/shared/news/tags/** dùng chung Owner+Manager (GB-04: hasAnyRole OWNER,MANAGER). */
class NewsTagControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void list_asOwner_ok() throws Exception {
		mockMvc.perform(get("/api/v1/shared/news/tags")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void list_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/shared/news/tags")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void fullLifecycle_createUpdateDelete() throws Exception {
		String managerAuth = bearerToken(TestAccounts.MANAGER1_EMAIL);
		NewsTagRequest create = new NewsTagRequest();
		create.setName("L2 Tag " + System.nanoTime());
		create.setSlug("l2-tag-" + System.nanoTime());

		String body = mockMvc.perform(post("/api/v1/shared/news/tags")
						.header("Authorization", managerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long tagId = objectMapper.readTree(body).path("data").path("id").asLong();

		NewsTagRequest update = new NewsTagRequest();
		update.setName("L2 Tag (updated)");
		update.setSlug("l2-tag-updated-" + System.nanoTime());
		mockMvc.perform(put("/api/v1/shared/news/tags/{id}", tagId)
						.header("Authorization", managerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("L2 Tag (updated)"));

		mockMvc.perform(delete("/api/v1/shared/news/tags/{id}", tagId)
						.header("Authorization", managerAuth))
				.andExpect(status().isOk());
	}

	@Test
	void create_blankName_rejected400() throws Exception {
		NewsTagRequest req = new NewsTagRequest();
		req.setName("");
		req.setSlug("slug-x");

		mockMvc.perform(post("/api/v1/shared/news/tags")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void create_withoutToken_rejected401() throws Exception {
		NewsTagRequest req = new NewsTagRequest();
		req.setName("x");
		req.setSlug("x");

		mockMvc.perform(post("/api/v1/shared/news/tags")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}
}
