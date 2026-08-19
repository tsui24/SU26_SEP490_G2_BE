package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.UserProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — UC-05 (Manage personal profile), NFR-SEC "Personal profile protection" (gbrs_nfse.md §2):
 * mỗi user chỉ xem/sửa được đúng hồ sơ của chính mình (id lấy từ JWT, không nhận qua tham số).
 */
class ProfileControllerL2Test extends AbstractControllerIntegrationTest {

	// ── GET /profile ───────────────────────────────────────────────────────

	@Test
	void getProfile_ownerAccount_returnsOwnData() throws Exception {
		mockMvc.perform(get("/api/v1/profile")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(TestAccounts.OWNER_EMAIL))
				.andExpect(jsonPath("$.data.fullName").isNotEmpty());
	}

	@Test
	void getProfile_playerAccount_includesBilliardRank() throws Exception {
		// Field billiardRank chỉ có ý nghĩa với PLAYER — seed player1 có rank "A" (DataInitializer).
		mockMvc.perform(get("/api/v1/profile")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.billiardRank").value("A"));
	}

	@Test
	void getProfile_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/profile"))
				.andExpect(status().isUnauthorized());
	}

	// ── PUT /profile ───────────────────────────────────────────────────────

	@Test
	void editProfile_ownerAccount_updatesFullNameAndBio() throws Exception {
		UserProfileRequest req = new UserProfileRequest();
		req.setFullName("Nguyễn Thành Đạt (updated)");
		req.setDisplayName("Đạt Owner");
		req.setBio("Cập nhật bởi L2 test");

		mockMvc.perform(put("/api/v1/profile")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.fullName").value("Nguyễn Thành Đạt (updated)"))
				.andExpect(jsonPath("$.data.bio").value("Cập nhật bởi L2 test"));
	}

	@Test
	void editProfile_nonPlayerSendingBilliardRank_rejected() throws Exception {
		// Business rule (UserProfileServiceImpl): role khác PLAYER cố set billiardRank -> COMMON_INVALID_REQUEST.
		UserProfileRequest req = new UserProfileRequest();
		req.setFullName("Trần Quốc Bảo");
		req.setBilliardRank("A");

		mockMvc.perform(put("/api/v1/profile")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@Test
	void editProfile_playerWithInvalidBilliardRank_rejected() throws Exception {
		UserProfileRequest req = new UserProfileRequest();
		req.setFullName("Nguyễn Văn Hùng");
		req.setBilliardRank("Z9-not-a-real-rank");

		mockMvc.perform(put("/api/v1/profile")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@Test
	void editProfile_blankFullName_rejected400() throws Exception {
		UserProfileRequest req = new UserProfileRequest();
		req.setFullName("");

		mockMvc.perform(put("/api/v1/profile")
						.header("Authorization", bearerToken(TestAccounts.PLAYER2_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void editProfile_invalidPhoneFormat_rejected400() throws Exception {
		UserProfileRequest req = new UserProfileRequest();
		req.setFullName("Nguyễn Văn Hùng");
		req.setPhone("abc-not-a-phone");

		mockMvc.perform(put("/api/v1/profile")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void editProfile_withoutToken_rejected401() throws Exception {
		UserProfileRequest req = new UserProfileRequest();
		req.setFullName("Không đăng nhập");

		mockMvc.perform(put("/api/v1/profile")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}
}
