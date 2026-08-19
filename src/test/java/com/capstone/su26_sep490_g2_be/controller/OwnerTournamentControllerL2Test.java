package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateTournamentRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchTournamentStatusRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchTournamentVisibilityRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SaveTournamentConfigRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateTournamentRequest;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — UC-16..UC-19 (Owner tạo/cấu hình/đổi trạng thái giải), theo đúng thứ tự wizard mô tả trong
 * BTMS-Tournament-Config-API.md: create -> config-form -> save config (override race-to) ->
 * resolved config -> validate -> mở đăng ký.
 */
class OwnerTournamentControllerL2Test extends AbstractControllerIntegrationTest {

	@Autowired
	private BranchRepository branchRepository;

	private Long branch1Id() {
		return branchRepository.findByOwnerId(userIdOf(TestAccounts.OWNER_EMAIL)).stream()
				.filter(b -> b.getName().contains("Thủ Đức"))
				.findFirst().orElseThrow(() -> new IllegalStateException("branch1 not found")).getId();
	}

	private CreateTournamentRequest validRequest() {
		CreateTournamentRequest req = new CreateTournamentRequest();
		req.setName("Giải Owner L2 Test " + System.nanoTime());
		req.setGameType("9_BALL");
		req.setFormat("SINGLE_ELIMINATION");
		req.setParticipantType("SINGLE");
		req.setMaxParticipants(16);
		req.setBranchId(branch1Id());
		return req;
	}

	private Long createTournament(String ownerAuth) throws Exception {
		String body = mockMvc.perform(post("/api/v1/owner/tournaments")
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(validRequest())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).path("data").path("id").asLong();
	}

	@Test
	void listTournaments_asOwner_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/tournaments")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void listTournaments_asStaff_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/owner/tournaments")
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void createTournament_missingGameType_rejected400() throws Exception {
		CreateTournamentRequest req = validRequest();
		req.setGameType(null);

		mockMvc.perform(post("/api/v1/owner/tournaments")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getTournament_ownTournament_ok() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long id = createTournament(ownerAuth);

		mockMvc.perform(get("/api/v1/owner/tournaments/{id}", id)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(id));
	}

	@Test
	void updateTournament_ownTournament_ok() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long id = createTournament(ownerAuth);

		UpdateTournamentRequest req = new UpdateTournamentRequest();
		req.setName("Giải Owner L2 Test (updated)");

		mockMvc.perform(put("/api/v1/owner/tournaments/{id}", id)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk());
	}

	@Test
	void configWizard_getForm_saveOverride_resolve_validate() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long id = createTournament(ownerAuth);

		mockMvc.perform(get("/api/v1/owner/tournaments/{id}/config-form", id)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.fields").isArray());

		SaveTournamentConfigRequest.ConfigFieldValueItem bracketSize =
				new SaveTournamentConfigRequest.ConfigFieldValueItem();
		bracketSize.setFieldKey("bracket_size");
		bracketSize.setValue("16");
		SaveTournamentConfigRequest.ConfigFieldValueItem thirdPlace =
				new SaveTournamentConfigRequest.ConfigFieldValueItem();
		thirdPlace.setFieldKey("third_place_match");
		thirdPlace.setValue("true");
		SaveTournamentConfigRequest.ConfigFieldValueItem breakRule =
				new SaveTournamentConfigRequest.ConfigFieldValueItem();
		breakRule.setFieldKey("break_rule");
		breakRule.setValue("ALTERNATE_BREAK");
		SaveTournamentConfigRequest.ConfigFieldValueItem lagForBreak =
				new SaveTournamentConfigRequest.ConfigFieldValueItem();
		lagForBreak.setFieldKey("lag_for_break");
		lagForBreak.setValue("true");

		SaveTournamentConfigRequest.RaceToOverrideItem finalOverride =
				new SaveTournamentConfigRequest.RaceToOverrideItem();
		finalOverride.setRoundKey("final");
		finalOverride.setRaceTo(11); // override so với default 9 (DC-02: race-to tự do theo vị trí)

		SaveTournamentConfigRequest saveReq = new SaveTournamentConfigRequest();
		saveReq.setSeedingMethod("RANDOM");
		saveReq.setFields(List.of(bracketSize, thirdPlace, breakRule, lagForBreak));
		saveReq.setRaceToOverrides(List.of(finalOverride));

		mockMvc.perform(put("/api/v1/owner/tournaments/{id}/config", id)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(saveReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isConfigComplete").value(true));

		mockMvc.perform(get("/api/v1/owner/tournaments/{id}/config", id)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.raceToRules.final").value(11));

		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/config/validate", id)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isValid").value(true));
	}

	@Test
	void saveConfig_emptyFields_rejected400() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long id = createTournament(ownerAuth);

		SaveTournamentConfigRequest req = new SaveTournamentConfigRequest();
		req.setSeedingMethod("RANDOM");
		req.setFields(List.of());

		mockMvc.perform(put("/api/v1/owner/tournaments/{id}/config", id)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void patchStatus_draftToOpenForRegistration_requiresCompleteConfigFirst() throws Exception {
		// SINGLE_ELIMINATION có đủ default Admin cho mọi field bắt buộc (bracket_size, third_place_match,
		// break_rule, lag_for_break) + seedingMethod=RANDOM tự gán lúc createTournament -> giải mới tạo
		// đã "complete" ngay, không dùng được để tái hiện case thiếu config (đây là đúng thiết kế, không
		// phải bug — collectConfigErrors cố ý cho phép mở đăng ký bằng default mà không cần Owner lưu tay).
		// DOUBLE_ELIMINATION thì khác: se_phase_size bắt buộc nhưng KHÔNG có defaultValue
		// (DataInitializer#ensureFormatConfigFieldsForDE — Owner phải tự nhập, không có số nào đúng cho
		// mọi giải) nên giải DOUBLE_ELIMINATION mới tạo, chưa lưu config, chắc chắn thiếu field này.
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		CreateTournamentRequest deRequest = validRequest();
		deRequest.setFormat("DOUBLE_ELIMINATION");
		String createBody = mockMvc.perform(post("/api/v1/owner/tournaments")
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(deRequest)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long id = objectMapper.readTree(createBody).path("data").path("id").asLong();

		PatchTournamentStatusRequest req = new PatchTournamentStatusRequest();
		req.setStatus("OPEN_FOR_REGISTRATION");

		mockMvc.perform(patch("/api/v1/owner/tournaments/{id}/status", id)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void updateVisibility_asOwner_ok() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long id = createTournament(ownerAuth);

		PatchTournamentVisibilityRequest req = new PatchTournamentVisibilityRequest();
		req.setIsShowTournament(true);

		mockMvc.perform(patch("/api/v1/owner/tournaments/{id}/visibility", id)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk());
	}

	@Test
	void getAuditLogs_asOwner_ok() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long id = createTournament(ownerAuth);

		mockMvc.perform(get("/api/v1/owner/tournaments/{id}/audit-logs", id)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void getTournament_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/owner/tournaments/{id}", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isNotFound());
	}
}
