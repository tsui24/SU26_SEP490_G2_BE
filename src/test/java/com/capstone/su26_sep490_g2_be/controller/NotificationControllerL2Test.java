package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.RegisterDeviceTokenRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — Notification/device-token. Cố ý KHÔNG nằm dưới {@code /api/v1/{role}/**} (xem comment
 * trong NotificationController) nên mọi role đã đăng nhập đều gọi được qua rule
 * {@code anyRequest().authenticated()} — test bằng PLAYER, giữ role-guard riêng cho các controller
 * thật sự khoá theo role.
 */
class NotificationControllerL2Test extends AbstractControllerIntegrationTest {

	// ── GET /notifications ────────────────────────────────────────────────

	@Test
	void listMine_withToken_ok() throws Exception {
		mockMvc.perform(get("/api/v1/notifications")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void listMine_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/notifications"))
				.andExpect(status().isUnauthorized());
	}

	// ── GET /notifications/unread-count ───────────────────────────────────

	@Test
	void countUnread_withToken_returnsNonNegativeNumber() throws Exception {
		mockMvc.perform(get("/api/v1/notifications/unread-count")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isNumber());
	}

	@Test
	void countUnread_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/notifications/unread-count"))
				.andExpect(status().isUnauthorized());
	}

	// ── POST /notifications/device-tokens ─────────────────────────────────

	@Test
	void registerDevice_validPayload_ok() throws Exception {
		RegisterDeviceTokenRequest req = RegisterDeviceTokenRequest.builder()
				.expoToken("ExponentPushToken[l2-test-" + System.nanoTime() + "]")
				.platform("android")
				.build();

		mockMvc.perform(post("/api/v1/notifications/device-tokens")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void registerDevice_calledTwice_idempotent() throws Exception {
		RegisterDeviceTokenRequest req = RegisterDeviceTokenRequest.builder()
				.expoToken("ExponentPushToken[l2-idempotent-" + System.nanoTime() + "]")
				.platform("ios")
				.build();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/v1/notifications/device-tokens")
							.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
							.contentType(MediaType.APPLICATION_JSON)
							.content(toJson(req)))
					.andExpect(status().isOk());
		}
	}

	@Test
	void registerDevice_blankExpoToken_rejected400() throws Exception {
		RegisterDeviceTokenRequest req = RegisterDeviceTokenRequest.builder()
				.expoToken("")
				.platform("android")
				.build();

		mockMvc.perform(post("/api/v1/notifications/device-tokens")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void registerDevice_withoutToken_rejected401() throws Exception {
		RegisterDeviceTokenRequest req = RegisterDeviceTokenRequest.builder()
				.expoToken("ExponentPushToken[no-auth]")
				.platform("android")
				.build();

		mockMvc.perform(post("/api/v1/notifications/device-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}

	// ── DELETE /notifications/device-tokens ───────────────────────────────

	@Test
	void unregisterDevice_afterRegister_ok() throws Exception {
		String expoToken = "ExponentPushToken[l2-unregister-" + System.nanoTime() + "]";
		RegisterDeviceTokenRequest req = RegisterDeviceTokenRequest.builder()
				.expoToken(expoToken)
				.platform("android")
				.build();
		mockMvc.perform(post("/api/v1/notifications/device-tokens")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/notifications/device-tokens")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.param("expoToken", expoToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void unregisterDevice_missingExpoTokenParam_rejected400() throws Exception {
		mockMvc.perform(delete("/api/v1/notifications/device-tokens")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isBadRequest());
	}
}
