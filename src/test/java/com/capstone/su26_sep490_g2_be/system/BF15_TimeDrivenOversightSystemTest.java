package com.capstone.su26_sep490_g2_be.system;

import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.scheduler.TournamentAutoStatusScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L3 §3a — BF-15 Time-driven Oversight & Operational Monitoring.
 * Rows TC-SYS-BF15-001..007 in docs/Report_5.3_SystemTests_L3.md.
 *
 * <p>{@code TournamentAutoStatusScheduler}'s {@code @Scheduled} methods are plain public methods
 * on a Spring bean — this test autowires the bean directly and calls them, rather than waiting
 * for the real 5-minute cron interval.
 */
@Transactional
class BF15_TimeDrivenOversightSystemTest extends SystemTestBase {

	@Autowired
	TournamentAutoStatusScheduler scheduler;

	@Autowired
	TournamentRepository tournamentRepository;

	/** TC-SYS-BF15-001..004 — main flow: overdue registration is auto-closed, audit entry recorded. */
	@Test
	void mainFlow_autoCloseExpiredRegistration_auditLogged() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createPublishedTournament(managerToken, branchId);

		// TC-SYS-BF15-001 (setup) — backdate registrationDeadline directly via repository, since
		// no HTTP endpoint lets a Manager set a deadline already in the past
		Tournament t = tournamentRepository.findById(tournamentId.longValue()).orElseThrow();
		t.setRegistrationDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
		tournamentRepository.save(t);
		tournamentRepository.flush();

		// TC-SYS-BF15-002 — direct scheduler invocation (not waiting for the real cron)
		scheduler.autoCloseExpiredRegistrations();

		// TC-SYS-BF15-003/004 — End condition
		mvc.perform(authed(get("/api/v1/manager/tournaments/{id}/audit-logs", tournamentId), managerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.toStatus=='REGISTRATION_CLOSED')]").exists());
		mvc.perform(authed(get("/api/v1/manager/tournaments/{id}", tournamentId), managerToken))
				.andExpect(jsonPath("$.data.status").value("REGISTRATION_CLOSED"));
	}

	/**
	 * TC-SYS-BF15-005..007 — exception path: no records meet the time condition — cycle ends with
	 * no data change (a future deadline is untouched by the scheduler).
	 */
	@Test
	void exceptionPath_noRecordsMeetCondition_noOp() throws Exception {
		String managerToken = login("manager@gmail.com", "manager123");
		Number branchId = accessibleBranchId(managerToken);
		Number tournamentId = createPublishedTournament(managerToken, branchId);

		// registrationDeadline stays at its default (far future) — nothing to close

		// TC-SYS-BF15-006
		scheduler.autoCloseExpiredRegistrations();

		// TC-SYS-BF15-007 — status unchanged
		mvc.perform(authed(get("/api/v1/manager/tournaments/{id}", tournamentId), managerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("OPEN_FOR_REGISTRATION"));
	}

	private Number createPublishedTournament(String managerToken, Number branchId) throws Exception {
		var tRes = mvc.perform(authed(post("/api/v1/manager/tournaments"), managerToken)
						.content("""
								{"name":"QA BF15 Tour %s","gameType":"9_BALL","format":"SINGLE_ELIMINATION",
								 "participantType":"SINGLE","maxParticipants":8,"branchId":%s,"isRegister":false,
								 "registrationDeadline":"%s"}
								""".formatted(uniq(), branchId,
								Instant.now().plus(30, ChronoUnit.DAYS))))
				.andExpect(status().isCreated())
				.andReturn();
		Number tournamentId = read(bodyOf(tRes), "$.data.id");

		mvc.perform(authed(patch("/api/v1/manager/tournaments/{id}/status", tournamentId), managerToken)
						.content("""
								{"status":"OPEN_FOR_REGISTRATION"}
								"""))
				.andExpect(status().isOk());

		return tournamentId;
	}

	private Number accessibleBranchId(String managerToken) throws Exception {
		var res = mvc.perform(authed(get("/api/v1/manager/branches"), managerToken))
				.andExpect(status().isOk())
				.andReturn();
		return read(bodyOf(res), "$.data.content[0].id");
	}
}
