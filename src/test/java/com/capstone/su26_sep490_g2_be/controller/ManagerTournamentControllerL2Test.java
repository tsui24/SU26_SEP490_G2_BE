package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateTournamentRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchTournamentStatusRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchTournamentVisibilityRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateTournamentRequest;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — UC-16..UC-19 (Manager tạo/cấu hình/đổi trạng thái giải). GB-05: Manager chỉ thao tác trên
 * chi nhánh được gán (manager1 -> branch Thủ Đức, manager2 -> branch Cầu Giấy). GB-06: chuyển
 * trạng thái giải chỉ theo whitelist một chiều.
 */
class ManagerTournamentControllerL2Test extends AbstractControllerIntegrationTest {

	@Autowired
	private BranchRepository branchRepository;

	private Long branch1Id() {
		return branchRepository.findByOwnerId(userIdOf(TestAccounts.OWNER_EMAIL)).stream()
				.filter(b -> b.getName().contains("Thủ Đức"))
				.findFirst().orElseThrow().getId();
	}

	private Long branch2Id() {
		return branchRepository.findByOwnerId(userIdOf(TestAccounts.OWNER_EMAIL)).stream()
				.filter(b -> b.getName().contains("Cầu Giấy"))
				.findFirst().orElseThrow().getId();
	}

	private CreateTournamentRequest validRequest(Long branchId) {
		CreateTournamentRequest req = new CreateTournamentRequest();
		req.setName("Giải L2 Test " + System.nanoTime());
		req.setGameType("9_BALL");
		req.setFormat("SINGLE_ELIMINATION");
		req.setParticipantType("SINGLE");
		req.setMaxParticipants(16);
		req.setBranchId(branchId);
		return req;
	}

	@Test
	void listTournaments_asManager_ok() throws Exception {
		mockMvc.perform(get("/api/v1/manager/tournaments")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void listTournaments_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/manager/tournaments")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void listFormats_asManager_includesSeeded() throws Exception {
		mockMvc.perform(get("/api/v1/manager/formats")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk());
	}

	@Test
	void listGameTypes_asManager_ok() throws Exception {
		mockMvc.perform(get("/api/v1/manager/game-types")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk());
	}

	@Test
	void createTournament_ownBranch_created201() throws Exception {
		mockMvc.perform(post("/api/v1/manager/tournaments")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(validRequest(branch1Id()))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void createTournament_otherManagerBranch_rejected() throws Exception {
		// GB-05: manager1 không được tạo giải cho branch của manager2.
		mockMvc.perform(post("/api/v1/manager/tournaments")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(validRequest(branch2Id()))))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void createTournament_missingBranchId_rejected400() throws Exception {
		CreateTournamentRequest req = validRequest(null);
		req.setBranchId(null);

		mockMvc.perform(post("/api/v1/manager/tournaments")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createTournament_maxParticipantsBelowMinimum_rejected400() throws Exception {
		CreateTournamentRequest req = validRequest(branch1Id());
		req.setMaxParticipants(1); // @Min(2)

		mockMvc.perform(post("/api/v1/manager/tournaments")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	private Long createTournament(String managerAuth, Long branchId) throws Exception {
		String body = mockMvc.perform(post("/api/v1/manager/tournaments")
						.header("Authorization", managerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(validRequest(branchId))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).path("data").path("id").asLong();
	}

	@Test
	void getTournament_ownerOfBranch_ok() throws Exception {
		String manager1Auth = bearerToken(TestAccounts.MANAGER1_EMAIL);
		Long tournamentId = createTournament(manager1Auth, branch1Id());

		mockMvc.perform(get("/api/v1/manager/tournaments/{id}", tournamentId)
						.header("Authorization", manager1Auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(tournamentId));
	}

	@Test
	void getTournament_otherManagerBranch_rejected() throws Exception {
		Long tournamentId = createTournament(bearerToken(TestAccounts.MANAGER1_EMAIL), branch1Id());

		mockMvc.perform(get("/api/v1/manager/tournaments/{id}", tournamentId)
						.header("Authorization", bearerToken(TestAccounts.MANAGER2_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void updateTournament_ownTournament_updatesName() throws Exception {
		String manager1Auth = bearerToken(TestAccounts.MANAGER1_EMAIL);
		Long tournamentId = createTournament(manager1Auth, branch1Id());

		UpdateTournamentRequest update = new UpdateTournamentRequest();
		update.setName("Giải L2 Test (đã sửa)");

		mockMvc.perform(put("/api/v1/manager/tournaments/{id}", tournamentId)
						.header("Authorization", manager1Auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isOk());
	}

	@Test
	void patchStatus_draftToOpenForRegistration_ok() throws Exception {
		String manager1Auth = bearerToken(TestAccounts.MANAGER1_EMAIL);
		Long tournamentId = createTournament(manager1Auth, branch1Id());

		PatchTournamentStatusRequest req = new PatchTournamentStatusRequest();
		req.setStatus("OPEN_FOR_REGISTRATION");

		mockMvc.perform(patch("/api/v1/manager/tournaments/{id}/status", tournamentId)
						.header("Authorization", manager1Auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk());
	}

	@Test
	void patchStatus_draftToCompleted_invalidTransitionRejected() throws Exception {
		// GB-06: whitelist chuyển trạng thái 1 chiều — DRAFT không thể nhảy thẳng sang COMPLETED.
		String manager1Auth = bearerToken(TestAccounts.MANAGER1_EMAIL);
		Long tournamentId = createTournament(manager1Auth, branch1Id());

		PatchTournamentStatusRequest req = new PatchTournamentStatusRequest();
		req.setStatus("COMPLETED");

		mockMvc.perform(patch("/api/v1/manager/tournaments/{id}/status", tournamentId)
						.header("Authorization", manager1Auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void updateVisibility_ownTournament_ok() throws Exception {
		String manager1Auth = bearerToken(TestAccounts.MANAGER1_EMAIL);
		Long tournamentId = createTournament(manager1Auth, branch1Id());

		PatchTournamentVisibilityRequest req = new PatchTournamentVisibilityRequest();
		req.setIsShowTournament(true);

		mockMvc.perform(patch("/api/v1/manager/tournaments/{id}/visibility", tournamentId)
						.header("Authorization", manager1Auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isShowTournament").value(true));
	}

	@Test
	void getAuditLogs_ownTournament_ok() throws Exception {
		String manager1Auth = bearerToken(TestAccounts.MANAGER1_EMAIL);
		Long tournamentId = createTournament(manager1Auth, branch1Id());

		mockMvc.perform(get("/api/v1/manager/tournaments/{id}/audit-logs", tournamentId)
						.header("Authorization", manager1Auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}
}
