package com.capstone.su26_sep490_g2_be.scheduler;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentStage;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentAuditChangeType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentStageRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentStatusHistoryRepository;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 scheduled jobs BJ-01..BJ-04 — gọi thẳng method, gán timestamp trên DB, không chờ cron.
 */
@RecordApplicationEvents
@TestPropertySource(properties = "spring.task.scheduling.enabled=false")
class BackgroundJobSchedulerL2Test extends AbstractControllerIntegrationTest {

	@Autowired private TournamentAutoStatusScheduler tournamentAutoStatusScheduler;
	@Autowired private EmailReminderScheduler emailReminderScheduler;
	@Autowired private TournamentRepository tournamentRepository;
	@Autowired private TournamentStatusHistoryRepository historyRepository;
	@Autowired private BranchRepository branchRepository;
	@Autowired private RegistrationRepository registrationRepository;
	@Autowired private ParticipantRepository participantRepository;
	@Autowired private TournamentStageRepository stageRepository;
	@Autowired private MatchRepository matchRepository;
	@Autowired private ApplicationEvents applicationEvents;
	@Autowired private EntityManager entityManager;

	@BeforeEach
	void seedOptimisticLockVersions() {
		entityManager.createNativeQuery("UPDATE tournaments SET version = 0 WHERE version IS NULL").executeUpdate();
		entityManager.createNativeQuery("UPDATE matches SET version = 0 WHERE version IS NULL").executeUpdate();
		entityManager.flush();
		entityManager.clear();
	}

	private Branch thuDuc() {
		return branchRepository.findByOwnerId(userIdOf(TestAccounts.OWNER_EMAIL)).stream()
				.filter(b -> b.getName().contains("Thủ Đức"))
				.findFirst().orElseThrow();
	}

	private Tournament newTournament(String status, Instant deadline, Instant startAt) {
		Instant now = Instant.now();
		return tournamentRepository.save(Tournament.builder()
				.name("L2 BJ " + System.nanoTime())
				.gameType("9_BALL")
				.format("SINGLE_ELIMINATION")
				.participantType(ParticipantType.SINGLE.getValue())
				.status(status)
				.maxParticipants(16)
				.tableCount(1)
				.entryFee(BigDecimal.ZERO)
				.prizePool(BigDecimal.ZERO)
				.registrationDeadline(deadline)
				.startAt(startAt)
				.endAt(now.plus(14, ChronoUnit.DAYS))
				.isShowTournament(false)
				.isPublicRatio(false)
				.isRegister(true)
				.createdBy(seedUser(TestAccounts.OWNER_EMAIL))
				.branch(thuDuc())
				.version(0L)
				.build());
	}

	private Registration approvedRegistration(Tournament t, String playerEmail) {
		return registrationRepository.save(Registration.builder()
				.tournament(t)
				.user(seedUser(playerEmail))
				.registrationType("SINGLE")
				.playerFullName("L2 Player " + playerEmail)
				.playerPhone("0900000001")
				.status(RegistrationStatus.APPROVED.getValue())
				.build());
	}

	private long autoAuditCount(Long tournamentId) {
		return historyRepository.existsByTournamentIdAndChangeType(
				tournamentId, TournamentAuditChangeType.AUTO.getValue()) ? 1 : 0;
	}

	private long warningCount(Long tournamentId) {
		return historyRepository.existsByTournamentIdAndChangeType(
				tournamentId, TournamentAuditChangeType.WARNING.getValue()) ? 1 : 0;
	}

	private boolean published(EmailEventType type, Long tournamentId) {
		return applicationEvents.stream(MailDomainEvent.class)
				.anyMatch(e -> type.equals(e.eventType()) && tournamentId.equals(e.tournamentId()));
	}

	// ── BJ-03 autoCloseExpiredRegistrations ──────────────────────────────

	/** TC-SCH-BJ03-001 */
	@Test
	void autoClose_overdueOpenRegistration_closesAndWritesAudit() {
		// Nếu code sai: giải đã quá hạn đăng ký vẫn để mở, người chơi còn nộp đơn được sau hạn.
		Tournament t = newTournament(
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
				Instant.now().minus(10, ChronoUnit.MINUTES),
				Instant.now().plus(2, ChronoUnit.DAYS));

		tournamentAutoStatusScheduler.autoCloseExpiredRegistrations();

		Tournament reloaded = tournamentRepository.findById(t.getId()).orElseThrow();
		assertEquals(TournamentStatus.REGISTRATION_CLOSED.getValue(), reloaded.getStatus());
		assertTrue(historyRepository.existsByTournamentIdAndChangeType(
				t.getId(), TournamentAuditChangeType.AUTO.getValue()));
	}

