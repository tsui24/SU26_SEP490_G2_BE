package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.DashboardStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.StatusCountItem;
import com.capstone.su26_sep490_g2_be.dto.response.TrendPointResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link DashboardServiceImpl}.
 *
 * <p>Mirrors the <b>DashboardService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-49 (the Owner and Manager overview screen).
 *
 * <p>Every tile on the screen is counted in memory from rows this class fetches, so the tests feed
 * it real entity lists and assert the arithmetic: which statuses count as active, an average that
 * must not divide by zero, a completion rate that has to treat a walkover as played.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · DashboardService — UC-49")
class DashboardServiceImplTest {

	@Mock TournamentRepository tournamentRepository;
	@Mock RegistrationRepository registrationRepository;
	@Mock ParticipantRepository participantRepository;
	@Mock PaymentRepository paymentRepository;
	@Mock MatchRepository matchRepository;
	@Mock GameTypeDefinitionRepository gameTypeDefinitionRepository;

	@InjectMocks DashboardServiceImpl service;

	private static final Long OWNER_ID = 4L;
	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("MM/yyyy");

	private static Instant inMonth(YearMonth month, int dayOfMonth) {
		return month.atDay(dayOfMonth).atStartOfDay(ZONE).toInstant();
	}

	private static Tournament tournament(long id, String status, String gameType,
	                                     BigDecimal prizePool, Long branchId) {
		Tournament tournament = Tournament.builder()
				.id(id).name("T" + id).status(status).gameType(gameType).prizePool(prizePool)
				.branch(branchId == null ? null : Branch.builder().id(branchId).name("B" + branchId).build())
				.build();
		// createdAt lives on BaseEntity, outside the builder
		tournament.setCreatedAt(inMonth(YearMonth.now(ZONE), 1));
		return tournament;
	}

	private static Registration registration(long id, RegistrationStatus status, Instant createdAt) {
		Registration registration = Registration.builder().id(id).status(status.getValue()).build();
		registration.setCreatedAt(createdAt);
		return registration;
	}

	private static Participant participant(long id, ParticipantStatus status) {
		return Participant.builder().id(id).displayName("P" + id).status(status.getValue()).build();
	}

	private static Payment payment(long id, PaymentStatus status, String amount, Instant paidAt, Instant createdAt) {
		return Payment.builder()
				.id(id).status(status.getValue())
				.amount(amount == null ? null : new BigDecimal(amount))
				.paidAt(paidAt).createdAt(createdAt)
				.build();
	}

	private static Match match(long id, String status) {
		return Match.builder().id(id).status(status).build();
	}

	/** Stubs the five fan-out queries so a test only has to supply the lists it cares about. */
	private void givenOwnerData(List<Tournament> tournaments, List<Registration> registrations,
	                            List<Participant> participants, List<Payment> payments, List<Match> matches) {
		when(tournamentRepository.findByCreatedById(OWNER_ID)).thenReturn(tournaments);
		lenient().when(registrationRepository.findByTournamentIdIn(any())).thenReturn(registrations);
		lenient().when(participantRepository.findByTournamentIdIn(any())).thenReturn(participants);
		lenient().when(paymentRepository.findByRegistration_Tournament_IdIn(any())).thenReturn(payments);
		lenient().when(matchRepository.findByTournamentIdIn(any())).thenReturn(matches);
		lenient().when(gameTypeDefinitionRepository.findAll()).thenReturn(List.of());
	}

	private static StatusCountItem statusItem(List<StatusCountItem> items, String status) {
		return items.stream().filter(i -> status.equals(i.getStatus())).findFirst().orElse(null);
	}

	// ══════════════════════════ scoping — UC-49 ══════════════════════════

	@Test
	@DisplayName("TC-001 · A request with no owner is refused rather than treated as a system-wide view")
	void TC001_buildStats_nullOwnerRejected() {
		BusinessException ex = assertThrows(BusinessException.class, () -> service.buildStats(null, null));

		// Reading a null owner as "everything" would show one chain the numbers of another
		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
		verify(tournamentRepository, never()).findByCreatedById(any());
	}

