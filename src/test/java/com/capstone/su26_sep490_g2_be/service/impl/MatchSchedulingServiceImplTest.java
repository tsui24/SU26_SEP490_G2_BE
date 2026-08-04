package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentStage;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link MatchSchedulingServiceImpl}.
 *
 * <p>Mirrors the <b>MatchSchedulingService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-19 (the draw assigns tables and estimated times) and
 * UC-49 (table and time assignment for the running order).
 *
 * <p>Scheduling is a convenience layer: the draw and the score entry must succeed even when it
 * cannot. Several cases therefore assert that a failure stays silent rather than propagating.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · MatchSchedulingService — UC-19, UC-49")
class MatchSchedulingServiceImplTest {

	@Mock TournamentRepository tournamentRepository;
	@Mock MatchRepository matchRepository;

	@InjectMocks MatchSchedulingServiceImpl service;

	private static final Long TOURNAMENT_ID = 400L;
	private static final Instant START_AT = Instant.now().plus(2, ChronoUnit.DAYS);

	private static Tournament tournament(String gameType, Integer tableCount, Instant startAt) {
		return Tournament.builder()
				.id(TOURNAMENT_ID).name("Summer Open 2026")
				.gameType(gameType).tableCount(tableCount).startAt(startAt)
				.build();
	}

	private static TournamentStage stage(Long id, int orderNo) {
		return TournamentStage.builder().id(id).stageType("KNOCKOUT").name("Loại trực tiếp")
				.orderNo(orderNo).build();
	}

	private static Match match(Long id, TournamentStage stage, int round, int pos, int raceTo) {
		return Match.builder()
				.id(id).stage(stage).bracketType("KNOCKOUT")
				.roundNo(round).positionNo(pos).matchCode("R" + round + "-M" + pos)
				.raceTo(raceTo).status(MatchStatus.PENDING.getValue())
				.isBye(false).scheduleLocked(false)
				.player1Score(0).player2Score(0)
				.build();
	}

