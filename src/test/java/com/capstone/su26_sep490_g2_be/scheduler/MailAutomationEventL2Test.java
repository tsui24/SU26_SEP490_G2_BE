package com.capstone.su26_sep490_g2_be.scheduler;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.RejectRegistrationRequest;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BJ-05 MailAutomationEventListener — AFTER_COMMIT không chạy trong test {@code @Transactional},
 * nên assert event được publish qua {@link ApplicationEvents}.
 */
@RecordApplicationEvents
@TestPropertySource(properties = "spring.task.scheduling.enabled=false")
class MailAutomationEventL2Test extends AbstractControllerIntegrationTest {

	@Autowired private RegistrationService registrationService;
	@Autowired private TournamentRepository tournamentRepository;
	@Autowired private RegistrationRepository registrationRepository;
	@Autowired private BranchRepository branchRepository;
	@Autowired private ApplicationEvents applicationEvents;

	private Tournament openFreeTournament() {
		var owner = seedUser(TestAccounts.OWNER_EMAIL);
		var branch = branchRepository.findByOwnerId(owner.getId()).stream()
				.filter(b -> b.getName().contains("Thủ Đức")).findFirst().orElseThrow();
		Instant now = Instant.now();
		return tournamentRepository.save(Tournament.builder()
				.name("L2 BJ-05 " + System.nanoTime())
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
				.createdBy(owner)
				.branch(branch)
				.version(0L)
				.build());
	}

	private Registration pending(Tournament t, String email) {
		return registrationRepository.save(Registration.builder()
				.tournament(t)
				.user(seedUser(email))
				.registrationType("SINGLE")
				.playerFullName("L2 BJ05")
				.playerPhone("0911111111")
				.status(RegistrationStatus.PENDING_PAYMENT.getValue())
				.build());
	}

	/** TC-SCH-BJ05-001 */
	@Test
	void approveRegistration_publishesRegistrationApprovedEvent() {
		// Nếu code sai: duyệt đơn xong người chơi không nhận được thư báo đã vào giải.
		Tournament t = openFreeTournament();
		Registration reg = pending(t, TestAccounts.PLAYER3_EMAIL);

		registrationService.approve(reg.getId(), userIdOf(TestAccounts.OWNER_EMAIL));

		assertTrue(applicationEvents.stream(MailDomainEvent.class)
				.anyMatch(e -> EmailEventType.REGISTRATION_APPROVED.equals(e.eventType())
						&& t.getId().equals(e.tournamentId())
						&& ("REGISTRATION-" + reg.getId()).equals(e.entityKey())));
	}

	/** TC-SCH-BJ05-002 */
	@Test
	void rejectRegistration_publishesRejectedNotApprovedEvent() {
		// Nếu code sai: từ chối đơn lại gửi thư chúc mừng đã được duyệt, người chơi hiểu nhầm mình đã vào giải.
		Tournament t = openFreeTournament();
		Registration reg = pending(t, TestAccounts.PLAYER4_EMAIL);
		RejectRegistrationRequest req = new RejectRegistrationRequest();
		req.setReason("L2 reject for BJ-05");

		registrationService.reject(reg.getId(), userIdOf(TestAccounts.OWNER_EMAIL), req);

		assertTrue(applicationEvents.stream(MailDomainEvent.class)
				.anyMatch(e -> EmailEventType.REGISTRATION_REJECTED.equals(e.eventType())
						&& t.getId().equals(e.tournamentId())));
		assertTrue(applicationEvents.stream(MailDomainEvent.class)
				.noneMatch(e -> EmailEventType.REGISTRATION_APPROVED.equals(e.eventType())
						&& t.getId().equals(e.tournamentId())));
	}
}
