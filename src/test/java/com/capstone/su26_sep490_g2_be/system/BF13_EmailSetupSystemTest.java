package com.capstone.su26_sep490_g2_be.system;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-13 Email Setup & Notification Delivery.
 * Rows TC-SYS-BF13-001..013 in docs/Report_5.3_SystemTests_L3.md.
 *
 * <p>Real SMTP delivery to smtp.gmail.com is not reachable from the test host, so
 * {@link #mainFlow_smtpDeliveryStatus_sentAndFailedBothRecorded()} controls the network boundary
 * the same way BF-05/BF-14 control PayOS/Facebook: {@code JavaMailSender} is spied so
 * {@code MailDispatcher.onEmailQueued} — the app's own real, unmodified AFTER_COMMIT/async
 * listener — runs for real and flips {@code EmailSendLog.status} to a genuine SENT or FAILED
 * outcome, rather than assuming one.
 */
@Transactional
class BF13_EmailSetupSystemTest extends SystemTestBase {

	@MockitoSpyBean
	JavaMailSender mailSender;

	/** TC-SYS-BF13-001..006 — main flow: template, layout, rule, manual send accepted. */
	@Test
	void mainFlow_templateLayoutRuleAndManualSend() throws Exception {
		String adminToken = login("admin@gmail.com", "admin1");
		String managerToken = login("manager@gmail.com", "manager123");
		String ownerToken = login("owner@gmail.com", "owner123");
		String suffix = uniq();

		// TC-SYS-BF13-001
		var tplRes = mvc.perform(authed(post("/api/v1/admin/email/templates"), adminToken)
						.content("""
								{"code":"QA_TPL_%s","name":"QA Template","category":"MARKETING",
								 "subjectTemplate":"Hello {{name}}","bodyHtmlTemplate":"<p>Hi {{name}}</p>","isActive":true}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		Number templateId = read(bodyOf(tplRes), "$.data.id");

		mvc.perform(authed(post("/api/v1/admin/email/templates/{id}/preview", templateId), adminToken)
						.content("""
								{"sampleVariables":{"name":"QA"}}
								"""))
				.andExpect(status().isOk());

		// TC-SYS-BF13-002
		mvc.perform(authed(get("/api/v1/admin/email/layout"), adminToken))
				.andExpect(status().isOk());

		// TC-SYS-BF13-003 — create a system-wide automation rule using the Step-1 template
		mvc.perform(authed(post("/api/v1/admin/email/automation-rules"), adminToken)
						.content("""
								{"code":"QA_RULE_%s","name":"QA Rule","eventType":"CUSTOM_MANUAL_SEND",
								 "templateId":%s,"recipientType":"ALL_PARTICIPANTS"}
								""".formatted(suffix, templateId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isEnabled").value(true))
				.andExpect(jsonPath("$.data.templateId").value(templateId.longValue()));

		// TC-SYS-BF13-004a (setup) — tournament with 1 registration so a recipient exists
		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createFreeTournamentWithOneRegistration(managerToken, branchId);

		// TC-SYS-BF13-005 — manual send. OwnerEmailController is /api/v1/owner/** role-gated, so
		// this call must use the Owner (who manages manager@gmail.com), not the Manager token.
		var sendRes = mvc.perform(authed(post("/api/v1/owner/tournaments/{id}/email/send-manual", tournamentId), ownerToken)
						.content("""
								{"templateId":%s,"recipientType":"ALL_PARTICIPANTS"}
								""".formatted(templateId)))
				.andExpect(status().isOk())
				.andReturn();
		int queuedCount = read(bodyOf(sendRes), "$.data.queuedCount");
		org.junit.jupiter.api.Assertions.assertTrue(queuedCount >= 1);
	}

	/**
	 * TC-SYS-BF13-006 — log/delivery-status check: a real SMTP success flips the log to SENT, a
	 * real SMTP failure flips a second log to FAILED with {@code errorMessage} populated.
	 * Not {@code @Transactional} (overridden to {@code NOT_SUPPORTED}) since {@code MailDispatcher}
	 * only runs {@code AFTER_COMMIT} — a surrounding test transaction that never commits would
	 * mean the listener never fires.
	 */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void mainFlow_smtpDeliveryStatus_sentAndFailedBothRecorded() throws Exception {
		String adminToken = login("admin@gmail.com", "admin1");
		String managerToken = login("manager@gmail.com", "manager123");
		String ownerToken = login("owner@gmail.com", "owner123");
		String suffix = uniq();

		var tplRes = mvc.perform(authed(post("/api/v1/admin/email/templates"), adminToken)
						.content("""
								{"code":"QA_TPL3_%s","name":"QA Template 3","category":"MARKETING",
								 "subjectTemplate":"Hello","bodyHtmlTemplate":"<p>Hi</p>","isActive":true}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		Number templateId = read(bodyOf(tplRes), "$.data.id");

		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createFreeTournamentWithOneRegistration(managerToken, branchId);

		// TC-SYS-BF13-006a — SMTP send succeeds -> log flips QUEUED -> SENT
		doNothing().when(mailSender).send(any(MimeMessage.class));
		mvc.perform(authed(post("/api/v1/owner/tournaments/{id}/email/send-manual", tournamentId), ownerToken)
						.content("""
								{"templateId":%s,"recipientType":"ALL_PARTICIPANTS"}
								""".formatted(templateId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.queuedCount").value(1));
		assertLogStatusEventually(ownerToken, tournamentId, "SENT");

		// TC-SYS-BF13-006b — SMTP send throws -> a second, independent log flips QUEUED -> FAILED
		doThrow(new MailSendException("simulated SMTP failure")).when(mailSender).send(any(MimeMessage.class));
		mvc.perform(authed(post("/api/v1/owner/tournaments/{id}/email/send-manual", tournamentId), ownerToken)
						.content("""
								{"templateId":%s,"recipientType":"ALL_PARTICIPANTS"}
								""".formatted(templateId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.queuedCount").value(1));
		assertLogStatusEventually(ownerToken, tournamentId, "FAILED");
	}

	/** Scoped to {@code triggerType=MANUAL} so a background automation-rule email for the same
	 * tournament (e.g. a registration-confirmation send) can't be mistaken for this chain's own
	 * manual-send log. */
	private void assertLogStatusEventually(String ownerToken, Number tournamentId, String expectedStatus) throws Exception {
		boolean found = false;
		for (int i = 0; i < 15 && !found; i++) {
			var res = mvc.perform(authed(get("/api/v1/owner/tournaments/{id}/email/logs", tournamentId)
							.param("status", expectedStatus)
							.param("triggerType", "MANUAL"), ownerToken))
					.andExpect(status().isOk())
					.andReturn();
			List<?> content = read(bodyOf(res), "$.data.content");
			found = !content.isEmpty();
			if (found) break;
			Thread.sleep(300);
		}
		org.junit.jupiter.api.Assertions.assertTrue(found, "expected a MANUAL-trigger email log with status=" + expectedStatus);
	}

	/**
	 * TC-SYS-BF13-007..013 — exception path: inactive template rejected, then empty recipients
	 * rejected, remediated by re-activating the template and creating a recipient.
	 */
	@Test
	void exceptionPath_inactiveTemplateThenEmptyRecipients_remediated() throws Exception {
		String adminToken = login("admin@gmail.com", "admin1");
		String managerToken = login("manager@gmail.com", "manager123");
		String ownerToken = login("owner@gmail.com", "owner123");
		String suffix = uniq();

		var tplRes = mvc.perform(authed(post("/api/v1/admin/email/templates"), adminToken)
						.content("""
								{"code":"QA_TPL2_%s","name":"QA Template 2","category":"MARKETING",
								 "subjectTemplate":"Hello","bodyHtmlTemplate":"<p>Hi</p>","isActive":true}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		Number templateId = read(bodyOf(tplRes), "$.data.id");

		mvc.perform(authed(patch("/api/v1/admin/email/templates/{id}/active", templateId)
						.param("active", "false"), adminToken))
				.andExpect(status().isOk());

		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createFreeTournamentNoRegistrations(managerToken, branchId);

		// TC-SYS-BF13-009
		mvc.perform(authed(post("/api/v1/owner/tournaments/{id}/email/send-manual", tournamentId), ownerToken)
						.content("""
								{"templateId":%s,"recipientType":"ALL_PARTICIPANTS"}
								""".formatted(templateId)))
				.andExpect(status().is(422))
				.andExpect(jsonPath("$.code").value("EMAIL_003"));

		// TC-SYS-BF13-010 (remediation 1)
		mvc.perform(authed(patch("/api/v1/admin/email/templates/{id}/active", templateId)
						.param("active", "true"), adminToken))
				.andExpect(status().isOk());

		// TC-SYS-BF13-011 — now inactive-template blocker is gone, but recipients are still empty
		mvc.perform(authed(post("/api/v1/owner/tournaments/{id}/email/send-manual", tournamentId), ownerToken)
						.content("""
								{"templateId":%s,"recipientType":"ALL_PARTICIPANTS"}
								""".formatted(templateId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_008"));

		// TC-SYS-BF13-012 (remediation 2) — add a recipient
		String playerToken = login("player4@gmail.com", "player123");
		mvc.perform(authed(post("/api/v1/player/tournaments/{id}/registrations", tournamentId), playerToken)
						.content("""
								{"registrationType":"SINGLE","fieldValues":[
								 {"fieldKey":"player_full_name","value":"QA Player"},
								 {"fieldKey":"player_phone","value":"0900000098"}
								]}
								"""))
				.andExpect(status().isCreated());

		// TC-SYS-BF13-013 — succeeds now
		mvc.perform(authed(post("/api/v1/owner/tournaments/{id}/email/send-manual", tournamentId), ownerToken)
						.content("""
								{"templateId":%s,"recipientType":"ALL_PARTICIPANTS"}
								""".formatted(templateId)))
				.andExpect(status().isOk());
	}

	private Number createFreeTournamentWithOneRegistration(String managerToken, Number branchId) throws Exception {
		Number tournamentId = createFreeTournamentNoRegistrations(managerToken, branchId);
		String playerToken = login("player5@gmail.com", "player123");
		mvc.perform(authed(post("/api/v1/player/tournaments/{id}/registrations", tournamentId), playerToken)
						.content("""
								{"registrationType":"SINGLE","fieldValues":[
								 {"fieldKey":"player_full_name","value":"QA Player"},
								 {"fieldKey":"player_phone","value":"0900000097"}
								]}
								"""))
				.andExpect(status().isCreated());
		return tournamentId;
	}

	private Number createFreeTournamentNoRegistrations(String managerToken, Number branchId) throws Exception {
		Number templateId = playerRegBasicTemplateId();
		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF13 Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":4,"branchId":%s,"isRegister":true,
								 "registrationFormTemplateId":%s,"isShowTournament":true}
								""".formatted(uniq(), branchId, templateId)))
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
