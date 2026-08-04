package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.AnalyticsOverviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.GameTypeBreakdownItem;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PaymentHistoryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerGrowthResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerLeaderboardItem;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RevenueBreakdownResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SocialEngagementResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentAnalyticsDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentPerformanceItem;
import com.capstone.su26_sep490_g2_be.dto.response.TransactionStatsResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.FacebookPost;
import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.FacebookPostRepository;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentFormatDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link AnalyticsServiceImpl}.
 *
 * <p>Mirrors the <b>AnalyticsService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-50 (owner analytics dashboard), UC-51 (revenue and
 * transaction reports).
 *
 * <p>Every figure on the analytics screen is computed in memory from rows this class fetches, so
 * the tests feed it real entity graphs and assert the arithmetic: a growth percentage against an
 * empty previous period, a fill rate that must skip tournaments with no capacity, a net profit
 * that deliberately uses all-time revenue rather than the filtered range.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · AnalyticsService — UC-50, UC-51")
class AnalyticsServiceImplTest {

	@Mock TournamentRepository tournamentRepository;
	@Mock RegistrationRepository registrationRepository;
	@Mock ParticipantRepository participantRepository;
	@Mock PaymentRepository paymentRepository;
	@Mock MatchRepository matchRepository;
	@Mock TournamentResultRepository tournamentResultRepository;
	@Mock FacebookPostRepository facebookPostRepository;
	@Mock GameTypeDefinitionRepository gameTypeDefinitionRepository;
	@Mock TournamentFormatDefinitionRepository tournamentFormatDefinitionRepository;

	@InjectMocks AnalyticsServiceImpl service;

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final Long OWNER_ID = 7L;
	private static final Long BRANCH_ID = 2L;

	private static final Instant NOW = Instant.now();
	private static final Instant FROM = NOW.minus(30, ChronoUnit.DAYS);
	private static final Instant IN_RANGE = NOW.minus(10, ChronoUnit.DAYS);
	private static final Instant BEFORE_RANGE = NOW.minus(45, ChronoUnit.DAYS);

	// ══════════════════════════ fixtures ══════════════════════════

	private static Branch branch(Long id, String name) {
		return Branch.builder().id(id).name(name).build();
	}

	private static Tournament tournament(Long id, String name, Branch branch, Integer maxParticipants,
										 BigDecimal prizePool, Instant startAt) {
		Tournament t = Tournament.builder()
				.id(id).name(name).branch(branch).gameType("EIGHT_BALL").format("SINGLE_ELIMINATION")
				.status(TournamentStatus.COMPLETED.getValue())
				.maxParticipants(maxParticipants).prizePool(prizePool)
				.entryFee(new BigDecimal("200000"))
				.startAt(startAt)
				.createdBy(User.builder().id(OWNER_ID).build())
				.build();
		t.setCreatedAt(startAt);
		return t;
	}

	private static Payment payment(Long id, Tournament t, String status, String amount, Instant paidAt) {
		Payment p = Payment.builder()
				.id(id).amount(new BigDecimal(amount)).paymentMethod("PAYOS").status(status)
				.registration(Registration.builder().id(id).tournament(t)
						.playerFullName("Nguyễn Văn A").build())
				.paidAt(paidAt)
				.build();
		p.setCreatedAt(paidAt);
		return p;
	}

	private static Participant participant(Long id, Tournament t, String status) {
		return Participant.builder().id(id).tournament(t).displayName("VĐV " + id).status(status).build();
	}

	private static Registration registration(Long id, Tournament t, Long userId, String status, Instant createdAt) {
		Registration r = Registration.builder()
				.id(id).tournament(t).status(status)
				.user(userId != null ? User.builder().id(userId).email("p" + userId + "@btms.vn").build() : null)
				.build();
		r.setCreatedAt(createdAt);
		return r;
	}

	private static Match match(Long id, Tournament t, String status) {
		return Match.builder().id(id).tournament(t).status(status).roundNo(1).positionNo(id.intValue()).build();
	}

	private static FacebookPost post(Long id, Tournament t, int likes, int comments, int shares, int reach,
									 Instant postedAt) {
		return FacebookPost.builder()
				.id(id).tournament(t).likesCount(likes).commentsCount(comments)
				.sharesCount(shares).reach(reach).postedAt(postedAt).build();
	}

	private static TournamentResult result(Long id, Tournament t, Long userId, String playerName,
										   int finalRank, String prize, int points, Instant recordedAt) {
		return TournamentResult.builder()
				.id(id).tournament(t)
				.participant(Participant.builder().id(id).displayName(playerName)
						.registration(Registration.builder().id(id)
								.user(userId != null ? User.builder().id(userId).build() : null).build())
						.build())
				.finalRank(finalRank).prizeAmount(new BigDecimal(prize)).pointsEarned(points)
				.recordedAt(recordedAt)
				.build();
	}