	@Test
	@DisplayName("TC-002 · An owner with no tournament is answered without querying the detail tables")
	void TC002_buildStats_noTournaments() {
		when(tournamentRepository.findByCreatedById(OWNER_ID)).thenReturn(List.of());
		when(gameTypeDefinitionRepository.findAll()).thenReturn(List.of());

		DashboardStatsResponse stats = service.buildStats(OWNER_ID, null);

		assertEquals(0, stats.getTournaments().getTotal());
		assertEquals(BigDecimal.ZERO, stats.getRevenue().getTotalRevenue());
		assertEquals(0.0, stats.getMatches().getCompletionRate());
		// An empty id list would make "IN (...)" match everything, so the queries are skipped
		verify(registrationRepository, never()).findByTournamentIdIn(any());
		verify(paymentRepository, never()).findByRegistration_Tournament_IdIn(any());
		verify(matchRepository, never()).findByTournamentIdIn(any());
	}

	@Test
	@DisplayName("TC-003 · A branch filter narrows the tournaments the tiles are built from")
	void TC003_buildStats_branchFilter() {
		givenOwnerData(List.of(
				tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, 1L),
				tournament(2L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, 2L),
				tournament(3L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(), List.of(), List.of(), List.of());

		DashboardStatsResponse stats = service.buildStats(OWNER_ID, List.of(1L));

		// A Manager sees only the branches they hold, and a chain-wide tournament is not one of them
		assertEquals(1, stats.getTournaments().getTotal());
	}

	@Test
	@DisplayName("TC-004 · No branch filter keeps the whole chain, unbranched tournaments included")
	void TC004_buildStats_noBranchFilter() {
		givenOwnerData(List.of(
				tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, 1L),
				tournament(3L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(), List.of(), List.of(), List.of());

		assertEquals(2, service.buildStats(OWNER_ID, null).getTournaments().getTotal());
	}

	// ══════════════════════════ tournament tiles — UC-49 ══════════════════════════

	@Test
	@DisplayName("TC-005 · Six of the nine statuses count as an active tournament")
	void TC005_buildStats_activeStatuses() {
		givenOwnerData(List.of(
				tournament(1L, TournamentStatus.DRAFT.getValue(), "9_BALL", null, null),
				tournament(2L, TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), "9_BALL", null, null),
				tournament(3L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null),
				tournament(4L, TournamentStatus.COMPLETED.getValue(), "9_BALL", null, null),
				tournament(5L, TournamentStatus.CANCELLED.getValue(), "9_BALL", null, null)),
				List.of(), List.of(), List.of(), List.of());

		var tournaments = service.buildStats(OWNER_ID, null).getTournaments();

		// A draft is not running yet and a completed or cancelled one is over
		assertEquals(2, tournaments.getActive());
		assertEquals(5, tournaments.getTotal());
		assertEquals(1, tournaments.getOpenForRegistration());
		assertEquals(1, tournaments.getInProgress());
		assertEquals(1, tournaments.getCompleted());
		assertEquals(1, tournaments.getCancelled());
	}

	@Test
	@DisplayName("TC-006 · Statuses nobody is in are left out of the breakdown")
	void TC006_buildStats_emptyStatusesOmitted() {
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(), List.of(), List.of(), List.of());

		List<StatusCountItem> byStatus = service.buildStats(OWNER_ID, null).getTournaments().getByStatus();

		// A chart with seven zero-height bars reads as broken rather than as empty
		assertEquals(1, byStatus.size());
		assertEquals(TournamentStatus.IN_PROGRESS.getValue(), byStatus.get(0).getStatus());
		assertEquals("Đang diễn ra", byStatus.get(0).getLabel());
	}

	@Test
	@DisplayName("TC-007 · The prize pool total skips tournaments that offer none")
	void TC007_buildStats_prizePoolIgnoresNulls() {
		givenOwnerData(List.of(
				tournament(1L, TournamentStatus.COMPLETED.getValue(), "9_BALL", new BigDecimal("20000000"), null),
				tournament(2L, TournamentStatus.COMPLETED.getValue(), "9_BALL", null, null),
				tournament(3L, TournamentStatus.COMPLETED.getValue(), "9_BALL", new BigDecimal("5000000"), null)),
				List.of(), List.of(), List.of(), List.of());

		assertEquals(new BigDecimal("25000000"),
				service.buildStats(OWNER_ID, null).getTournaments().getTotalPrizePool());
	}

