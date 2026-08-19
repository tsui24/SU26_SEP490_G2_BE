package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.entity.FacebookPost;
import com.capstone.su26_sep490_g2_be.repository.FacebookPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — /api/v1/shared/facebook/**, dùng chung Owner+Manager (GB-04: hasAnyRole OWNER,MANAGER).
 *
 * <p>Các endpoint publish/token thật sự gọi Facebook Graph API (FacebookPublishService,
 * FacebookTokenManager) — L2 chỉ kiểm tra nhánh chặn TRƯỚC khi gọi ra ngoài (validation, role-guard)
 * để không phụ thuộc FB_PAGE_ACCESS_TOKEN thật khi chạy trên máy dev/CI. Endpoint đọc dữ liệu cục bộ
 * (listPosts, listPostsByTournament, getPost) test bình thường; getEngagement/getInsights chỉ test
 * với record đã có statsSyncedAt sẵn (nhánh cache, không gọi ra ngoài).
 */
class FacebookControllerL2Test extends AbstractControllerIntegrationTest {

	@Autowired
	private FacebookPostRepository facebookPostRepository;

	// ── Validation trước khi gọi Facebook ─────────────────────────────────

	@Test
	void publishTextPost_blankMessage_rejected400() throws Exception {
		String body = "{\"message\": \"\", \"link\": null, \"tournamentId\": null}";

		mockMvc.perform(post("/api/v1/shared/facebook/post/text")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void publishTextPost_asPlayer_rejected403() throws Exception {
		String body = "{\"message\": \"test\"}";

		mockMvc.perform(post("/api/v1/shared/facebook/post/text")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden());
	}

	@Test
	void publishTextPost_withoutToken_rejected401() throws Exception {
		String body = "{\"message\": \"test\"}";

		mockMvc.perform(post("/api/v1/shared/facebook/post/text")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void publishPhotoPost_blankImageUrl_rejected400() throws Exception {
		String body = "{\"message\": \"has message\", \"imageUrl\": \"\"}";

		mockMvc.perform(post("/api/v1/shared/facebook/post/photo")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void exchangeToken_blankToken_rejected400() throws Exception {
		String body = "{\"shortLivedToken\": \"\"}";

		mockMvc.perform(post("/api/v1/shared/facebook/token/exchange")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}

	// ── Đọc dữ liệu cục bộ (không gọi Facebook) ───────────────────────────

	@Test
	void listPosts_asOwner_ok() throws Exception {
		mockMvc.perform(get("/api/v1/shared/facebook/posts")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void listPosts_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/shared/facebook/posts")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void getPost_existingRecord_ok() throws Exception {
		FacebookPost saved = facebookPostRepository.save(FacebookPost.builder()
				.facebookPostId("fb-l2-test-" + System.nanoTime())
				.content("Nội dung test L2")
				.postType("TEXT")
				.postedAt(Instant.now())
				.build());

		mockMvc.perform(get("/api/v1/shared/facebook/posts/{id}", saved.getId())
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").value("Nội dung test L2"));
	}

	@Test
	void getPost_unknownId_returns500NotFoundBusinessLogic() throws Exception {
		// Controller ném RuntimeException thô (không phải BusinessException) khi không thấy record —
		// rơi vào handler chung -> 500, không phải 404. Ghi lại đúng hành vi hiện tại.
		mockMvc.perform(get("/api/v1/shared/facebook/posts/{id}", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().is5xxServerError());
	}

	@Test
	void getEngagement_cachedRecord_doesNotCallFacebook() throws Exception {
		FacebookPost saved = facebookPostRepository.save(FacebookPost.builder()
				.facebookPostId("fb-l2-cached-" + System.nanoTime())
				.content("Đã cache thống kê")
				.postType("TEXT")
				.postedAt(Instant.now())
				.statsSyncedAt(Instant.now())
				.likesCount(10)
				.commentsCount(2)
				.sharesCount(1)
				.build());

		mockMvc.perform(get("/api/v1/shared/facebook/posts/{id}/engagement", saved.getId())
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likes").value(10))
				.andExpect(jsonPath("$.data.fromCache").value(true));
	}
}
