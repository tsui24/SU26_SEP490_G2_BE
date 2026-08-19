package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.ManualAddParticipantRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateSeedNoRequest;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — UC-24 (Owner/Manager thêm participant thủ công) + UC-26 (rút lui) + GB-16 (roster editable). */
class ParticipantControllerL2Test extends AbstractControllerIntegrationTest {

	@Autowired private TournamentRepository tournamentRepository;
	@Autowired private BranchRepository branchRepository;

	private Tournament setupOpenTournament() {
		var owner = seedUser(TestAccounts.OWNER_EMAIL);
		var branch1 = branchRepository.findByOwnerId(owner.getId()).stream()
				.filter(b -> b.getName().contains("Thủ Đức")).findFirst().orElseThrow();
		Instant now = Instant.now();
		return tournamentRepository.save(Tournament.builder()
				.name("L2 Participant Test " + System.nanoTime())
				.gameType("9_BALL")
				.format("SINGLE_ELIMINATION")
				.participantType(ParticipantType.SINGLE.getValue())
				.status(TournamentStatus.REGISTRATION_CLOSED.getValue())
				.maxParticipants(8)
				.tableCount(1)
				.entryFee(BigDecimal.ZERO)
				.prizePool(BigDecimal.ZERO)
				.registrationDeadline(now.minus(1, ChronoUnit.DAYS))
				.startAt(now)
				.endAt(now.plus(1, ChronoUnit.DAYS))
				.isShowTournament(false)
				.isPublicRatio(true)
				.isRegister(false)
				.createdBy(owner)
				.branch(branch1)
				.build());
	}

	@Test
	void listParticipants_public_emptyTournament_ok() throws Exception {
		Tournament t = setupOpenTournament();
		mockMvc.perform(get("/api/v1/tournaments/{id}/participants", t.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void addManual_asOwner_created201() throws Exception {
		Tournament t = setupOpenTournament();
		ManualAddParticipantRequest req = new ManualAddParticipantRequest();
		req.setDisplayName("L2 Manual Participant");
		req.setSeedNo(1);

		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/participants/manual", t.getId())
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.displayName").value("L2 Manual Participant"))
				.andExpect(jsonPath("$.data.seedNo").value(1));
	}

	@Test
	void addManual_asPlayer_rejected403() throws Exception {
		Tournament t = setupOpenTournament();
		ManualAddParticipantRequest req = new ManualAddParticipantRequest();
		req.setDisplayName("Blocked");

		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/participants/manual", t.getId())
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isForbidden());
	}

	@Test
	void addManual_blankDisplayName_rejected400() throws Exception {
		Tournament t = setupOpenTournament();
		ManualAddParticipantRequest req = new ManualAddParticipantRequest();
		req.setDisplayName("");

		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/participants/manual", t.getId())
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void addManual_afterRosterLocked_rejected() throws Exception {
		// GB-16: roster chỉ sửa được ở DRAFT/OPEN_FOR_REGISTRATION/REGISTRATION_CLOSED — DRAW_PREVIEW khoá lại.
		Tournament t = setupOpenTournament();
		t.setStatus(TournamentStatus.DRAW_PREVIEW.getValue());
		tournamentRepository.save(t);

		ManualAddParticipantRequest req = new ManualAddParticipantRequest();
		req.setDisplayName("Quá muộn");

		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/participants/manual", t.getId())
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void addManual_duplicateSeedNo_rejected409() throws Exception {
		Tournament t = setupOpenTournament();
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);

		ManualAddParticipantRequest req1 = new ManualAddParticipantRequest();
		req1.setDisplayName("Người 1");
		req1.setSeedNo(1);
		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/participants/manual", t.getId())
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req1)))
				.andExpect(status().isCreated());

		ManualAddParticipantRequest req2 = new ManualAddParticipantRequest();
		req2.setDisplayName("Người 2 - trùng seed");
		req2.setSeedNo(1);
		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/participants/manual", t.getId())
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req2)))
				.andExpect(status().isConflict());
	}

	@Test
	void updateSeedNoThenWithdraw_asManager_ok() throws Exception {
		Tournament t = setupOpenTournament();
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);

		ManualAddParticipantRequest addReq = new ManualAddParticipantRequest();
		addReq.setDisplayName("L2 Withdraw Target");
		String addBody = mockMvc.perform(post("/api/v1/owner/tournaments/{id}/participants/manual", t.getId())
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(addReq)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long participantId = objectMapper.readTree(addBody).path("data").path("id").asLong();

		UpdateSeedNoRequest seedReq = new UpdateSeedNoRequest();
		seedReq.setSeedNo(3);
		mockMvc.perform(patch("/api/v1/owner/participants/{id}/seed-no", participantId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(seedReq)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.seedNo").value(3));

		mockMvc.perform(patch("/api/v1/owner/participants/{id}/withdraw", participantId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
	}

	@Test
	void downloadImportTemplate_asManager_ok() throws Exception {
		Tournament t = setupOpenTournament();
		mockMvc.perform(get("/api/v1/manager/tournaments/{id}/participants/import-template", t.getId())
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk());
	}
}
