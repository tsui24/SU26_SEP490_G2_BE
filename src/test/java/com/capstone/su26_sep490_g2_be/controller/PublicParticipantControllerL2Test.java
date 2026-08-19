package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — /api/v1/participants/** công khai (hồ sơ cơ thủ + lịch sử thành tích), không cần đăng nhập. */
class PublicParticipantControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void getProfile_unknownParticipantId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/participants/{id}/profile", 999_999_999L))
				.andExpect(status().isNotFound());
	}

	@Test
	void getProfileByUserId_seededPlayer_ok() throws Exception {
		mockMvc.perform(get("/api/v1/participants/user/{userId}/profile", userIdOf(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isOk());
	}

	@Test
	void getProfileByUserId_unknownUserId_stillReturns200EmptyAchievements() throws Exception {
		// TournamentResultServiceImpl#getPlayerProfileByUserId không throw khi không có profile/participant
		// nào khớp — trả về response gần như rỗng (achievements=[]) thay vì 404. Ghi lại đúng hành vi thật.
		mockMvc.perform(get("/api/v1/participants/user/{userId}/profile", 999_999_999L))
				.andExpect(status().isOk());
	}
}
