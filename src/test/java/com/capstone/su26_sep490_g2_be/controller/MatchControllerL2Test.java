package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CompleteMatchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateScoreRequest;
import com.capstone.su26_sep490_g2_be.entity.Branch;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — UC-19 (Draw) + UC-33 (Match Operation). Dựng thật 1 giải SINGLE_ELIMINATION 4 người (không
 * BYE) qua đúng luồng production (draw -> confirm -> IN_PROGRESS), giống cách
 * {@code ProgressiveRoundRobinOddPlayoffTest} đã làm cho service layer — ở đây gọi qua MockMvc để
 * phủ luôn Controller + Security. Trọng tâm: GB-10 (đủ participant mới bốc thăm được), GB-11 (chỉ
 * thao tác khi tournament ở phase cho phép; điểm không vượt race-to), GB-12 (chỉ Staff được gán mới
 * thao tác được trận), GB-14 (endpoint Public bị ẩn khi isPublicRatio=false).
 */
class MatchControllerL2Test extends AbstractControllerIntegrationTest {

	@Autowired private TournamentRepository tournamentRepository;
	@Autowired private TournamentConfigRepository tournamentConfigRepository;
	@Autowired private TournamentConfigValueRepository configValueRepository;
	@Autowired private ConfigFieldDefinitionRepository configFieldRepository;
	@Autowired private ParticipantRepository participantRepository;
	@Autowired private BranchRepository branchRepository;

	private Branch branch1() {
		return branchRepository.findByOwnerId(userIdOf(TestAccounts.OWNER_EMAIL)).stream()
				.filter(b -> b.getName().contains("Thủ Đức"))
				.findFirst().orElseThrow();
	}

	private void addConfigValue(Tournament t, String key, String value) {
		ConfigFieldDefinition fieldDef = configFieldRepository.findById(key).orElseThrow();
		configValueRepository.save(TournamentConfigValue.builder()
				.id(new TournamentConfigValueId(t.getId(), key))
				.tournament(t).fieldDefinition(fieldDef).value(value).build());
	}

	/** Giải SINGLE_ELIMINATION 4 người, chưa bốc thăm — status REGISTRATION_CLOSED (đủ điều kiện GB-10). */
	private Tournament setupReadyToDrawTournament() {
		var owner = seedUser(TestAccounts.OWNER_EMAIL);
		Instant now = Instant.now();
		Tournament t = tournamentRepository.save(Tournament.builder()
				.name("L2 Match Test " + System.nanoTime())
				.gameType("9_BALL")
				.format("SINGLE_ELIMINATION")
				.participantType(ParticipantType.SINGLE.getValue())
				.status(TournamentStatus.REGISTRATION_CLOSED.getValue())
				.maxParticipants(4)
				.tableCount(1)
				.entryFee(BigDecimal.ZERO)
				.prizePool(BigDecimal.ZERO)
				.registrationDeadline(now.minus(1, ChronoUnit.DAYS))
				.startAt(now)
				.endAt(now.plus(1, ChronoUnit.DAYS))
				.isShowTournament(false)
				.isPublicRatio(false)
				.isRegister(false)
				.createdBy(owner)
				.branch(branch1())
				.build());

		tournamentConfigRepository.save(TournamentConfig.builder()
				.tournament(t).formatCode(t.getFormat()).seedingMethod(SeedingMethod.RANDOM.name()).build());
		addConfigValue(t, "bracket_size", "4");
		addConfigValue(t, "third_place_match", "false");
		addConfigValue(t, "break_rule", "ALTERNATE_BREAK");
		addConfigValue(t, "lag_for_break", "true");
		addConfigValue(t, "scoring_unit", "GAME");

		for (String name : List.of("L2 P1", "L2 P2", "L2 P3", "L2 P4")) {
			participantRepository.save(Participant.builder()
					.tournament(t).registration(null)
					.participantType(ParticipantType.SINGLE.getValue())
					.displayName(name)
					.status(ParticipantStatus.ACTIVE.getValue())
					.build());
		}
		return t;
	}

