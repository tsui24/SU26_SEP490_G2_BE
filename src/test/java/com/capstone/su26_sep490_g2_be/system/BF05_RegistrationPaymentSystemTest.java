package com.capstone.su26_sep490_g2_be.system;

import com.capstone.su26_sep490_g2_be.service.PayOSService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-05 Tournament Registration & Payment Processing.
 * Rows TC-SYS-BF05-001..016 in docs/Report_5.3_SystemTests_L3.md.
 *
 * <p>{@code PayOSService.createPaymentLink} calls the real PayOS API (api-merchant.payos.vn),
 * which this sandbox cannot reach (no outbound DNS) — a real environment limitation, not a code
 * defect. It's spied out here so the checkout endpoint can create a local {@code Payment} row
 * without a live network call. Everything else — {@code verifyWebhookSignature}, the webhook
 * endpoint, and the registration/payment state machine — runs for real. This does NOT weaken the
 * test's own PayOS-related assertions, since those exercise this app's webhook handling, not
 * PayOS's own API.
 */
@Transactional
class BF05_RegistrationPaymentSystemTest extends SystemTestBase {

	@MockitoSpyBean
	PayOSService payOSService;

	/** TC-SYS-BF05-001..009 — main flow: submit, pay, webhook confirms, registration APPROVED. */
	@Test
	void mainFlow_paidRegistration_approvedAndParticipantCreated() throws Exception {
		doReturn("https://fake-payos-checkout.test-l3.local").when(payOSService)
				.createPaymentLink(anyLong(), anyLong(), anyString());
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);

		// 0a (setup) — paid tournament, room for 2, publicly registrable
		Number tournamentId = createConfigurePublishTournament(managerToken, branchId, 100000, 2);

		String playerToken = login("player1@gmail.com", "player123");

		// TC-SYS-BF05-001
		mvc.perform(authed(put("/api/v1/profile"), playerToken)
						.content("""
								{"fullName":"QA Player One","phone":"%s"}
								""".formatted(freshPhone())))
				.andExpect(status().isOk());

		// TC-SYS-BF05-002
		mvc.perform(authed(get("/api/v1/player/tournaments/{id}", tournamentId), playerToken))
				.andExpect(status().isOk());