	@Test
	@DisplayName("TC-008 · Game types are labelled from the catalog and ordered by popularity")
	void TC008_buildStats_gameTypeBreakdown() {
		when(tournamentRepository.findByCreatedById(OWNER_ID)).thenReturn(List.of(
				tournament(1L, TournamentStatus.COMPLETED.getValue(), "9_BALL", null, null),
				tournament(2L, TournamentStatus.COMPLETED.getValue(), "9_BALL", null, null),
				tournament(3L, TournamentStatus.COMPLETED.getValue(), "CAROM_3C", null, null)));
		when(gameTypeDefinitionRepository.findAll()).thenReturn(List.of(
				GameTypeDefinition.builder().code("9_BALL").name("9-Ball").build(),
				GameTypeDefinition.builder().code("CAROM_3C").name("Carom 3 băng").build()));
		lenient().when(registrationRepository.findByTournamentIdIn(any())).thenReturn(List.of());
		lenient().when(participantRepository.findByTournamentIdIn(any())).thenReturn(List.of());
		lenient().when(paymentRepository.findByRegistration_Tournament_IdIn(any())).thenReturn(List.of());
		lenient().when(matchRepository.findByTournamentIdIn(any())).thenReturn(List.of());

		List<StatusCountItem> byGameType = service.buildStats(OWNER_ID, null).getTournaments().getByGameType();

		assertEquals(2, byGameType.size());
		assertEquals("9-Ball", byGameType.get(0).getLabel());
		assertEquals(2, byGameType.get(0).getCount());
		assertEquals("Carom 3 băng", byGameType.get(1).getLabel());
	}

	@Test
	@DisplayName("TC-009 · A game type missing from the catalog is labelled with its own code")
	void TC009_buildStats_unknownGameTypeCode() {
		givenOwnerData(List.of(tournament(1L, TournamentStatus.COMPLETED.getValue(), "10_BALL", null, null)),
				List.of(), List.of(), List.of(), List.of());

		List<StatusCountItem> byGameType = service.buildStats(OWNER_ID, null).getTournaments().getByGameType();

		// A code disabled or deleted after the tournament was created still has to render
		assertEquals("10_BALL", byGameType.get(0).getLabel());
	}

	// ══════════════════════════ registrations and participants — UC-49 ══════════════════════════

	@Test
	@DisplayName("TC-010 · Registration tiles count each status separately")
	void TC010_buildStats_registrationBreakdown() {
		Instant thisMonth = inMonth(YearMonth.now(ZONE), 2);
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(registration(1L, RegistrationStatus.PENDING_PAYMENT, thisMonth),
						registration(2L, RegistrationStatus.APPROVED, thisMonth),
						registration(3L, RegistrationStatus.APPROVED, thisMonth),
						registration(4L, RegistrationStatus.REJECTED, thisMonth),
						registration(5L, RegistrationStatus.CANCELLED, thisMonth)),
				List.of(), List.of(), List.of());

		var registrations = service.buildStats(OWNER_ID, null).getRegistrations();