	private void givenTournamentAndMatches(Tournament t, List<Match> matches) {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(TOURNAMENT_ID))
				.thenReturn(matches);
	}

	// ══════════════════════════ reschedule — guards ══════════════════════════

	@Test
	@DisplayName("TC-001 · Scheduling a tournament that does not exist is a no-op")
	void TC001_reschedule_tournamentNotFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		service.reschedule(TOURNAMENT_ID);

		verify(matchRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("TC-002 · A tournament with no matches drawn yet is a no-op")
	void TC002_reschedule_noMatches() {
		givenTournamentAndMatches(tournament("9_BALL", 2, START_AT), List.of());

		service.reschedule(TOURNAMENT_ID);

		verify(matchRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("TC-003 · A failure while scheduling never breaks the caller")
	void TC003_reschedule_swallowsFailures() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenThrow(new IllegalStateException("db down"));

		service.reschedule(TOURNAMENT_ID);      // must not throw

		verify(matchRepository, never()).saveAll(any());
	}

	// ══════════════════════════ reschedule — table allocation ══════════════════════════

	@Test
	@DisplayName("TC-004 · Matches of the same round are spread across the available tables")
	void TC004_reschedule_spreadsAcrossTables() {
		TournamentStage stage = stage(1L, 1);
		Match m1 = match(11L, stage, 1, 1, 5);
		Match m2 = match(12L, stage, 1, 2, 5);
		givenTournamentAndMatches(tournament("9_BALL", 2, START_AT), List.of(m1, m2));

		service.reschedule(TOURNAMENT_ID);

		assertEquals(1, m1.getTableNo());
		assertEquals(2, m2.getTableNo(), "a second free table must be used before queueing behind the first");
		assertEquals(m1.getScheduledAt(), m2.getScheduledAt(), "both can start at once on different tables");
		verify(matchRepository).saveAll(List.of(m1, m2));
	}

	@Test
	@DisplayName("TC-005 · With one table the second match waits for the first plus a turnaround")
	void TC005_reschedule_singleTableQueues() {
		TournamentStage stage = stage(1L, 1);
		Match m1 = match(11L, stage, 1, 1, 5);
		Match m2 = match(12L, stage, 1, 2, 5);
		givenTournamentAndMatches(tournament("9_BALL", 1, START_AT), List.of(m1, m2));

		service.reschedule(TOURNAMENT_ID);

		assertEquals(1, m1.getTableNo());
		assertEquals(1, m2.getTableNo());
		assertEquals(m1.getEstimatedEndAt().plus(Duration.ofMinutes(10)), m2.getScheduledAt(),
				"ten minutes of turnaround are left between two matches on one table");
	}

	@Test
	@DisplayName("TC-006 · A tournament with no table count recorded is scheduled on one table")
	void TC006_reschedule_defaultsToOneTable() {
		TournamentStage stage = stage(1L, 1);
		Match m1 = match(11L, stage, 1, 1, 5);
		Match m2 = match(12L, stage, 1, 2, 5);
		givenTournamentAndMatches(tournament("9_BALL", null, START_AT), List.of(m1, m2));

		service.reschedule(TOURNAMENT_ID);

		assertEquals(1, m1.getTableNo());
		assertEquals(1, m2.getTableNo());
	}

	@Test
	@DisplayName("TC-007 · Play is scheduled from the announced start time, not from now")
	void TC007_reschedule_anchorsOnStartTime() {
		TournamentStage stage = stage(1L, 1);
		Match m1 = match(11L, stage, 1, 1, 5);
		givenTournamentAndMatches(tournament("9_BALL", 2, START_AT), List.of(m1));

		service.reschedule(TOURNAMENT_ID);

		assertEquals(START_AT, m1.getScheduledAt());
	}

	@Test
	@DisplayName("TC-008 · A tournament that should already have started is scheduled from now")
	void TC008_reschedule_anchorsOnNowWhenLate() {
		TournamentStage stage = stage(1L, 1);
		Match m1 = match(11L, stage, 1, 1, 5);
		Instant before = Instant.now();
		givenTournamentAndMatches(
				tournament("9_BALL", 2, Instant.now().minus(1, ChronoUnit.DAYS)), List.of(m1));

		service.reschedule(TOURNAMENT_ID);

		assertFalse(m1.getScheduledAt().isBefore(before), "a start time already past must not be used");
	}

	// ══════════════════════════ reschedule — dependencies ══════════════════════════

	@Test
	@DisplayName("TC-009 · A match cannot start before the match feeding it has finished")
	void TC009_reschedule_respectsFeeders() {
		TournamentStage stage = stage(1L, 1);
		Match semi1 = match(11L, stage, 1, 1, 5);
		Match semi2 = match(12L, stage, 1, 2, 5);
		Match finalMatch = match(13L, stage, 2, 1, 7);
		semi1.setNextMatchWin(finalMatch);
		semi2.setNextMatchWin(finalMatch);
		givenTournamentAndMatches(tournament("9_BALL", 4, START_AT), List.of(semi1, semi2, finalMatch));

		service.reschedule(TOURNAMENT_ID);

		assertFalse(finalMatch.getScheduledAt().isBefore(semi1.getEstimatedEndAt()));
		assertFalse(finalMatch.getScheduledAt().isBefore(semi2.getEstimatedEndAt()));
	}

	@Test
	@DisplayName("TC-010 · A walkthrough takes no table and no time")
	void TC010_reschedule_byeTakesNoTable() {
		TournamentStage stage = stage(1L, 1);
		Match bye = match(11L, stage, 1, 1, 5);
		bye.setIsBye(true);
		Match real = match(12L, stage, 1, 2, 5);
		givenTournamentAndMatches(tournament("9_BALL", 1, START_AT), List.of(bye, real));

		service.reschedule(TOURNAMENT_ID);

		assertNull(bye.getTableNo());
		assertNull(bye.getScheduledAt());
		assertEquals(START_AT, real.getScheduledAt(), "the bye must not push the real match back");
	}

	@Test
	@DisplayName("TC-011 · A stage with no cross-links starts after everything before it")
	void TC011_reschedule_independentStageRunsAfterPrevious() {
		TournamentStage group = TournamentStage.builder().id(1L).stageType("GROUP").name("Vòng tròn")
				.orderNo(1).build();
		TournamentStage playoff = TournamentStage.builder().id(2L).stageType("PLAYOFF").name("Playoff")
				.orderNo(2).build();
		Match groupMatch = match(11L, group, 1, 1, 5);
		Match playoffMatch = match(12L, playoff, 1, 1, 5);
		givenTournamentAndMatches(tournament("9_BALL", 4, START_AT), List.of(groupMatch, playoffMatch));

		service.reschedule(TOURNAMENT_ID);

		assertFalse(playoffMatch.getScheduledAt().isBefore(groupMatch.getEstimatedEndAt()),
				"a playoff drawn from a table of results cannot overlap the group stage that produces it");
	}

	// ══════════════════════════ reschedule — fixed matches ══════════════════════════

	@Test
	@DisplayName("TC-012 · A finished match is left exactly as it was")
	void TC012_reschedule_leavesFinishedMatchAlone() {
		TournamentStage stage = stage(1L, 1);
		Match done = match(11L, stage, 1, 1, 5);
		done.setStatus(MatchStatus.COMPLETED.getValue());
		done.setTableNo(1);
		Instant played = Instant.now().minus(1, ChronoUnit.HOURS);
		done.setScheduledAt(played);
		Match pending = match(12L, stage, 1, 2, 5);
		givenTournamentAndMatches(tournament("9_BALL", 2, START_AT), List.of(done, pending));

		service.reschedule(TOURNAMENT_ID);

		assertEquals(played, done.getScheduledAt());
		verify(matchRepository).saveAll(List.of(pending));
	}

	@Test
	@DisplayName("TC-013 · A match under way keeps its table until it is expected to end")
	void TC013_reschedule_inProgressHoldsItsTable() {
		TournamentStage stage = stage(1L, 1);
		Match live = match(11L, stage, 1, 1, 5);
		live.setStatus(MatchStatus.IN_PROGRESS.getValue());
		live.setTableNo(1);
		live.setScheduledAt(Instant.now().minus(10, ChronoUnit.MINUTES));
		Match waiting = match(12L, stage, 1, 2, 5);
		givenTournamentAndMatches(tournament("9_BALL", 1, START_AT), List.of(live, waiting));

		service.reschedule(TOURNAMENT_ID);

		assertEquals(1, waiting.getTableNo());
		assertTrue(waiting.getScheduledAt().isAfter(Instant.now()),
				"the only table is busy, so the next match must be pushed beyond the live one");
	}

	@Test
	@DisplayName("TC-014 · A time the organiser pinned by hand is never moved")
	void TC014_reschedule_lockedMatchKeepsItsSlot() {
		TournamentStage stage = stage(1L, 1);
		Match locked = match(11L, stage, 1, 1, 5);
		locked.setScheduleLocked(true);
		locked.setTableNo(2);
		Instant pinned = START_AT.plus(3, ChronoUnit.HOURS);
		locked.setScheduledAt(pinned);
		locked.setEstimatedEndAt(pinned.plus(1, ChronoUnit.HOURS));
		givenTournamentAndMatches(tournament("9_BALL", 2, START_AT), List.of(locked));

		service.reschedule(TOURNAMENT_ID);

		assertEquals(pinned, locked.getScheduledAt());
		assertEquals(2, locked.getTableNo());
		verify(matchRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("TC-015 · A match already scheduled later than the algorithm wants is not pulled forward")
	void TC015_reschedule_onlyEverPushesLater() {
		TournamentStage stage = stage(1L, 1);
		Match m1 = match(11L, stage, 1, 1, 5);
		Instant announced = START_AT.plus(5, ChronoUnit.HOURS);
		m1.setScheduledAt(announced);
		givenTournamentAndMatches(tournament("9_BALL", 2, START_AT), List.of(m1));

		service.reschedule(TOURNAMENT_ID);

		assertEquals(announced, m1.getScheduledAt(),
				"players have been told this time — rescheduling may delay it but never bring it forward");
	}

	// ══════════════════════════ estimateDurationMinutes ══════════════════════════

	@Test
	@DisplayName("TC-016 · A race to nine at nine-ball is estimated at eighty-five minutes")
	void TC016_estimateDuration_nineBall() {
		Match m = match(11L, stage(1L, 1), 1, 1, 9);
		m.setTournament(tournament("9_BALL", 2, START_AT));

		assertEquals(85, service.estimateDurationMinutes(m));    // ceil(1.5 × 9 × 6) rounded up to a multiple of 5
	}

	@Test
	@DisplayName("TC-017 · Eight-ball racks are allowed more time than nine-ball ones")
	void TC017_estimateDuration_eightBallIsSlower() {
		Match nine = match(11L, stage(1L, 1), 1, 1, 7);
		nine.setTournament(tournament("9_BALL", 2, START_AT));
		Match eight = match(12L, stage(1L, 1), 1, 2, 7);
		eight.setTournament(tournament("8_BALL", 2, START_AT));

		assertTrue(service.estimateDurationMinutes(eight) > service.estimateDurationMinutes(nine));
		assertEquals(85, service.estimateDurationMinutes(eight));
	}

	@Test
	@DisplayName("TC-018 · Ten-ball uses the middle rack time")
	void TC018_estimateDuration_tenBall() {
		Match m = match(11L, stage(1L, 1), 1, 1, 7);
		m.setTournament(tournament("10_BALL", 2, START_AT));

		assertEquals(75, service.estimateDurationMinutes(m));
	}

	@Test
	@DisplayName("TC-019 · An unknown game type falls back to the default rack time")
	void TC019_estimateDuration_unknownGameType() {
		Match m = match(11L, stage(1L, 1), 1, 1, 7);
		m.setTournament(tournament("SNOOKER", 2, START_AT));

		assertEquals(75, service.estimateDurationMinutes(m));    // same as the seven-minute default
	}

	@Test
	@DisplayName("TC-020 · A match with no tournament attached still returns an estimate")
	void TC020_estimateDuration_noTournament() {
		Match m = match(11L, stage(1L, 1), 1, 1, 7);

		assertEquals(75, service.estimateDurationMinutes(m));
	}

	@Test
	@DisplayName("TC-021 · A match with no race-to recorded is estimated on a race to five")
	void TC021_estimateDuration_defaultRaceTo() {
		Match m = match(11L, stage(1L, 1), 1, 1, 5);
		m.setRaceTo(null);
		m.setTournament(tournament("9_BALL", 2, START_AT));

		assertEquals(45, service.estimateDurationMinutes(m));    // ceil(1.5 × 5 × 6)
	}

	@Test
	@DisplayName("TC-022 · A walkthrough is estimated at no time at all")
	void TC022_estimateDuration_byeIsZero() {
		Match m = match(11L, stage(1L, 1), 1, 1, 9);
		m.setIsBye(true);
		m.setTournament(tournament("9_BALL", 2, START_AT));

		assertEquals(0, service.estimateDurationMinutes(m));
	}

	@Test
	@DisplayName("TC-023 · Estimates are rounded up to whole five-minute blocks")
	void TC023_estimateDuration_roundedToFiveMinutes() {
		Match m = match(11L, stage(1L, 1), 1, 1, 5);
		m.setTournament(tournament("SNOOKER", 2, START_AT));

		long minutes = service.estimateDurationMinutes(m);       // 1.5 × 5 × 7 = 52.5 → 53 → 55
		assertEquals(55, minutes);
		assertEquals(0, minutes % 5, "a schedule reads better in five-minute blocks");
	}

	@Test
	@DisplayName("TC-024 · The estimate is what the schedule actually books")
	void TC024_reschedule_bookedSlotMatchesTheEstimate() {
		TournamentStage stage = stage(1L, 1);
		Match m = match(11L, stage, 1, 1, 9);
		Tournament t = tournament("9_BALL", 2, START_AT);
		m.setTournament(t);
		givenTournamentAndMatches(t, List.of(m));

		service.reschedule(TOURNAMENT_ID);

		assertNotNull(m.getEstimatedEndAt());
		assertEquals(85, Duration.between(m.getScheduledAt(), m.getEstimatedEndAt()).toMinutes());
	}
}
