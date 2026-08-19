package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateGameTypeRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchFormatActiveRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateGameTypeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Admin quản lý catalog loại bi (DC-01: chỗ 9-Ball/8-Ball/10-Ball đăng ký làm GameType). */
class AdminGameTypeControllerL2Test extends AbstractControllerIntegrationTest {

	// ── GET /admin/game-types ────────────────────────────────────────────

	@Test
	void listGameTypes_asAdmin_includesSeeded9Ball() throws Exception {
		mockMvc.perform(get("/api/v1/admin/game-types")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.param("search", "9_BALL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].code").value("9_BALL"));
	}

	@Test
	void listGameTypes_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/game-types")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	// ── GET /admin/game-types/{code} ─────────────────────────────────────

	@Test
	void getGameType_existingCode_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/game-types/{code}", "8_BALL")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.code").value("8_BALL"));
	}

	@Test
	void getGameType_unknownCode_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/admin/game-types/{code}", "NOT_A_REAL_CODE")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isNotFound());
	}

	// ── POST /admin/game-types ───────────────────────────────────────────

	@Test
	void createGameType_validPayload_created201() throws Exception {
		CreateGameTypeRequest req = new CreateGameTypeRequest();
		String code = "TEST_L2_" + System.nanoTime();
		req.setCode(code);
		req.setName("L2 Test Game Type");
		req.setDescription("Tạo bởi integration test");
		req.setDefaultRaceTo(7);
		req.setCompatibleTableTypes(List.of("POOL"));
		req.setIsActive(true);

		mockMvc.perform(post("/api/v1/admin/game-types")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.code").value(code));
	}

	@Test
	void createGameType_duplicateCode_rejected409() throws Exception {
		CreateGameTypeRequest req = new CreateGameTypeRequest();
		req.setCode("9_BALL"); // đã seed sẵn
		req.setName("Trùng mã");
		req.setDefaultRaceTo(7);

		mockMvc.perform(post("/api/v1/admin/game-types")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isConflict());
	}

	@Test
	void createGameType_lowercaseCode_rejected400() throws Exception {
		// Business rule: code phải UPPER_SNAKE_CASE (regex trong CreateGameTypeRequest).
		CreateGameTypeRequest req = new CreateGameTypeRequest();
		req.setCode("not-upper-snake");
		req.setName("Sai định dạng mã");

		mockMvc.perform(post("/api/v1/admin/game-types")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createGameType_zeroDefaultRaceTo_rejected400() throws Exception {
		CreateGameTypeRequest req = new CreateGameTypeRequest();
		req.setCode("TEST_BAD_RACE_" + System.nanoTime());
		req.setName("Race-to = 0");
		req.setDefaultRaceTo(0); // @Min(1)

		mockMvc.perform(post("/api/v1/admin/game-types")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	// ── PUT /admin/game-types/{code} ─────────────────────────────────────

	@Test
	void updateGameType_existingCode_updatesName() throws Exception {
		CreateGameTypeRequest create = new CreateGameTypeRequest();
		String code = "TEST_UPDATE_" + System.nanoTime();
		create.setCode(code);
		create.setName("Trước khi sửa");
		create.setDefaultRaceTo(7);
		mockMvc.perform(post("/api/v1/admin/game-types")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isCreated());

		UpdateGameTypeRequest update = new UpdateGameTypeRequest();
		update.setName("Sau khi sửa");
		update.setDefaultRaceTo(9);
		update.setIsActive(true);

		mockMvc.perform(put("/api/v1/admin/game-types/{code}", code)
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Sau khi sửa"));
	}

	@Test
	void updateGameType_unknownCode_rejected404() throws Exception {
		UpdateGameTypeRequest update = new UpdateGameTypeRequest();
		update.setName("Không tồn tại");

		mockMvc.perform(put("/api/v1/admin/game-types/{code}", "NOT_A_REAL_CODE")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isNotFound());
	}

	// ── PATCH /admin/game-types/{code}/active ─────────────────────────────

	@Test
	void patchGameTypeActive_toggleOff_thenReflectsInList() throws Exception {
		CreateGameTypeRequest create = new CreateGameTypeRequest();
		String code = "TEST_TOGGLE_" + System.nanoTime();
		create.setCode(code);
		create.setName("Toggle test");
		create.setDefaultRaceTo(7);
		create.setIsActive(true);
		mockMvc.perform(post("/api/v1/admin/game-types")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(create)))
				.andExpect(status().isCreated());

		PatchFormatActiveRequest patchReq = new PatchFormatActiveRequest();
		patchReq.setIsActive(false);

		mockMvc.perform(patch("/api/v1/admin/game-types/{code}/active", code)
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(patchReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isActive").value(false));
	}

	@Test
	void patchGameTypeActive_missingIsActive_rejected400() throws Exception {
		PatchFormatActiveRequest patchReq = new PatchFormatActiveRequest();

		mockMvc.perform(patch("/api/v1/admin/game-types/{code}/active", "9_BALL")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(patchReq)))
				.andExpect(status().isBadRequest());
	}
}
