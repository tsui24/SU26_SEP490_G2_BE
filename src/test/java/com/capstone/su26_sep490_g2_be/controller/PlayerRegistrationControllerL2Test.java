package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.SubmitTournamentRegistrationRequest;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFormTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — UC-23 (Player đăng ký giải). Giải MIỄN PHÍ (entryFee=0) + isRegister=true +
 * OPEN_FOR_REGISTRATION + có registrationFormTemplateId — auto-approve ngay theo GB-09 (đăng ký
 * được duyệt tự động khi giải miễn phí), tránh phải dựng cả luồng PayOS thật ở L2.
 */
class PlayerRegistrationControllerL2Test extends AbstractControllerIntegrationTest {

	@Autowired private TournamentRepository tournamentRepository;
	@Autowired private BranchRepository branchRepository;
	@Autowired private RegistrationFormTemplateRepository formTemplateRepository;

	private Tournament setupOpenFreeRegistrationTournament() {
		var owner = seedUser(TestAccounts.OWNER_EMAIL);
		var branch1 = branchRepository.findByOwnerId(owner.getId()).stream()
				.filter(b -> b.getName().contains("Thủ Đức")).findFirst().orElseThrow();
		var template = formTemplateRepository.findByCode("PLAYER_REG_BASIC").orElseThrow();
		Instant now = Instant.now();
		return tournamentRepository.save(Tournament.builder()
				.name("L2 Registration Test " + System.nanoTime())
				.gameType("9_BALL")
				.format("SINGLE_ELIMINATION")
				.participantType(ParticipantType.SINGLE.getValue())
				.status(TournamentStatus.OPEN_FOR_REGISTRATION.getValue())
				.maxParticipants(16)
				.tableCount(1)
				.entryFee(BigDecimal.ZERO)
				.prizePool(BigDecimal.ZERO)
				.registrationDeadline(now.plus(7, ChronoUnit.DAYS))
				.startAt(now.plus(10, ChronoUnit.DAYS))
				.endAt(now.plus(11, ChronoUnit.DAYS))
				.isShowTournament(true)
				.isPublicRatio(true)
				.isRegister(true)
				.registrationFormTemplateId(template.getId())
				.createdBy(owner)
				.branch(branch1)
				.build());
	}

	private SubmitTournamentRegistrationRequest validSubmitRequest() {
		SubmitTournamentRegistrationRequest.FieldValueItem name = new SubmitTournamentRegistrationRequest.FieldValueItem();
		name.setFieldKey("player_full_name");
		name.setValue("Nguyễn L2 Test");
		SubmitTournamentRegistrationRequest.FieldValueItem phone = new SubmitTournamentRegistrationRequest.FieldValueItem();
		phone.setFieldKey("player_phone");
		phone.setValue("0912345678");

		SubmitTournamentRegistrationRequest req = new SubmitTournamentRegistrationRequest();
		req.setRegistrationType("SINGLE");
		req.setFieldValues(List.of(name, phone));
		return req;
	}

	@Test
	void listTournaments_public_ok() throws Exception {
		mockMvc.perform(get("/api/v1/player/tournaments")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void getTournamentDetail_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/player/tournaments/{id}", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void submitRegistration_freeTournament_autoApproved() throws Exception {
		Tournament t = setupOpenFreeRegistrationTournament();

		mockMvc.perform(post("/api/v1/player/tournaments/{id}/registrations", t.getId())
						.header("Authorization", bearerToken(TestAccounts.PLAYER5_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(validSubmitRequest())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("APPROVED"));
	}

	@Test
	void submitRegistration_duplicateSubmission_rejected409() throws Exception {
		Tournament t = setupOpenFreeRegistrationTournament();
		String playerAuth = bearerToken(TestAccounts.PLAYER6_EMAIL);

		mockMvc.perform(post("/api/v1/player/tournaments/{id}/registrations", t.getId())
						.header("Authorization", playerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(validSubmitRequest())))
				.andExpect(status().isCreated());

		// GB-08: không được đăng ký 2 lần cho cùng 1 giải.
		mockMvc.perform(post("/api/v1/player/tournaments/{id}/registrations", t.getId())
						.header("Authorization", playerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(validSubmitRequest())))
				.andExpect(status().isConflict());
	}

	@Test
	void submitRegistration_tournamentNotOpen_rejected() throws Exception {
		// GB-07: chỉ nhận đăng ký khi status=OPEN_FOR_REGISTRATION.
		Tournament t = setupOpenFreeRegistrationTournament();
		t.setStatus(TournamentStatus.DRAFT.getValue());
		tournamentRepository.save(t);

		mockMvc.perform(post("/api/v1/player/tournaments/{id}/registrations", t.getId())
						.header("Authorization", bearerToken(TestAccounts.PLAYER7_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(validSubmitRequest())))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void submitRegistration_invalidRegistrationType_rejected400() throws Exception {
		Tournament t = setupOpenFreeRegistrationTournament();
		SubmitTournamentRegistrationRequest req = validSubmitRequest();
		req.setRegistrationType("TRIPLE");

		mockMvc.perform(post("/api/v1/player/tournaments/{id}/registrations", t.getId())
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getMyRegistrationForTournament_notRegistered_returnsNullData() throws Exception {
		Tournament t = setupOpenFreeRegistrationTournament();

		mockMvc.perform(get("/api/v1/player/tournaments/{id}/my-registration", t.getId())
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void myRegistrationsThenCancel_fullLifecycle() throws Exception {
		Tournament t = setupOpenFreeRegistrationTournament();
		String playerAuth = bearerToken(TestAccounts.PLAYER8_EMAIL);

		String body = mockMvc.perform(post("/api/v1/player/tournaments/{id}/registrations", t.getId())
						.header("Authorization", playerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(validSubmitRequest())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long registrationId = objectMapper.readTree(body).path("data").path("id").asLong();

		mockMvc.perform(get("/api/v1/player/registrations")
						.header("Authorization", playerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());

		mockMvc.perform(get("/api/v1/player/registrations/{id}", registrationId)
						.header("Authorization", playerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(registrationId));

		mockMvc.perform(delete("/api/v1/player/registrations/{id}", registrationId)
						.header("Authorization", playerAuth))
				.andExpect(status().isOk());
	}

	@Test
	void getRegistrationDetail_ofAnotherPlayer_rejected() throws Exception {
		Tournament t = setupOpenFreeRegistrationTournament();
		String player9Auth = bearerToken(TestAccounts.PLAYER9_EMAIL);
		String body = mockMvc.perform(post("/api/v1/player/tournaments/{id}/registrations", t.getId())
						.header("Authorization", player9Auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(validSubmitRequest())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long registrationId = objectMapper.readTree(body).path("data").path("id").asLong();

		// Player khác không được xem đăng ký không phải của mình.
		mockMvc.perform(get("/api/v1/player/registrations/{id}", registrationId)
						.header("Authorization", bearerToken(TestAccounts.PLAYER10_EMAIL)))
				.andExpect(status().is4xxClientError());
	}
}