	/** TC-SCH-BJ03-002 */
	@Test
	void autoClose_deadlineStillInFuture_doesNotTouchTournament() {
		// Nếu code sai: giải còn hạn đăng ký bị đóng sớm, BTC mất lượt nhận thêm người chơi.
		Tournament t = newTournament(
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
				Instant.now().plus(2, ChronoUnit.HOURS),
				Instant.now().plus(3, ChronoUnit.DAYS));

		tournamentAutoStatusScheduler.autoCloseExpiredRegistrations();

		Tournament reloaded = tournamentRepository.findById(t.getId()).orElseThrow();
		assertEquals(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), reloaded.getStatus());
		assertEquals(0, autoAuditCount(t.getId()));
	}

	/** TC-SCH-BJ03-003 */
	@Test
	void autoClose_draftWithPastDeadline_doesNotTouchTournament() {
		// Nếu code sai: giải nháp chưa mở đăng ký bị hệ thống đóng trạng thái, BTC tưởng đã chạy vòng đời thật.
		Tournament t = newTournament(
				TournamentStatus.DRAFT.getValue(),
				Instant.now().minus(1, ChronoUnit.DAYS),
				Instant.now().plus(2, ChronoUnit.DAYS));

		tournamentAutoStatusScheduler.autoCloseExpiredRegistrations();

		Tournament reloaded = tournamentRepository.findById(t.getId()).orElseThrow();
		assertEquals(TournamentStatus.DRAFT.getValue(), reloaded.getStatus());
		assertEquals(0, autoAuditCount(t.getId()));
	}

	// ── BJ-04 warnOverdueTournamentStart ─────────────────────────────────

	/** TC-SCH-BJ04-001 */
	@Test
	void warnOverdue_pastStartStillNotReady_writesWarningKeepsStatus() {
		// Nếu code sai: giải đã quá giờ khai mạc mà bracket chưa xong thì không ai được nhắc, hoặc bị đổi trạng thái nhầm.
		Tournament t = newTournament(
				TournamentStatus.DRAW_PREVIEW.getValue(),
				Instant.now().minus(3, ChronoUnit.DAYS),
				Instant.now().minus(1, ChronoUnit.HOURS));

		tournamentAutoStatusScheduler.warnOverdueTournamentStart();

		Tournament reloaded = tournamentRepository.findById(t.getId()).orElseThrow();
		assertEquals(TournamentStatus.DRAW_PREVIEW.getValue(), reloaded.getStatus());
		assertEquals(1, warningCount(t.getId()));
	}

	/** TC-SCH-BJ04-002 */
	@Test
	void warnOverdue_startStillInFuture_doesNotWriteWarning() {
		// Nếu code sai: giải chưa tới giờ khai mạc đã bị gắn cảnh báo trễ, BTC tưởng mình chậm tiến độ.
		Tournament t = newTournament(
				TournamentStatus.DRAW_PREVIEW.getValue(),
				Instant.now().minus(1, ChronoUnit.DAYS),
				Instant.now().plus(2, ChronoUnit.DAYS));

		tournamentAutoStatusScheduler.warnOverdueTournamentStart();

		assertEquals(0, warningCount(t.getId()));
		assertEquals(TournamentStatus.DRAW_PREVIEW.getValue(),
				tournamentRepository.findById(t.getId()).orElseThrow().getStatus());
	}

	// ── BJ-01 sendRegistrationClosingSoonReminders ───────────────────────

	/** TC-SCH-BJ01-001 */
	@Test
	void closingSoon_deadlineIn23to24hWindow_publishesReminderEvent() {
		// Nếu code sai: giải sắp hết hạn đăng ký mà người đã ghi danh không được nhắc, dễ bỏ lỡ đóng đơn.
		Tournament t = newTournament(
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
				Instant.now().plus(23, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES),
				Instant.now().plus(10, ChronoUnit.DAYS));
		approvedRegistration(t, TestAccounts.PLAYER1_EMAIL);

		emailReminderScheduler.sendRegistrationClosingSoonReminders();

		assertTrue(published(EmailEventType.TOURNAMENT_REGISTRATION_CLOSING_SOON, t.getId()));
	}

	/** TC-SCH-BJ01-002 */
	@Test
	void closingSoon_deadlineOutsideWindow_doesNotPublishEvent() {
		// Nếu code sai: giải còn rất lâu mới hết hạn vẫn gửi nhắc, người chơi bị làm phiền spl vô cớ.
		Tournament t = newTournament(
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
				Instant.now().plus(5, ChronoUnit.HOURS),
				Instant.now().plus(10, ChronoUnit.DAYS));
		approvedRegistration(t, TestAccounts.PLAYER1_EMAIL);

		emailReminderScheduler.sendRegistrationClosingSoonReminders();

		assertTrue(applicationEvents.stream(MailDomainEvent.class)
				.noneMatch(e -> EmailEventType.TOURNAMENT_REGISTRATION_CLOSING_SOON.equals(e.eventType())
						&& t.getId().equals(e.tournamentId())));
	}

	// ── BJ-02 sendMatchStartingSoonReminders ─────────────────────────────

	private Match pendingMatch(Tournament t, Instant scheduledAt) {
		Registration r1 = approvedRegistration(t, TestAccounts.PLAYER1_EMAIL);
		Registration r2 = approvedRegistration(t, TestAccounts.PLAYER2_EMAIL);
		Participant p1 = participantRepository.save(Participant.builder()
				.tournament(t).registration(r1)
				.participantType(ParticipantType.SINGLE.getValue())
				.displayName("L2 P1")
				.status(ParticipantStatus.ACTIVE.getValue())
				.build());
		Participant p2 = participantRepository.save(Participant.builder()
				.tournament(t).registration(r2)
				.participantType(ParticipantType.SINGLE.getValue())
				.displayName("L2 P2")
				.status(ParticipantStatus.ACTIVE.getValue())
				.build());
		TournamentStage stage = stageRepository.save(TournamentStage.builder()
				.tournament(t).name("L2 KO").stageType("KNOCKOUT").orderNo(1).status("PENDING")
				.build());
		return matchRepository.save(Match.builder()
				.tournament(t).stage(stage).bracketType("KNOCKOUT")
				.roundNo(1).positionNo(1).matchCode("R1-M1")
				.player1(p1).player2(p2)
				.raceTo(5)
				.status(MatchStatus.PENDING.getValue())
				.scheduledAt(scheduledAt)
				.isBye(false).player1Score(0).player2Score(0)
				.version(0L)
				.build());
	}

	/** TC-SCH-BJ02-001 */
	@Test
	void matchStartingSoon_in30to60MinuteWindow_publishesReminderEvent() {
		// Nếu code sai: trận sắp bắt đầu mà hai kỳ thủ không được nhắc, người chơi có thể đến trễ.
		Tournament t = newTournament(
				TournamentStatus.IN_PROGRESS.getValue(),
				Instant.now().minus(2, ChronoUnit.DAYS),
				Instant.now().minus(1, ChronoUnit.HOURS));
		Match match = pendingMatch(t, Instant.now().plus(45, ChronoUnit.MINUTES));

		emailReminderScheduler.sendMatchStartingSoonReminders();

		assertTrue(applicationEvents.stream(MailDomainEvent.class)
				.anyMatch(e -> EmailEventType.MATCH_SCHEDULED_REMINDER.equals(e.eventType())
						&& t.getId().equals(e.tournamentId())
						&& ("MATCH-REMINDER-" + match.getId()).equals(e.entityKey())));
	}

	/** TC-SCH-BJ02-002 */
	@Test
	void matchStartingSoon_outsideWindow_doesNotPublishEvent() {
		// Nếu code sai: trận còn lâu mới đấu đã bị gửi nhắc, hoặc trận đang chờ bị bỏ sót vì quét sai cửa sổ giờ.
		Tournament t = newTournament(
				TournamentStatus.IN_PROGRESS.getValue(),
				Instant.now().minus(2, ChronoUnit.DAYS),
				Instant.now().minus(1, ChronoUnit.HOURS));
		Match match = pendingMatch(t, Instant.now().plus(5, ChronoUnit.HOURS));

		emailReminderScheduler.sendMatchStartingSoonReminders();

		assertTrue(applicationEvents.stream(MailDomainEvent.class)
				.noneMatch(e -> EmailEventType.MATCH_SCHEDULED_REMINDER.equals(e.eventType())
						&& ("MATCH-REMINDER-" + match.getId()).equals(e.entityKey())));
	}
}