	/** Owner owns the given tournaments; every scoped fetch defaults to empty unless overridden. */
	private void givenOwnerTournaments(List<Tournament> tournaments) {
		lenient().when(tournamentRepository.findByCreatedById(OWNER_ID)).thenReturn(tournaments);
		lenient().when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of());
		lenient().when(participantRepository.findByTournamentIdIn(anyList())).thenReturn(List.of());
		lenient().when(registrationRepository.findByTournamentIdIn(anyList())).thenReturn(List.of());
		lenient().when(matchRepository.findByTournamentIdIn(anyList())).thenReturn(List.of());
		lenient().when(tournamentResultRepository.findByTournamentIdIn(anyList())).thenReturn(List.of());
		lenient().when(facebookPostRepository.findByTournamentIdIn(anyList())).thenReturn(List.of());
	}

	// ══════════════════════════ ownership guard ══════════════════════════

	@Test
	@DisplayName("TC-001 · Analytics cannot be read without saying whose they are")
	void TC001_ownerTournaments_ownerIdRequired() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.buildOverview(null, FROM, NOW, null));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode(),
				"a null owner must never be read as \"no filter\" — it would expose every venue's takings");
		verify(tournamentRepository, never()).findByCreatedById(any());
	}

	@Test
	@DisplayName("TC-002 · A branch filter narrows the tournaments the figures are built from")
	void TC002_ownerTournaments_branchFilter() {
		Tournament mine = tournament(1L, "Giải Quận 1", branch(BRANCH_ID, "Chi nhánh Quận 1"), 16, null, IN_RANGE);
		Tournament other = tournament(2L, "Giải Quận 7", branch(9L, "Chi nhánh Quận 7"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(mine, other));

		AnalyticsOverviewResponse response = service.buildOverview(OWNER_ID, FROM, NOW, List.of(BRANCH_ID));

		assertEquals(1, response.getTotalTournaments());
		assertEquals(1, response.getBranchCount());
	}

	@Test
	@DisplayName("TC-003 · A tournament with no branch is left out of a branch-filtered view")
	void TC003_ownerTournaments_branchlessExcludedWhenFiltering() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải không chi nhánh", null, 16, null, IN_RANGE)));

		AnalyticsOverviewResponse response = service.buildOverview(OWNER_ID, FROM, NOW, List.of(BRANCH_ID));

		assertEquals(0, response.getTotalTournaments());
	}

	// ══════════════════════════ buildOverview ══════════════════════════

	@Test
	@DisplayName("TC-004 · The overview totals the takings of the period")
	void TC004_buildOverview_totalsRevenue() {
		Tournament t = tournament(1L, "Summer Open", branch(BRANCH_ID, "Chi nhánh Quận 1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of(
				payment(1L, t, PaymentStatus.SUCCESS.getValue(), "200000", IN_RANGE),
				payment(2L, t, PaymentStatus.SUCCESS.getValue(), "300000", IN_RANGE),
				payment(3L, t, PaymentStatus.PENDING.getValue(), "500000", IN_RANGE)));

		AnalyticsOverviewResponse response = service.buildOverview(OWNER_ID, FROM, NOW, null);

		assertEquals(new BigDecimal("500000"), response.getTotalRevenue(),
				"a pending payment is not money in the till");
		assertEquals("Summer Open", response.getTopTournamentName());
		assertEquals("Chi nhánh Quận 1", response.getTopBranchName());
	}

	@Test
	@DisplayName("TC-005 · An owner with no tournaments sees zeroes rather than an error")
	void TC005_buildOverview_noTournaments() {
		givenOwnerTournaments(List.of());

		AnalyticsOverviewResponse response = service.buildOverview(OWNER_ID, FROM, NOW, null);

		assertEquals(BigDecimal.ZERO, response.getTotalRevenue());
		assertEquals(0, response.getTotalTournaments());
		assertEquals(0.0, response.getAvgFillRatePct());
		assertNull(response.getTopTournamentName());
	}

	@Test
	@DisplayName("TC-006 · A first period of takings is reported as full growth")
	void TC006_buildOverview_growthFromNothing() {
		Tournament t = tournament(1L, "Summer Open", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of(
				payment(1L, t, PaymentStatus.SUCCESS.getValue(), "200000", IN_RANGE)));

		AnalyticsOverviewResponse response = service.buildOverview(OWNER_ID, FROM, NOW, null);

		assertEquals(100.0, response.getRevenueGrowthPct(),
				"there is nothing to divide by, so the first period counts as +100%");
	}

	@Test
	@DisplayName("TC-007 · Two empty periods report no growth at all")
	void TC007_buildOverview_growthBothEmpty() {
		givenOwnerTournaments(List.of(tournament(1L, "Summer Open", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE)));

		AnalyticsOverviewResponse response = service.buildOverview(OWNER_ID, FROM, NOW, null);

		assertEquals(0.0, response.getRevenueGrowthPct());
	}

	@Test
	@DisplayName("TC-008 · Takings that fell against the previous period show a negative growth")
	void TC008_buildOverview_negativeGrowth() {
		Tournament t = tournament(1L, "Summer Open", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of(
				payment(1L, t, PaymentStatus.SUCCESS.getValue(), "100000", IN_RANGE),
				payment(2L, t, PaymentStatus.SUCCESS.getValue(), "400000", BEFORE_RANGE)));

		AnalyticsOverviewResponse response = service.buildOverview(OWNER_ID, FROM, NOW, null);

		assertEquals(new BigDecimal("100000"), response.getTotalRevenue());
		assertEquals(new BigDecimal("400000"), response.getRevenuePrevPeriod());
		assertEquals(-75.0, response.getRevenueGrowthPct());
	}

	@Test
	@DisplayName("TC-009 · The fill rate skips tournaments that declare no capacity")
	void TC009_buildOverview_fillRateSkipsUncappedTournaments() {
		Tournament capped = tournament(1L, "Có giới hạn", branch(BRANCH_ID, "CN1"), 10, null, IN_RANGE);
		Tournament uncapped = tournament(2L, "Không giới hạn", branch(BRANCH_ID, "CN1"), null, null, IN_RANGE);
		givenOwnerTournaments(List.of(capped, uncapped));
		when(participantRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				participant(1L, capped, ParticipantStatus.ACTIVE.getValue()),
				participant(2L, capped, ParticipantStatus.ACTIVE.getValue()),
				participant(3L, capped, ParticipantStatus.WITHDRAWN.getValue())));

		AnalyticsOverviewResponse response = service.buildOverview(OWNER_ID, FROM, NOW, null);

		assertEquals(20.0, response.getAvgFillRatePct(),
				"2 active of 10 places; the uncapped tournament cannot have a percentage and is left out");
	}

	@Test
	@DisplayName("TC-010 · Unique players are counted once however many times they entered")
	void TC010_buildOverview_uniquePlayers() {
		Tournament t = tournament(1L, "Summer Open", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(registrationRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				registration(1L, t, 11L, RegistrationStatus.APPROVED.getValue(), IN_RANGE),
				registration(2L, t, 11L, RegistrationStatus.APPROVED.getValue(), IN_RANGE),
				registration(3L, t, 12L, RegistrationStatus.APPROVED.getValue(), IN_RANGE),
				registration(4L, t, null, RegistrationStatus.APPROVED.getValue(), IN_RANGE),
				registration(5L, t, 13L, RegistrationStatus.APPROVED.getValue(), BEFORE_RANGE)));

		AnalyticsOverviewResponse response = service.buildOverview(OWNER_ID, FROM, NOW, null);

		assertEquals(2L, response.getTotalUniquePlayers(),
				"an imported entry has no account and an out-of-range entry belongs to another period");
	}

	@Test
	@DisplayName("TC-011 · Branches are counted once each")
	void TC011_buildOverview_branchCount() {
		Branch cn1 = branch(BRANCH_ID, "Chi nhánh Quận 1");
		givenOwnerTournaments(List.of(
				tournament(1L, "Giải A", cn1, 16, null, IN_RANGE),
				tournament(2L, "Giải B", cn1, 16, null, IN_RANGE),
				tournament(3L, "Giải C", branch(9L, "Chi nhánh Quận 7"), 16, null, IN_RANGE)));

		assertEquals(2, service.buildOverview(OWNER_ID, FROM, NOW, null).getBranchCount());
	}

	// ══════════════════════════ buildRevenueBreakdown ══════════════════════════

	@Test
	@DisplayName("TC-012 · Revenue is broken down by tournament, branch and payment method")
	void TC012_buildRevenueBreakdown_allDimensions() {
		Tournament a = tournament(1L, "Giải A", branch(BRANCH_ID, "Chi nhánh Quận 1"), 16, null, IN_RANGE);
		Tournament b = tournament(2L, "Giải B", branch(9L, "Chi nhánh Quận 7"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(a, b));
		when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of(
				payment(1L, a, PaymentStatus.SUCCESS.getValue(), "300000", IN_RANGE),
				payment(2L, b, PaymentStatus.SUCCESS.getValue(), "100000", IN_RANGE)));

		RevenueBreakdownResponse response = service.buildRevenueBreakdown(OWNER_ID, FROM, NOW, "month", null);

		assertEquals(2, response.getByTournament().size());
		assertEquals("Giải A", response.getByTournament().get(0).getLabel(), "the biggest earner comes first");
		assertEquals(new BigDecimal("300000"), response.getByTournament().get(0).getAmount());
		assertEquals("Chi nhánh Quận 1", response.getByBranch().get(0).getLabel());
		assertEquals("PAYOS", response.getByPaymentMethod().get(0).getStatus());
		assertEquals(2L, response.getByPaymentMethod().get(0).getCount());
	}

	@Test
	@DisplayName("TC-013 · A tournament with no branch is grouped under an explicit label")
	void TC013_buildRevenueBreakdown_branchlessLabel() {
		Tournament t = tournament(1L, "Giải lẻ", null, 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of(
				payment(1L, t, PaymentStatus.SUCCESS.getValue(), "100000", IN_RANGE)));

		RevenueBreakdownResponse response = service.buildRevenueBreakdown(OWNER_ID, FROM, NOW, "month", null);

		assertEquals("Không rõ chi nhánh", response.getByBranch().get(0).getLabel(),
				"an unnamed group is clearer than a blank row on the chart");
	}

	@Test
	@DisplayName("TC-014 · A daily breakdown produces one point per day of the range")
	void TC014_buildRevenueBreakdown_dailyTrend() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		Instant from = NOW.minus(2, ChronoUnit.DAYS);

		RevenueBreakdownResponse response = service.buildRevenueBreakdown(OWNER_ID, from, NOW, "day", null);

		assertEquals(3, response.getTrend().size(), "three calendar days inclusive");
		assertTrue(response.getTrend().stream().allMatch(p -> p.getPeriod().matches("\\d{2}/\\d{2}")));
	}

	@Test
	@DisplayName("TC-015 · An unrecognised granularity falls back to months")
	void TC015_buildRevenueBreakdown_unknownGranularity() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE)));

		RevenueBreakdownResponse response = service.buildRevenueBreakdown(OWNER_ID, FROM, NOW, "quarter", null);

		assertTrue(response.getTrend().stream().allMatch(p -> p.getPeriod().matches("\\d{2}/\\d{4}")));
	}

	@Test
	@DisplayName("TC-016 · A weekly breakdown buckets by the Monday of each week")
	void TC016_buildRevenueBreakdown_weeklyTrend() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE)));

		RevenueBreakdownResponse response = service.buildRevenueBreakdown(
				OWNER_ID, NOW.minus(14, ChronoUnit.DAYS), NOW, "week", null);

		assertTrue(response.getTrend().size() >= 2 && response.getTrend().size() <= 4);
	}

	// ══════════════════════════ buildTournamentPerformance ══════════════════════════

	@Test
	@DisplayName("TC-017 · Performance lists every tournament, not only those in the period")
	void TC017_buildTournamentPerformance_listsEveryTournament() {
		Tournament inRange = tournament(1L, "Giải trong kỳ", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		Tournament older = tournament(2L, "Giải cũ", branch(BRANCH_ID, "CN1"), 16, null, BEFORE_RANGE);
		givenOwnerTournaments(List.of(inRange, older));

		List<TournamentPerformanceItem> items = service.buildTournamentPerformance(OWNER_ID, FROM, NOW, null);

		assertEquals(2, items.size(),
				"the table is a roster of the owner's tournaments; only the revenue column is scoped to the period");
	}

	@Test
	@DisplayName("TC-018 · Net profit is takings across all time minus the prize money")
	void TC018_buildTournamentPerformance_netProfitUsesAllTimeRevenue() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, new BigDecimal("500000"), IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of(
				payment(1L, t, PaymentStatus.SUCCESS.getValue(), "300000", IN_RANGE),
				payment(2L, t, PaymentStatus.SUCCESS.getValue(), "400000", BEFORE_RANGE)));

		TournamentPerformanceItem item = service.buildTournamentPerformance(OWNER_ID, FROM, NOW, null).get(0);

		assertEquals(new BigDecimal("300000"), item.getRevenue(), "the revenue column follows the filter");
		assertEquals(new BigDecimal("200000"), item.getNetProfit(),
				"prize money is a one-off cost of the whole tournament, so profit uses all-time takings");
	}

	@Test
	@DisplayName("TC-019 · The completion rate counts matches that reached a result")
	void TC019_buildTournamentPerformance_completionRate() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(matchRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				match(1L, t, MatchStatus.COMPLETED.getValue()),
				match(2L, t, MatchStatus.WALKOVER.getValue()),
				match(3L, t, MatchStatus.BYE.getValue()),
				match(4L, t, MatchStatus.PENDING.getValue())));

		TournamentPerformanceItem item = service.buildTournamentPerformance(OWNER_ID, FROM, NOW, null).get(0);

		assertEquals(75.0, item.getCompletionRatePct(),
				"a walkover and a bye are resolved results, not unplayed matches");
	}

	@Test
	@DisplayName("TC-020 · A tournament with no capacity reports no fill rate at all")
	void TC020_buildTournamentPerformance_nullFillRate() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), null, null, IN_RANGE)));

		TournamentPerformanceItem item = service.buildTournamentPerformance(OWNER_ID, FROM, NOW, null).get(0);

		assertNull(item.getFillRatePct(), "null and 0% mean different things on the dashboard");
		assertEquals(0.0, item.getCompletionRatePct());
	}

	@Test
	@DisplayName("TC-021 · A status the enum does not know still renders as itself")
	void TC021_buildTournamentPerformance_unknownStatusLabel() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		t.setStatus("LEGACY_STATUS");
		givenOwnerTournaments(List.of(t));

		TournamentPerformanceItem item = service.buildTournamentPerformance(OWNER_ID, FROM, NOW, null).get(0);

		assertEquals("LEGACY_STATUS", item.getStatusLabel());
	}

	@Test
	@DisplayName("TC-022 · The performance table is ordered by takings")
	void TC022_buildTournamentPerformance_sortedByRevenue() {
		Tournament small = tournament(1L, "Giải nhỏ", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		Tournament big = tournament(2L, "Giải lớn", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(small, big));
		when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of(
				payment(1L, small, PaymentStatus.SUCCESS.getValue(), "100000", IN_RANGE),
				payment(2L, big, PaymentStatus.SUCCESS.getValue(), "900000", IN_RANGE)));

		List<TournamentPerformanceItem> items = service.buildTournamentPerformance(OWNER_ID, FROM, NOW, null);

		assertEquals("Giải lớn", items.get(0).getName());
	}

	// ══════════════════════════ buildPlayerLeaderboard ══════════════════════════

	@Test
	@DisplayName("TC-023 · The leaderboard aggregates a player's results across tournaments")
	void TC023_buildPlayerLeaderboard_aggregatesPerPlayer() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(tournamentResultRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				result(1L, t, 11L, "Nguyễn Văn A", 1, "500000", 100, IN_RANGE),
				result(2L, t, 11L, "Nguyễn Văn A", 3, "100000", 40, IN_RANGE),
				result(3L, t, 12L, "Trần Thị B", 2, "200000", 60, IN_RANGE)));

		List<PlayerLeaderboardItem> items = service.buildPlayerLeaderboard(OWNER_ID, FROM, NOW, null);

		assertEquals(2, items.size());
		PlayerLeaderboardItem top = items.get(0);
		assertEquals(11L, top.getUserId());
		assertEquals(2, top.getTournamentsPlayed());
		assertEquals(1, top.getChampionCount());
		assertEquals(2, top.getTop3Count());
		assertEquals(new BigDecimal("600000"), top.getTotalPrizeAmount());
		assertEquals(140L, top.getTotalPoints());
	}

	@Test
	@DisplayName("TC-024 · A result with no account behind it is left off the leaderboard")
	void TC024_buildPlayerLeaderboard_skipsAccountlessResults() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(tournamentResultRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				result(1L, t, null, "Khách vãng lai", 1, "500000", 100, IN_RANGE)));

		assertTrue(service.buildPlayerLeaderboard(OWNER_ID, FROM, NOW, null).isEmpty(),
				"an imported player has no account to rank");
	}

	@Test
	@DisplayName("TC-025 · Results recorded outside the period do not count")
	void TC025_buildPlayerLeaderboard_respectsRange() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(tournamentResultRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				result(1L, t, 11L, "Nguyễn Văn A", 1, "500000", 100, BEFORE_RANGE)));

		assertTrue(service.buildPlayerLeaderboard(OWNER_ID, FROM, NOW, null).isEmpty());
	}

	@Test
	@DisplayName("TC-026 · The leaderboard is capped at ten players")
	void TC026_buildPlayerLeaderboard_topTenOnly() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		List<TournamentResult> results = new java.util.ArrayList<>();
		for (long i = 1; i <= 15; i++) {
			results.add(result(i, t, 100 + i, "VĐV " + i, (int) i, String.valueOf(i * 10000), (int) i, IN_RANGE));
		}
		when(tournamentResultRepository.findByTournamentIdIn(anyList())).thenReturn(results);

		List<PlayerLeaderboardItem> items = service.buildPlayerLeaderboard(OWNER_ID, FROM, NOW, null);

		assertEquals(10, items.size());
		assertEquals(new BigDecimal("150000"), items.get(0).getTotalPrizeAmount(), "ordered by prize money");
	}

	// ══════════════════════════ buildSocialEngagement ══════════════════════════

	@Test
	@DisplayName("TC-027 · Social figures are totalled across the period's posts")
	void TC027_buildSocialEngagement_totals() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(facebookPostRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				post(1L, t, 10, 2, 1, 500, IN_RANGE),
				post(2L, t, 20, 4, 3, 1500, IN_RANGE),
				post(3L, t, 99, 99, 99, 9999, BEFORE_RANGE)));

		SocialEngagementResponse response = service.buildSocialEngagement(OWNER_ID, FROM, NOW, null);

		assertEquals(2, response.getTotalPosts());
		assertEquals(30L, response.getTotalLikes());
		assertEquals(6L, response.getTotalComments());
		assertEquals(4L, response.getTotalShares());
		assertEquals(2000L, response.getTotalReach());
		assertEquals(1500, response.getTopPostReach());
		assertEquals("Giải A", response.getTopPostTournamentName());
	}

	@Test
	@DisplayName("TC-028 · Posts with no figures recorded count as zero")
	void TC028_buildSocialEngagement_nullCountsAreZero() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(facebookPostRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				FacebookPost.builder().id(1L).tournament(t).postedAt(IN_RANGE).build()));

		SocialEngagementResponse response = service.buildSocialEngagement(OWNER_ID, FROM, NOW, null);

		assertEquals(1, response.getTotalPosts());
		assertEquals(0L, response.getTotalLikes());
		assertEquals(0, response.getTopPostReach(), "a post whose stats have not synced yet is not an error");
	}

	@Test
	@DisplayName("TC-029 · A period with no posts reports nothing rather than failing")
	void TC029_buildSocialEngagement_noPosts() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE)));

		SocialEngagementResponse response = service.buildSocialEngagement(OWNER_ID, FROM, NOW, null);

		assertEquals(0, response.getTotalPosts());
		assertNull(response.getTopPostTournamentName());
	}

	// ══════════════════════════ buildRegistrationFunnel ══════════════════════════

	@Test
	@DisplayName("TC-030 · The funnel counts entries by their status")
	void TC030_buildRegistrationFunnel_countsByStatus() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(registrationRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				registration(1L, t, 11L, RegistrationStatus.APPROVED.getValue(), IN_RANGE),
				registration(2L, t, 12L, RegistrationStatus.APPROVED.getValue(), IN_RANGE),
				registration(3L, t, 13L, RegistrationStatus.PENDING_PAYMENT.getValue(), IN_RANGE),
				registration(4L, t, 14L, RegistrationStatus.REJECTED.getValue(), IN_RANGE),
				registration(5L, t, 15L, RegistrationStatus.CANCELLED.getValue(), IN_RANGE)));

		RegistrationStatsResponse response = service.buildRegistrationFunnel(OWNER_ID, FROM, NOW, "month", null);

		assertEquals(5, response.getTotal());
		assertEquals(2L, response.getApproved());
		assertEquals(1L, response.getPending());
		assertEquals(1L, response.getRejected());
		assertEquals(1L, response.getCancelled());
		assertEquals(4, response.getByStatus().size(), "statuses nobody reached are left off the chart");
	}

	@Test
	@DisplayName("TC-031 · A funnel with no entries has no status rows at all")
	void TC031_buildRegistrationFunnel_empty() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE)));

		RegistrationStatsResponse response = service.buildRegistrationFunnel(OWNER_ID, FROM, NOW, "month", null);

		assertEquals(0, response.getTotal());
		assertTrue(response.getByStatus().isEmpty());
		assertTrue(response.getMonthlyTrend().size() >= 1, "the trend still spans the requested period");
	}

	// ══════════════════════════ buildGameTypeBreakdown ══════════════════════════

	@Test
	@DisplayName("TC-032 · Game types are labelled from the admin catalogue")
	void TC032_buildGameTypeBreakdown_usesCatalogueLabel() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 10, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(gameTypeDefinitionRepository.findAll()).thenReturn(List.of(
				GameTypeDefinition.builder().code("EIGHT_BALL").name("8 bi").build()));
		when(participantRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				participant(1L, t, ParticipantStatus.ACTIVE.getValue()),
				participant(2L, t, ParticipantStatus.ACTIVE.getValue())));
		when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of(
				payment(1L, t, PaymentStatus.SUCCESS.getValue(), "400000", IN_RANGE)));

		List<GameTypeBreakdownItem> items = service.buildGameTypeBreakdown(OWNER_ID, FROM, NOW, null);

		assertEquals(1, items.size());
		assertEquals("8 bi", items.get(0).getLabel());
		assertEquals(1, items.get(0).getTournamentCount());
		assertEquals(new BigDecimal("400000"), items.get(0).getTotalRevenue());
		assertEquals(20.0, items.get(0).getAvgFillRatePct());
	}

	@Test
	@DisplayName("TC-033 · A game type missing from the catalogue falls back to its code")
	void TC033_buildGameTypeBreakdown_fallsBackToCode() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 10, null, IN_RANGE)));
		when(gameTypeDefinitionRepository.findAll()).thenReturn(List.of());

		List<GameTypeBreakdownItem> items = service.buildGameTypeBreakdown(OWNER_ID, FROM, NOW, null);

		assertEquals("EIGHT_BALL", items.get(0).getLabel());
	}

	// ══════════════════════════ buildPlayerGrowth ══════════════════════════

	@Test
	@DisplayName("TC-034 · A player counts as new on the date they first entered anything")
	void TC034_buildPlayerGrowth_newPlayersUseFirstEntry() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(registrationRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				registration(1L, t, 11L, RegistrationStatus.APPROVED.getValue(), BEFORE_RANGE),
				registration(2L, t, 11L, RegistrationStatus.APPROVED.getValue(), IN_RANGE),
				registration(3L, t, 12L, RegistrationStatus.APPROVED.getValue(), IN_RANGE)));

		PlayerGrowthResponse response = service.buildPlayerGrowth(OWNER_ID, FROM, NOW, "month", null);

		long newPlayers = response.getNewPlayersTrend().stream().mapToLong(p -> p.getCount()).sum();
		assertEquals(1L, newPlayers, "player 11 first appeared before the period, so only 12 is new");
		assertEquals(2L, response.getActivePlayerCount());
	}

	@Test
	@DisplayName("TC-035 · The repeat rate is the share of active players who came back")
	void TC035_buildPlayerGrowth_repeatRate() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(registrationRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				registration(1L, t, 11L, RegistrationStatus.APPROVED.getValue(), IN_RANGE),
				registration(2L, t, 11L, RegistrationStatus.APPROVED.getValue(), IN_RANGE),
				registration(3L, t, 12L, RegistrationStatus.APPROVED.getValue(), IN_RANGE)));

		PlayerGrowthResponse response = service.buildPlayerGrowth(OWNER_ID, FROM, NOW, "month", null);

		assertEquals(2L, response.getActivePlayerCount());
		assertEquals(1L, response.getReturningPlayerCount());
		assertEquals(50.0, response.getRepeatPlayerRatePct());
	}

	@Test
	@DisplayName("TC-036 · A period with no active players reports a zero repeat rate")
	void TC036_buildPlayerGrowth_noActivePlayers() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE)));

		PlayerGrowthResponse response = service.buildPlayerGrowth(OWNER_ID, FROM, NOW, "month", null);

		assertEquals(0L, response.getActivePlayerCount());
		assertEquals(0.0, response.getRepeatPlayerRatePct(), "no division by zero on an empty period");
	}

	// ══════════════════════════ buildTournamentDetail ══════════════════════════

	@Test
	@DisplayName("TC-037 · Drilling into a tournament that does not exist")
	void TC037_buildTournamentDetail_notFound() {
		when(tournamentRepository.findById(1L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.buildTournamentDetail(OWNER_ID, 1L, null));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-038 · An owner may not drill into somebody else's tournament")
	void TC038_buildTournamentDetail_otherOwnerDenied() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		t.setCreatedBy(User.builder().id(999L).build());
		when(tournamentRepository.findById(1L)).thenReturn(Optional.of(t));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.buildTournamentDetail(OWNER_ID, 1L, null));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-039 · A manager may not drill into a tournament outside their branches")
	void TC039_buildTournamentDetail_branchDenied() {
		when(tournamentRepository.findById(1L)).thenReturn(Optional.of(
				tournament(1L, "Giải A", branch(9L, "Chi nhánh Quận 7"), 16, null, IN_RANGE)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.buildTournamentDetail(OWNER_ID, 1L, List.of(BRANCH_ID)));

		assertEquals(ErrorCode.BRANCH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-040 · A branchless tournament is refused to a branch-scoped manager")
	void TC040_buildTournamentDetail_branchlessDeniedToManager() {
		when(tournamentRepository.findById(1L)).thenReturn(Optional.of(
				tournament(1L, "Giải lẻ", null, 16, null, IN_RANGE)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.buildTournamentDetail(OWNER_ID, 1L, List.of(BRANCH_ID)));

		assertEquals(ErrorCode.BRANCH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-041 · The drill-down carries every statistic block of the tournament")
	void TC041_buildTournamentDetail_allBlocks() {
		Tournament t = tournament(1L, "Summer Open", branch(BRANCH_ID, "Chi nhánh Quận 1"), 10,
				new BigDecimal("100000"), IN_RANGE);
		when(tournamentRepository.findById(1L)).thenReturn(Optional.of(t));
		when(registrationRepository.findByTournamentIdIn(List.of(1L))).thenReturn(List.of(
				registration(1L, t, 11L, RegistrationStatus.APPROVED.getValue(), IN_RANGE),
				registration(2L, t, 12L, RegistrationStatus.PENDING_PAYMENT.getValue(), IN_RANGE)));
		when(participantRepository.findByTournamentIdIn(List.of(1L))).thenReturn(List.of(
				participant(1L, t, ParticipantStatus.ACTIVE.getValue()),
				participant(2L, t, ParticipantStatus.WITHDRAWN.getValue())));
		when(matchRepository.findByTournamentIdIn(List.of(1L))).thenReturn(List.of(
				match(1L, t, MatchStatus.COMPLETED.getValue()),
				match(2L, t, MatchStatus.PENDING.getValue())));
		when(paymentRepository.findByRegistration_Tournament_IdIn(List.of(1L))).thenReturn(List.of(
				payment(1L, t, PaymentStatus.SUCCESS.getValue(), "200000", IN_RANGE)));
		when(facebookPostRepository.findByTournamentIdOrderByPostedAtDesc(1L)).thenReturn(List.of(
				post(1L, t, 10, 2, 1, 800, IN_RANGE)));
		when(gameTypeDefinitionRepository.findById("EIGHT_BALL")).thenReturn(Optional.of(
				GameTypeDefinition.builder().code("EIGHT_BALL").name("8 bi").build()));
		when(tournamentFormatDefinitionRepository.findById("SINGLE_ELIMINATION")).thenReturn(Optional.of(
				TournamentFormatDefinition.builder().code("SINGLE_ELIMINATION").name("Loại trực tiếp").build()));

		TournamentAnalyticsDetailResponse response = service.buildTournamentDetail(OWNER_ID, 1L, null);

		assertEquals("8 bi", response.getGameTypeLabel());
		assertEquals("Loại trực tiếp", response.getFormatLabel());
		assertEquals(2, response.getRegistrationStats().getTotal());
		assertEquals(1L, response.getParticipantStats().getActive());
		assertEquals(1L, response.getParticipantStats().getWithdrawn());
		assertEquals(2, response.getMatchStats().getTotal());
		assertEquals(50.0, response.getMatchStats().getCompletionRate());
		assertEquals(new BigDecimal("200000"), response.getTransactionStats().getTotalAmount());
		assertEquals(1, response.getSocial().getTotalPosts());
		assertEquals(10.0, response.getFillRatePct(), "1 active player of 10 places");
		assertEquals(new BigDecimal("100000"), response.getNetProfit(), "200,000 taken less 100,000 in prizes");
	}

	@Test
	@DisplayName("TC-042 · A game type or format missing from the catalogue falls back to its code")
	void TC042_buildTournamentDetail_labelFallback() {
		Tournament t = tournament(1L, "Summer Open", branch(BRANCH_ID, "CN1"), 10, null, IN_RANGE);
		when(tournamentRepository.findById(1L)).thenReturn(Optional.of(t));
		when(registrationRepository.findByTournamentIdIn(List.of(1L))).thenReturn(List.of());
		when(participantRepository.findByTournamentIdIn(List.of(1L))).thenReturn(List.of());
		when(matchRepository.findByTournamentIdIn(List.of(1L))).thenReturn(List.of());
		when(paymentRepository.findByRegistration_Tournament_IdIn(List.of(1L))).thenReturn(List.of());
		when(facebookPostRepository.findByTournamentIdOrderByPostedAtDesc(1L)).thenReturn(List.of());
		when(gameTypeDefinitionRepository.findById("EIGHT_BALL")).thenReturn(Optional.empty());
		when(tournamentFormatDefinitionRepository.findById("SINGLE_ELIMINATION")).thenReturn(Optional.empty());

		TournamentAnalyticsDetailResponse response = service.buildTournamentDetail(OWNER_ID, 1L, null);

		assertEquals("EIGHT_BALL", response.getGameTypeLabel());
		assertEquals("SINGLE_ELIMINATION", response.getFormatLabel());
		assertEquals(0.0, response.getMatchStats().getCompletionRate(), "no matches means no completion");
	}

	// ══════════════════════════ buildTransactionStats ══════════════════════════

	@Test
	@DisplayName("TC-043 · Transaction statistics report the success rate and average value")
	void TC043_buildTransactionStats_ratesAndAverages() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		Payment slow = payment(1L, t, PaymentStatus.SUCCESS.getValue(), "200000", IN_RANGE);
		slow.setCreatedAt(IN_RANGE.minus(30, ChronoUnit.MINUTES));
		when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of(
				slow,
				payment(2L, t, PaymentStatus.SUCCESS.getValue(), "400000", IN_RANGE),
				payment(3L, t, PaymentStatus.FAILED.getValue(), "100000", IN_RANGE),
				payment(4L, t, PaymentStatus.CANCELLED.getValue(), "100000", IN_RANGE)));

		TransactionStatsResponse response = service.buildTransactionStats(OWNER_ID, FROM, NOW, "month", null);

		assertEquals(4L, response.getTotalTransactions());
		assertEquals(2L, response.getSuccessCount());
		assertEquals(1L, response.getFailedCount());
		assertEquals(1L, response.getCancelledCount());
		assertEquals(50.0, response.getSuccessRatePct());
		assertEquals(new BigDecimal("600000"), response.getTotalAmount());
		assertEquals(new BigDecimal("300000"), response.getAvgTransactionValue());
		assertEquals(15.0, response.getAvgConversionMinutes(),
				"one payment took 30 minutes and one was instant");
	}

	@Test
	@DisplayName("TC-044 · A period with no transactions reports zeroes, not a division error")
	void TC044_buildTransactionStats_empty() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE)));

		TransactionStatsResponse response = service.buildTransactionStats(OWNER_ID, FROM, NOW, "month", null);

		assertEquals(0L, response.getTotalTransactions());
		assertEquals(0.0, response.getSuccessRatePct());
		assertEquals(BigDecimal.ZERO, response.getAvgTransactionValue());
		assertTrue(response.getByStatus().isEmpty());
	}

	// ══════════════════════════ listTransactions ══════════════════════════

	@Test
	@DisplayName("TC-045 · Listing the transactions of a tournament the owner does not own")
	void TC045_listTransactions_foreignTournamentDenied() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.listTransactions(OWNER_ID, 999L, null, null, null, 0, 10, null));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-046 · Nonsense paging values fall back to the first page of ten")
	@SuppressWarnings("unchecked")
	void TC046_listTransactions_pagingDefaults() {
		givenOwnerTournaments(List.of(tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE)));
		when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		service.listTransactions(OWNER_ID, null, null, null, null, -5, 0, null);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(paymentRepository).findAll(any(Specification.class), pageable.capture());
		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(10, pageable.getValue().getPageSize());
	}

	@Test
	@DisplayName("TC-047 · A transaction row carries the tournament and player it belongs to")
	@SuppressWarnings("unchecked")
	void TC047_listTransactions_mapsRow() {
		Tournament t = tournament(1L, "Summer Open", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(payment(1L, t, PaymentStatus.SUCCESS.getValue(), "200000", IN_RANGE))));

		PageResponse<PaymentHistoryResponse> page =
				service.listTransactions(OWNER_ID, null, null, null, null, 0, 10, null);

		PaymentHistoryResponse row = page.getContent().get(0);
		assertEquals("Summer Open", row.getTournamentName());
		assertEquals("Nguyễn Văn A", row.getPlayerName());
		assertEquals("Thành công", row.getStatusLabel());
	}

	@Test
	@DisplayName("TC-048 · A payment status the enum does not know still renders")
	@SuppressWarnings("unchecked")
	void TC048_listTransactions_unknownStatusLabel() {
		Tournament t = tournament(1L, "Summer Open", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		Payment odd = payment(1L, t, "LEGACY", "200000", IN_RANGE);
		when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(odd)));

		PageResponse<PaymentHistoryResponse> page =
				service.listTransactions(OWNER_ID, null, null, null, null, 0, 10, null);

		assertEquals("LEGACY", page.getContent().get(0).getStatusLabel());
	}

	// ══════════════════════════ buildMonthlyReport ══════════════════════════

	@Test
	@DisplayName("TC-049 · The monthly report defaults to the last twelve months")
	void TC049_buildMonthlyReport_defaultRange() {
		givenOwnerTournaments(List.of());

		MonthlyReportResponse response = service.buildMonthlyReport(OWNER_ID, null, null, null);

		assertEquals(12, response.getMonths().size());
		assertEquals(BigDecimal.ZERO, response.getTotalRevenue());
	}

	@Test
	@DisplayName("TC-050 · A range given back to front is put the right way round")
	void TC050_buildMonthlyReport_swapsReversedRange() {
		givenOwnerTournaments(List.of());
		YearMonth now = YearMonth.now(ZONE);

		MonthlyReportResponse response = service.buildMonthlyReport(OWNER_ID, now, now.minusMonths(2), null);

		assertEquals(3, response.getMonths().size());
		assertEquals(now.minusMonths(2).getMonthValue(), response.getMonths().get(0).getMonth());
	}

	@Test
	@DisplayName("TC-051 · An absurd range is clamped to five years")
	void TC051_buildMonthlyReport_clampsLongRange() {
		givenOwnerTournaments(List.of());
		YearMonth now = YearMonth.now(ZONE);

		MonthlyReportResponse response = service.buildMonthlyReport(OWNER_ID, YearMonth.of(1990, 1), now, null);

		assertEquals(61, response.getMonths().size(),
				"a mistyped year would otherwise generate hundreds of empty rows");
	}

	@Test
	@DisplayName("TC-052 · Monthly figures are totalled across the whole report")
	void TC052_buildMonthlyReport_totals() {
		Tournament t = tournament(1L, "Giải A", branch(BRANCH_ID, "CN1"), 16, null, IN_RANGE);
		givenOwnerTournaments(List.of(t));
		when(paymentRepository.findByRegistration_Tournament_IdIn(anyList())).thenReturn(List.of(
				payment(1L, t, PaymentStatus.SUCCESS.getValue(), "200000", IN_RANGE),
				payment(2L, t, PaymentStatus.PENDING.getValue(), "999999", IN_RANGE)));
		when(registrationRepository.findByTournamentIdIn(anyList())).thenReturn(List.of(
				registration(1L, t, 11L, RegistrationStatus.APPROVED.getValue(), IN_RANGE)));

		MonthlyReportResponse response = service.buildMonthlyReport(OWNER_ID, null, null, null);

		assertEquals(new BigDecimal("200000"), response.getTotalRevenue(), "only settled payments are revenue");
		assertEquals(1L, response.getTotalTransactions());
		assertEquals(1L, response.getTotalNewTournaments());
		assertEquals(1L, response.getTotalNewRegistrations());
		assertTrue(response.getMonths().stream().anyMatch(m -> m.getMonthLabel().startsWith("Tháng ")));
	}

	// ══════════════════════════ range clamping ══════════════════════════

	@Test
	@DisplayName("TC-053 · A range spanning more than three years is trimmed before any bucketing")
	void TC053_clampRangeStart_limitsToThreeYears() {
		givenOwnerTournaments(List.of());

		RevenueBreakdownResponse response = service.buildRevenueBreakdown(
				OWNER_ID, NOW.minus(3650, ChronoUnit.DAYS), NOW, "month", null);

		assertTrue(response.getTrend().size() <= 38,
				"ten years of daily buckets would be tens of thousands of points on one chart");
		assertNotNull(response.getTrend());
	}

	@Test
	@DisplayName("TC-054 · Leaving the period open defaults it to the last twelve months")
	void TC054_defaultRange_lastTwelveMonths() {
		givenOwnerTournaments(List.of());

		RevenueBreakdownResponse response = service.buildRevenueBreakdown(OWNER_ID, null, null, "month", null);

		assertEquals(13, response.getTrend().size(), "twelve months back, inclusive of both ends");
	}
}
