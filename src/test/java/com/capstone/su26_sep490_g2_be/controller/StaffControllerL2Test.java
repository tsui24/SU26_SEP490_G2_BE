package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.AssignMatchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.ScoreIncrementRequest;
import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfig;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValue;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValueId;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.SeedingMethod;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.ConfigFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentConfigRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentConfigValueRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — UC-33 phía Staff/trọng tài (GB-12: chỉ Staff được gán mới thao tác trận của mình). */
class StaffControllerL2Test extends AbstractControllerIntegrationTest {

	@Autowired private TournamentRepository tournamentRepository;
	@Autowired private TournamentConfigRepository tournamentConfigRepository;
	@Autowired private TournamentConfigValueRepository configValueRepository;
	@Autowired private ConfigFieldDefinitionRepository configFieldRepository;
	@Autowired private ParticipantRepository participantRepository;
	@Autowired private BranchRepository branchRepository;

	private void addConfigValue(Tournament t, String key, String value) {
		ConfigFieldDefinition fieldDef = configFieldRepository.findById(key).orElseThrow();
		configValueRepository.save(TournamentConfigValue.builder()
				.id(new TournamentConfigValueId(t.getId(), key))
				.tournament(t).fieldDefinition(fieldDef).value(value).build());
	}

	/** Giải SINGLE_ELIMINATION 4 người, đã bốc thăm + confirm + IN_PROGRESS + gán staff1 vào R1-M1. */
	private Long setupMatchAssignedToStaff1() throws Exception {
		var owner = seedUser(TestAccounts.OWNER_EMAIL);
		var branch1 = branchRepository.findByOwnerId(owner.getId()).stream()
				.filter(b -> b.getName().contains("Thủ Đức")).findFirst().orElseThrow();
		Instant now = Instant.now();
		Tournament t = tournamentRepository.save(Tournament.builder()
				.name("L2 Staff Test " + System.nanoTime())
				.gameType("9_BALL").format("SINGLE_ELIMINATION")
				.participantType(ParticipantType.SINGLE.getValue())
				.status(TournamentStatus.REGISTRATION_CLOSED.getValue())
				.maxParticipants(4).tableCount(1)
				.entryFee(BigDecimal.ZERO).prizePool(BigDecimal.ZERO)
				.registrationDeadline(now.minus(1, ChronoUnit.DAYS))
				.startAt(now).endAt(now.plus(1, ChronoUnit.DAYS))
				.isShowTournament(false).isPublicRatio(false).isRegister(false)
				.createdBy(owner).branch(branch1)
				.build());

		tournamentConfigRepository.save(TournamentConfig.builder()
				.tournament(t).formatCode(t.getFormat()).seedingMethod(SeedingMethod.RANDOM.name()).build());
		addConfigValue(t, "bracket_size", "4");
		addConfigValue(t, "third_place_match", "false");
		addConfigValue(t, "break_rule", "ALTERNATE_BREAK");
		addConfigValue(t, "lag_for_break", "true");
		addConfigValue(t, "scoring_unit", "GAME");

		for (String name : List.of("S1", "S2", "S3", "S4")) {
			participantRepository.save(Participant.builder()
					.tournament(t).registration(null)
					.participantType(ParticipantType.SINGLE.getValue())
					.displayName(name).status(ParticipantStatus.ACTIVE.getValue())
					.build());
		}

		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/draw", t.getId())
				.header("Authorization", ownerAuth)).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/draw/confirm", t.getId())
				.header("Authorization", ownerAuth)).andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/owner/tournaments/{id}/status", t.getId())
				.header("Authorization", ownerAuth)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"IN_PROGRESS\"}")).andExpect(status().isOk());

		String stagesBody = mockMvc.perform(get("/api/v1/owner/tournaments/{id}/stages", t.getId())
						.header("Authorization", ownerAuth))
				.andReturn().getResponse().getContentAsString();
		JsonNode matches = objectMapper.readTree(stagesBody).path("data").get(0).path("matches");
		long matchId = -1;
		for (JsonNode m : matches) {
			if (m.path("roundNo").asInt() == 1 && m.path("positionNo").asInt() == 1) {
				matchId = m.path("id").asLong();
				break;
			}
		}
		if (matchId == -1) throw new IllegalStateException("Không tìm thấy R1-M1");

		AssignMatchRequest assignReq = new AssignMatchRequest();
		assignReq.setAssignedStaffId(userIdOf(TestAccounts.STAFF1_EMAIL));
		mockMvc.perform(patch("/api/v1/owner/matches/{id}/assignment", matchId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(assignReq)))
				.andExpect(status().isOk());

		return matchId;
	}

	@Test
	void getMyMatches_assignedStaff_includesMatch() throws Exception {
		Long matchId = setupMatchAssignedToStaff1();

		mockMvc.perform(get("/api/v1/staff/matches")
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == " + matchId + ")]").exists());
	}

	@Test
	void getMyMatches_unassignedStaff_doesNotIncludeMatch() throws Exception {
		Long matchId = setupMatchAssignedToStaff1();

		mockMvc.perform(get("/api/v1/staff/matches")
						.header("Authorization", bearerToken(TestAccounts.STAFF2_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == " + matchId + ")]").doesNotExist());
	}

	@Test
	void getMyMatches_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/staff/matches"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getMyMatches_asOwner_rejected403() throws Exception {
		// GB-04: /api/v1/staff/** chỉ role STAFF.
		mockMvc.perform(get("/api/v1/staff/matches")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void incrementScore_assignedStaff_ok() throws Exception {
		Long matchId = setupMatchAssignedToStaff1();
		String staffAuth = bearerToken(TestAccounts.STAFF1_EMAIL);

		mockMvc.perform(patch("/api/v1/staff/matches/{id}/start", matchId)
						.header("Authorization", staffAuth))
				.andExpect(status().isOk());

		ScoreIncrementRequest req = new ScoreIncrementRequest();
		req.setPlayerSlot(1);
		req.setDelta(1);

		mockMvc.perform(patch("/api/v1/staff/matches/{id}/score/increment", matchId)
						.header("Authorization", staffAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.match.player1Score").value(1));
	}

	@Test
	void incrementScore_unassignedStaff_rejected() throws Exception {
		// GB-12: staff2 không được gán trận này -> chặn dù đúng role STAFF.
		Long matchId = setupMatchAssignedToStaff1();

		ScoreIncrementRequest req = new ScoreIncrementRequest();
		req.setPlayerSlot(1);
		req.setDelta(1);

		mockMvc.perform(patch("/api/v1/staff/matches/{id}/score/increment", matchId)
						.header("Authorization", bearerToken(TestAccounts.STAFF2_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void incrementScore_deltaOutOfRange_rejected400() throws Exception {
		Long matchId = setupMatchAssignedToStaff1();
		ScoreIncrementRequest req = new ScoreIncrementRequest();
		req.setPlayerSlot(1);
		req.setDelta(2); // chỉ nhận -1, 0, 1

		mockMvc.perform(patch("/api/v1/staff/matches/{id}/score/increment", matchId)
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}
}