		// TC-SYS-BF05-003
		var regRes = mvc.perform(authed(post("/api/v1/player/tournaments/{id}/registrations", tournamentId), playerToken)
						.content("""
								{"registrationType":"SINGLE","fieldValues":[
								 {"fieldKey":"player_full_name","value":"QA Player"},
								 {"fieldKey":"player_phone","value":"0900000099"}
								]}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
				.andReturn();
		Number regId = read(bodyOf(regRes), "$.data.id");

		// TC-SYS-BF05-004
		var checkoutRes = mvc.perform(authed(post("/api/v1/player/registrations/{id}/checkout", regId), playerToken))
				.andExpect(status().isOk())
				.andReturn();
		Number orderCode = read(bodyOf(checkoutRes), "$.data.orderCode");

		// TC-SYS-BF05-005..007 — PayOS webhook confirms success
		mvc.perform(post("/api/v1/payments/payos/webhook")
						.contentType("application/json")
						.content(payOsWebhookPayload("00", orderCode.longValue(), "QA-REF-" + uniq())))
				.andExpect(status().isOk());

		// TC-SYS-BF05-008/009 — End condition
		mvc.perform(authed(get("/api/v1/player/registrations/{id}", regId), playerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("APPROVED"));
		mvc.perform(authed(get("/api/v1/player/payments"), playerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.registrationId==" + regId + ")].status").value("SUCCESS"));
	}

	/**
	 * TC-SYS-BF05-010..016 — exception path: paid-but-rejected registration when the last slot
	 * fills first. Two players both reach PENDING_PAYMENT (slot check counts only APPROVED); the
	 * second to pay finds the roster full and ends up REJECTED despite a successful payment.
	 */
	@Test
	void exceptionPath_lastSlotRace_paidButRejectedRegistration() throws Exception {
		doReturn("https://fake-payos-checkout.test-l3.local").when(payOSService)
				.createPaymentLink(anyLong(), anyLong(), anyString());
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);
		// maxParticipants must be >= 2 (server validation); one manual ACTIVE participant is added
		// up front so only a single real slot remains for Players A and B to race for below.
		Number tournamentId = createConfigurePublishTournament(managerToken, branchId, 100000, 2);
		mvc.perform(authed(post("/api/v1/manager/tournaments/{id}/participants/manual", tournamentId), managerToken)
						.content("""
								{"displayName":"QA Seat Filler","seedNo":1}
								"""))
				.andExpect(status().isCreated());

		String playerAToken = login("player2@gmail.com", "player123");
		String playerBToken = login("player3@gmail.com", "player123");

		// TC-SYS-BF05-011/012
		Number regAId = submitRegistration(tournamentId, playerAToken);
		Number regBId = submitRegistration(tournamentId, playerBToken);

		// TC-SYS-BF05-013 — Player A pays first, slot fills
		Number orderA = checkout(regAId, playerAToken);
		mvc.perform(post("/api/v1/payments/payos/webhook")
						.contentType("application/json")
						.content(payOsWebhookPayload("00", orderA.longValue(), "QA-REF-A-" + uniq())))
				.andExpect(status().isOk());
		mvc.perform(authed(get("/api/v1/player/registrations/{id}", regAId), playerAToken))
				.andExpect(jsonPath("$.data.status").value("APPROVED"));

		// TC-SYS-BF05-014 — Player B still allowed to checkout (no re-check of slots at checkout time)
		Number orderB = checkout(regBId, playerBToken);

		// TC-SYS-BF05-015 — webhook always returns 200; Payment B succeeds but roster is now full
		mvc.perform(post("/api/v1/payments/payos/webhook")
						.contentType("application/json")
						.content(payOsWebhookPayload("00", orderB.longValue(), "QA-REF-B-" + uniq())))
				.andExpect(status().isOk());

		// TC-SYS-BF05-016 — dangling state: Payment SUCCESS but Registration REJECTED
		mvc.perform(authed(get("/api/v1/manager/registrations/{id}/payments", regBId), managerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
		mvc.perform(authed(get("/api/v1/player/registrations/{id}", regBId), playerBToken))
				.andExpect(jsonPath("$.data.status").value("REJECTED"));
	}

	private Number createConfigurePublishTournament(String managerToken, Number branchId, long entryFee, int maxParticipants) throws Exception {
		Number templateId = playerRegBasicTemplateId();
		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF05 Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":%s,"branchId":%s,"entryFee":%s,
								 "isRegister":true,"isShowTournament":true,"registrationFormTemplateId":%s}
								""".formatted(uniq(), maxParticipants, branchId, entryFee, templateId)))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		mvc.perform(authed(put("/api/v1/manager/tournaments/{id}/config", tournamentId), managerToken)
						.content("""
								{"seedingMethod":"RANDOM","raceToOverrides":[],"fields":[
								 {"fieldKey":"bracket_size","value":"8"},
								 {"fieldKey":"third_place_match","value":"true"},
								 {"fieldKey":"break_rule","value":"ALTERNATE_BREAK"},
								 {"fieldKey":"lag_for_break","value":"true"}
								]}
								"""))
				.andExpect(status().isOk());

		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"OPEN_FOR_REGISTRATION"}
								"""))
				.andExpect(status().isOk());

		return tournamentId;
	}

	private Number submitRegistration(Number tournamentId, String playerToken) throws Exception {
		var res = mvc.perform(authed(post("/api/v1/player/tournaments/{id}/registrations", tournamentId), playerToken)
						.content("""
								{"registrationType":"SINGLE","fieldValues":[
								 {"fieldKey":"player_full_name","value":"QA Player"},
								 {"fieldKey":"player_phone","value":"0900000099"}
								]}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return read(bodyOf(res), "$.data.id");
	}

	private Number checkout(Number regId, String playerToken) throws Exception {
		var res = mvc.perform(authed(post("/api/v1/player/registrations/{id}/checkout", regId), playerToken))
				.andExpect(status().isOk())
				.andReturn();
		return read(bodyOf(res), "$.data.orderCode");
	}

	private Number playerRegBasicTemplateId() throws Exception {
		String adminToken = login("admin@gmail.com", "admin1");
		var res = mvc.perform(authed(get("/api/v1/admin/registration-form-templates"), adminToken))
				.andExpect(status().isOk())
				.andReturn();
		java.util.List<Number> ids = read(bodyOf(res), "$.data.content[?(@.code=='PLAYER_REG_BASIC')].id");
		return ids.get(0);
	}

	private Number accessibleBranchId(String managerToken) throws Exception {
		var res = mvc.perform(authed(get("/api/v1/manager/branches"), managerToken))
				.andExpect(status().isOk())
				.andReturn();
		return read(bodyOf(res), "$.data.content[0].id");
	}
}
