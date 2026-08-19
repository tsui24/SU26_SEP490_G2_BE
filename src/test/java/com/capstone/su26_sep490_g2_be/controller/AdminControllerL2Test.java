package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — UC-07 (Manage User Accounts). GB-04: {@code /api/v1/admin/**} chỉ role ADMIN. GB-03: role
 * gán 1 lần lúc tạo, không có API đổi role sau đó (không có endpoint nào cho phép sửa field role).
 */
class AdminControllerL2Test extends AbstractControllerIntegrationTest {

	// ── POST /admin/accounts/owner ────────────────────────────────────────

	@Test
	void createOwner_asAdmin_created201() throws Exception {
		CreateAccountRequest req = new CreateAccountRequest();
		req.setEmail("new-owner-" + System.nanoTime() + "@gmail.com");
		req.setPhone("0912345678");
		req.setPassword("Passw0rd");

		mockMvc.perform(post("/api/v1/admin/accounts/owner")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.role").value("OWNER"));
	}

	@Test
	void createOwner_asNonAdmin_rejected403() throws Exception {
		// GB-04: role-based URL authorization — OWNER không được gọi /admin/**.
		CreateAccountRequest req = new CreateAccountRequest();
		req.setEmail("blocked-owner-" + System.nanoTime() + "@gmail.com");
		req.setPassword("Passw0rd");

		mockMvc.perform(post("/api/v1/admin/accounts/owner")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_006"));
	}

	@Test
	void createOwner_duplicateEmail_rejected409() throws Exception {
		CreateAccountRequest req = new CreateAccountRequest();
		req.setEmail(TestAccounts.OWNER_EMAIL); // seed sẵn
		req.setPassword("Passw0rd");

		mockMvc.perform(post("/api/v1/admin/accounts/owner")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isConflict());
	}

	@Test
	void createOwner_missingEmail_rejected400() throws Exception {
		CreateAccountRequest req = new CreateAccountRequest();
		req.setPassword("Passw0rd");

		mockMvc.perform(post("/api/v1/admin/accounts/owner")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	// ── POST /admin/accounts/admin ────────────────────────────────────────

	@Test
	void createAdmin_asAdmin_created201() throws Exception {
		CreateAccountRequest req = new CreateAccountRequest();
		req.setEmail("new-admin-" + System.nanoTime() + "@gmail.com");
		req.setPassword("Passw0rd");

		mockMvc.perform(post("/api/v1/admin/accounts/admin")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.role").value("ADMIN"));
	}

	@Test
	void createAdmin_withoutToken_rejected401() throws Exception {
		CreateAccountRequest req = new CreateAccountRequest();
		req.setEmail("no-auth-admin@gmail.com");
		req.setPassword("Passw0rd");

		mockMvc.perform(post("/api/v1/admin/accounts/admin")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}

	// ── GET /admin/accounts ──────────────────────────────────────────────

	@Test
	void getUsers_asAdmin_filterByRole_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/accounts")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.param("role", "STAFF")
						.param("page", "0")
						.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void getUsers_asStaff_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/accounts")
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	// ── PUT /admin/accounts/{id}/deactivate + reactivate ──────────────────

	@Test
	void deactivateThenReactivate_asAdmin_flipsStatus() throws Exception {
		Long staffId = userIdOf(TestAccounts.STAFF3_EMAIL);

		mockMvc.perform(put("/api/v1/admin/accounts/{id}/deactivate", staffId)
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk());
		User locked = userRepository.findById(staffId).orElseThrow();
		assertEquals(UserStatus.LOCKED, locked.getStatus());

		mockMvc.perform(put("/api/v1/admin/accounts/{id}/reactivate", staffId)
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk());
		User active = userRepository.findById(staffId).orElseThrow();
		assertEquals(UserStatus.ACTIVE, active.getStatus());
	}

	@Test
	void deactivate_nonExistentUser_rejected404() throws Exception {
		mockMvc.perform(put("/api/v1/admin/accounts/{id}/deactivate", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void deactivate_asManager_rejected403() throws Exception {
		mockMvc.perform(put("/api/v1/admin/accounts/{id}/deactivate", userIdOf(TestAccounts.STAFF4_EMAIL))
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	// ── POST /admin/leaderboard/recalculate-points ────────────────────────

	@Test
	void recalculateLeaderboardPoints_asAdmin_ok() throws Exception {
		mockMvc.perform(post("/api/v1/admin/leaderboard/recalculate-points")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isNumber());
	}

	@Test
	void recalculateLeaderboardPoints_asPlayer_rejected403() throws Exception {
		mockMvc.perform(post("/api/v1/admin/leaderboard/recalculate-points")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}
}
