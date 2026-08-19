package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreatePlayerProfileRequest;
import com.capstone.su26_sep490_g2_be.dto.request.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — UC-01 bước 2 (tạo profile Player sau khi đăng ký). */
class PlayerControllerL2Test extends AbstractControllerIntegrationTest {

	/** Đăng ký 1 tài khoản Player mới qua chính API thật — chưa có UserProfile, dùng cho happy path createProfile. */
	private String registerFreshPlayerAndGetToken() throws Exception {
		RegisterRequest req = new RegisterRequest();
		String email = "fresh-player-" + System.nanoTime() + "@gmail.com";
		req.setEmail(email);
		req.setPassword("Passw0rd");

		String body = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + objectMapper.readTree(body).path("data").path("token").asText();
	}

	@Test
	void createProfile_freshPlayer_created201() throws Exception {
		String token = registerFreshPlayerAndGetToken();

		CreatePlayerProfileRequest req = new CreatePlayerProfileRequest();
		req.setFullName("Người chơi mới L2");
		req.setBilliardRank("B");

		mockMvc.perform(post("/api/v1/player/profile")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.fullName").value("Người chơi mới L2"));
	}

	@Test
	void createProfile_alreadyExists_rejected409() throws Exception {
		// player1 đã có UserProfile do DataInitializer seed sẵn.
		CreatePlayerProfileRequest req = new CreatePlayerProfileRequest();
		req.setFullName("Trùng profile");

		mockMvc.perform(post("/api/v1/player/profile")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isConflict());
	}

	@Test
	void createProfile_blankFullName_rejected400() throws Exception {
		String token = registerFreshPlayerAndGetToken();
		CreatePlayerProfileRequest req = new CreatePlayerProfileRequest();
		req.setFullName("");

		mockMvc.perform(post("/api/v1/player/profile")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createProfile_asOwner_rejected403() throws Exception {
		// GB-04: /player/** chỉ role PLAYER — cả owner tạo hộ cũng không được vì profile gắn theo JWT.
		CreatePlayerProfileRequest req = new CreatePlayerProfileRequest();
		req.setFullName("x");

		mockMvc.perform(post("/api/v1/player/profile")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isForbidden());
	}

	@Test
	void getMyProfile_seededPlayer_ok() throws Exception {
		mockMvc.perform(get("/api/v1/player/profile")
						.header("Authorization", bearerToken(TestAccounts.PLAYER2_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void getMyProfile_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/player/profile"))
				.andExpect(status().isUnauthorized());
	}
}