		assertEquals(5, registrations.getTotal());
		assertEquals(1, registrations.getPending());
		assertEquals(2, registrations.getApproved());
		assertEquals(1, registrations.getRejected());
		assertEquals(1, registrations.getCancelled());
		assertEquals(4, registrations.getByStatus().size());
	}

	@Test
	@DisplayName("TC-011 · Participant tiles separate those still in from those who pulled out")
	void TC011_buildStats_participantBreakdown() {
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(),
				List.of(participant(1L, ParticipantStatus.ACTIVE),
						participant(2L, ParticipantStatus.ACTIVE),
						participant(3L, ParticipantStatus.WITHDRAWN)),
				List.of(), List.of());

		var participants = service.buildStats(OWNER_ID, null).getParticipants();

		assertEquals(3, participants.getTotal());
		assertEquals(2, participants.getActive());
		assertEquals(1, participants.getWithdrawn());
	}

	// ══════════════════════════ revenue — UC-49 ══════════════════════════

	@Test
	@DisplayName("TC-012 · Only settled payments count towards revenue")
	void TC012_buildStats_revenueCountsSuccessOnly() {
		Instant now = Instant.now();
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(), List.of(),
				List.of(payment(1L, PaymentStatus.SUCCESS, "300000", now, now),
						payment(2L, PaymentStatus.SUCCESS, "200000", now, now),
						payment(3L, PaymentStatus.PENDING, "300000", null, now),
						payment(4L, PaymentStatus.FAILED, "300000", null, now)),
				List.of());

		var revenue = service.buildStats(OWNER_ID, null).getRevenue();

		// A pending entry fee is money the chain has not been paid
		assertEquals(new BigDecimal("500000"), revenue.getTotalRevenue());
		assertEquals(2, revenue.getSuccessCount());
		assertEquals(1, revenue.getPendingCount());
		assertEquals(1, revenue.getFailedCount());
		assertEquals(new BigDecimal("250000"), revenue.getAvgTicketValue());
	}

	@Test
	@DisplayName("TC-013 · An owner with no settled payment has an average of zero, not an error")
	void TC013_buildStats_avgTicketWithoutSuccess() {
		Instant now = Instant.now();
		givenOwnerData(List.of(tournament(1L, TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), "9_BALL", null, null)),
				List.of(), List.of(),
				List.of(payment(1L, PaymentStatus.PENDING, "300000", null, now)),
				List.of());

		var revenue = service.buildStats(OWNER_ID, null).getRevenue();

		// The zero guard is what keeps a brand-new chain from dividing by zero on first load
		assertEquals(BigDecimal.ZERO, revenue.getAvgTicketValue());
		assertEquals(BigDecimal.ZERO, revenue.getTotalRevenue());
	}

	@Test
	@DisplayName("TC-014 · A settled payment carrying no amount counts as zero")
	void TC014_buildStats_successWithNullAmount() {
		Instant now = Instant.now();
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(), List.of(),
				List.of(payment(1L, PaymentStatus.SUCCESS, "300000", now, now),
						payment(2L, PaymentStatus.SUCCESS, null, now, now)),
				List.of());

		var revenue = service.buildStats(OWNER_ID, null).getRevenue();

		assertEquals(new BigDecimal("300000"), revenue.getTotalRevenue());
		// The free entry still counts as a settled transaction, which halves the average
		assertEquals(new BigDecimal("150000"), revenue.getAvgTicketValue());
	}

	@Test
	@DisplayName("TC-015 · The average ticket value is rounded to whole đồng")
	void TC015_buildStats_avgTicketRounded() {
		Instant now = Instant.now();
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(), List.of(),
				List.of(payment(1L, PaymentStatus.SUCCESS, "100", now, now),
						payment(2L, PaymentStatus.SUCCESS, "101", now, now),
						payment(3L, PaymentStatus.SUCCESS, "101", now, now)),
				List.of());

		// 302 / 3 = 100.67, rounded half-up to 101 — there is no sub-đồng currency unit
		assertEquals(new BigDecimal("101"),
				service.buildStats(OWNER_ID, null).getRevenue().getAvgTicketValue());
	}

	// ══════════════════════════ matches — UC-49 ══════════════════════════

	@Test
	@DisplayName("TC-016 · A bye and a walkover count as played towards the completion rate")
	void TC016_buildStats_completionRateCountsResolved() {
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(), List.of(), List.of(),
				List.of(match(1L, MatchStatus.COMPLETED.getValue()),
						match(2L, MatchStatus.BYE.getValue()),
						match(3L, MatchStatus.WALKOVER.getValue()),
						match(4L, MatchStatus.IN_PROGRESS.getValue()),
						match(5L, MatchStatus.PENDING.getValue())));

		var matches = service.buildStats(OWNER_ID, null).getMatches();

		// A bye is never played but it is settled, so the bracket is genuinely 60% done
		assertEquals(5, matches.getTotal());
		assertEquals(3, matches.getCompleted());
		assertEquals(1, matches.getInProgress());
		assertEquals(1, matches.getPending());
		assertEquals(60.0, matches.getCompletionRate());
	}

	@Test
	@DisplayName("TC-017 · A status the enum does not know is treated as unplayed")
	void TC017_buildStats_unknownMatchStatus() {
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(), List.of(), List.of(),
				List.of(match(1L, MatchStatus.COMPLETED.getValue()), match(2L, "LEGACY_STATUS")));

		var matches = service.buildStats(OWNER_ID, null).getMatches();

		// A row left behind by an older schema must not take the whole dashboard down
		assertEquals(2, matches.getTotal());
		assertEquals(1, matches.getCompleted());
		assertEquals(50.0, matches.getCompletionRate());
	}

	@Test
	@DisplayName("TC-018 · A tournament with no match drawn yet reports a zero completion rate")
	void TC018_buildStats_noMatches() {
		givenOwnerData(List.of(tournament(1L, TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), "9_BALL", null, null)),
				List.of(), List.of(), List.of(), List.of());

		assertEquals(0.0, service.buildStats(OWNER_ID, null).getMatches().getCompletionRate());
	}

	// ══════════════════════════ trends — UC-49 ══════════════════════════

	@Test
	@DisplayName("TC-019 · Every trend spans the last six months, oldest first")
	void TC019_buildStats_trendWindow() {
		YearMonth current = YearMonth.now(ZONE);
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(registration(1L, RegistrationStatus.APPROVED, inMonth(current, 3))),
				List.of(), List.of(), List.of());

		List<TrendPointResponse> trend = service.buildStats(OWNER_ID, null).getRegistrations().getMonthlyTrend();

		assertEquals(6, trend.size());
		assertEquals(current.minusMonths(5).format(PERIOD), trend.get(0).getPeriod());
		assertEquals(current.format(PERIOD), trend.get(5).getPeriod());
		// A month with nothing in it is still a point, so the chart keeps an even x axis
		assertEquals(0, trend.get(0).getCount());
		assertEquals(1, trend.get(5).getCount());
	}

	@Test
	@DisplayName("TC-020 · Anything older than six months is left off the trend but stays in the total")
	void TC020_buildStats_trendDropsOldRows() {
		YearMonth current = YearMonth.now(ZONE);
		Instant longAgo = inMonth(current.minusMonths(10), 5);
		givenOwnerData(List.of(tournament(1L, TournamentStatus.COMPLETED.getValue(), "9_BALL", null, null)),
				List.of(), List.of(),
				List.of(payment(1L, PaymentStatus.SUCCESS, "300000", longAgo, longAgo),
						payment(2L, PaymentStatus.SUCCESS, "200000", Instant.now(), Instant.now())),
				List.of());

		var revenue = service.buildStats(OWNER_ID, null).getRevenue();

		assertEquals(new BigDecimal("500000"), revenue.getTotalRevenue());
		assertEquals(6, revenue.getMonthlyTrend().size());
		long charted = revenue.getMonthlyTrend().stream().mapToLong(TrendPointResponse::getCount).sum();
		// The tile is all-time while the chart is the last six months — a difference worth knowing
		assertEquals(1, charted);
	}

	@Test
	@DisplayName("TC-021 · A settled payment with no settlement time falls back to when it was raised")
	void TC021_buildStats_revenueTrendFallsBackToCreatedAt() {
		Instant now = Instant.now();
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(), List.of(),
				List.of(payment(1L, PaymentStatus.SUCCESS, "300000", null, now),
						payment(2L, PaymentStatus.SUCCESS, "100000", null, null)),
				List.of());

		List<TrendPointResponse> trend = service.buildStats(OWNER_ID, null).getRevenue().getMonthlyTrend();
		TrendPointResponse thisMonth = trend.get(5);

		// Cash paid at the counter carries no gateway settlement time, so the created time stands in
		assertEquals(1, thisMonth.getCount());
		assertEquals(new BigDecimal("300000"), thisMonth.getAmount());
		// The one with no timestamp at all cannot be placed on the axis, but still counts as revenue
		assertEquals(new BigDecimal("400000"),
				service.buildStats(OWNER_ID, null).getRevenue().getTotalRevenue());
	}

	@Test
	@DisplayName("TC-022 · A month with no revenue is charted as zero rather than left out")
	void TC022_buildStats_revenueTrendFillsEmptyMonths() {
		givenOwnerData(List.of(tournament(1L, TournamentStatus.IN_PROGRESS.getValue(), "9_BALL", null, null)),
				List.of(), List.of(), List.of(), List.of());

		List<TrendPointResponse> trend = service.buildStats(OWNER_ID, null).getRevenue().getMonthlyTrend();

		assertEquals(6, trend.size());
		assertTrue(trend.stream().allMatch(p -> BigDecimal.ZERO.equals(p.getAmount())));
	}
}