	/** Bốc thăm + confirm + chuyển IN_PROGRESS qua đúng API — trả về id trận R1-M1. */
	private Long drawConfirmAndStart(Tournament t, String ownerAuth) throws Exception {
		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/draw", t.getId())
						.header("Authorization", ownerAuth))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/draw/confirm", t.getId())
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/owner/tournaments/{id}/status", t.getId())
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"IN_PROGRESS\"}"))
				.andExpect(status().isOk());

		String stagesBody = mockMvc.perform(get("/api/v1/owner/tournaments/{id}/stages", t.getId())
						.header("Authorization", ownerAuth))
				.andReturn().getResponse().getContentAsString();
		JsonNode matches = objectMapper.readTree(stagesBody).path("data").get(0).path("matches");
		for (JsonNode m : matches) {
			if (m.path("roundNo").asInt() == 1 && m.path("positionNo").asInt() == 1) {
				return m.path("id").asLong();
			}
		}
		throw new IllegalStateException("Không tìm thấy R1-M1");
	}

	// ── Draw (GB-10) ─────────────────────────────────────────────────────

	@Test
	void draw_enoughParticipants_created201() throws Exception {
		Tournament t = setupReadyToDrawTournament();
		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/draw", t.getId())
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isCreated());
	}

	@Test
	void draw_asPlayer_rejected403() throws Exception {
		Tournament t = setupReadyToDrawTournament();
		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/draw", t.getId())
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void draw_whileStillDraft_rejected() throws Exception {
		// GB-10: chỉ bốc thăm được khi REGISTRATION_CLOSED (hoặc DRAW_PREVIEW để regen) — DRAFT bị chặn.
		Tournament t = setupReadyToDrawTournament();
		t.setStatus(TournamentStatus.DRAFT.getValue());
		tournamentRepository.save(t);

		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/draw", t.getId())
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	// ── Match lifecycle (GB-11) ─────────────────────────────────────────

	@Test
	void startScoreComplete_fullHappyPath() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Tournament t = setupReadyToDrawTournament();
		Long matchId = drawConfirmAndStart(t, ownerAuth);

		mockMvc.perform(patch("/api/v1/owner/matches/{id}/start", matchId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

		UpdateScoreRequest score = new UpdateScoreRequest();
		// resolveRoundKey(round, totalRounds): distance-to-final 0="final", 1="semi_final", 2="quarter_final",
		// else="round_1". Bracket 4 người chỉ có 2 vòng -> vòng 1 là distance=1 => "semi_final" (raceTo=7),
		// KHÔNG phải "round_1" (raceTo=5) — round_1 chỉ áp dụng bracket có từ 5 vòng (32+ người) trở lên.
		score.setPlayer1Score(7);
		score.setPlayer2Score(2);
		String scoreBody = mockMvc.perform(put("/api/v1/owner/matches/{id}/score", matchId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(score)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.player1Score").value(7))
				.andReturn().getResponse().getContentAsString();
		Long winnerId = objectMapper.readTree(scoreBody).path("data").path("player1").path("id").asLong();

		CompleteMatchRequest complete = new CompleteMatchRequest();
		complete.setWinnerParticipantId(winnerId);

		mockMvc.perform(post("/api/v1/owner/matches/{id}/complete", matchId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(complete)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.winner.id").value(winnerId));
	}

	@Test
	void start_beforeInProgressPhase_rejected() throws Exception {
		// GB-11: DRAW_DONE (chưa IN_PROGRESS) với SINGLE_ELIMINATION -> chưa thao tác được trận.
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Tournament t = setupReadyToDrawTournament();
		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/draw", t.getId())
						.header("Authorization", ownerAuth)).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/owner/tournaments/{id}/draw/confirm", t.getId())
						.header("Authorization", ownerAuth)).andExpect(status().isOk());

		String stagesBody = mockMvc.perform(get("/api/v1/owner/tournaments/{id}/stages", t.getId())
						.header("Authorization", ownerAuth))
				.andReturn().getResponse().getContentAsString();
		JsonNode matches = objectMapper.readTree(stagesBody).path("data").get(0).path("matches");
		long matchId = matches.get(0).path("id").asLong();

		mockMvc.perform(patch("/api/v1/owner/matches/{id}/start", matchId)
						.header("Authorization", ownerAuth))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void updateScore_exceedsRaceTo_rejected() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Tournament t = setupReadyToDrawTournament();
		Long matchId = drawConfirmAndStart(t, ownerAuth);
		mockMvc.perform(patch("/api/v1/owner/matches/{id}/start", matchId)
						.header("Authorization", ownerAuth)).andExpect(status().isOk());

		UpdateScoreRequest score = new UpdateScoreRequest();
		score.setPlayer1Score(8); // > race-to thật của vòng 1 bracket 4 người (semi_final = 7, xem resolveRoundKey)
		score.setPlayer2Score(0);

		mockMvc.perform(put("/api/v1/owner/matches/{id}/score", matchId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(score)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void updateScore_negativeValue_rejected400() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Tournament t = setupReadyToDrawTournament();
		Long matchId = drawConfirmAndStart(t, ownerAuth);

		UpdateScoreRequest score = new UpdateScoreRequest();
		score.setPlayer1Score(-1);
		score.setPlayer2Score(0);

		mockMvc.perform(put("/api/v1/owner/matches/{id}/score", matchId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(score)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void staffOperation_notAssigned_rejected() throws Exception {
		// GB-12: chỉ Staff được gán làm trọng tài của TRẬN ĐÓ mới thao tác được — staff1 chưa được gán.
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Tournament t = setupReadyToDrawTournament();
		Long matchId = drawConfirmAndStart(t, ownerAuth);

		mockMvc.perform(patch("/api/v1/staff/matches/{id}/start", matchId)
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void start_withoutToken_rejected401() throws Exception {
		mockMvc.perform(patch("/api/v1/owner/matches/{id}/start", 1L))
				.andExpect(status().isUnauthorized());
	}

	// ── Public visibility (GB-14) ─────────────────────────────────────────

	@Test
	void publicStages_whenIsPublicRatioFalse_hiddenAsNotFound() throws Exception {
		Tournament t = setupReadyToDrawTournament(); // isPublicRatio=false theo setup
		mockMvc.perform(get("/api/v1/tournaments/{id}/stages", t.getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	void publicMatches_whenIsPublicRatioTrue_visible() throws Exception {
		Tournament t = setupReadyToDrawTournament();
		t.setIsPublicRatio(true);
		tournamentRepository.save(t);

		mockMvc.perform(get("/api/v1/tournaments/{id}/matches", t.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	// ── Player: xem lịch của tôi ───────────────────────────────────────────

	@Test
	void playerMatches_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/player/matches"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void playerMatches_asPlayer_ok() throws Exception {
		mockMvc.perform(get("/api/v1/player/matches")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}
}
