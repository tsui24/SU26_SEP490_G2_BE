package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.DrawResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.StandingsEntryResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfig;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValue;
import com.capstone.su26_sep490_g2_be.entity.TournamentStage;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.SeedingMethod;
import com.capstone.su26_sep490_g2_be.enums.TournamentStageStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchScoreEventRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentConfigRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentConfigValueRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentStageRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MatchSchedulingService;
import com.capstone.su26_sep490_g2_be.service.TournamentAuditService;
import com.capstone.su26_sep490_g2_be.service.TournamentRaceToRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link BracketGenerationServiceImpl}.
 *
 * <p>Mirrors the <b>BracketGenerationService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-19 (draw the bracket), UC-20 (confirm the draw / swap seeds),
 * UC-21 (advance a stage), UC-33 (league standings).
 *
 * <p>The two repositories the algorithm writes through — matches and stages — are backed by an
 * in-memory store rather than plain stubs. A bracket is a graph the code builds by saving rows and
 * reading them back, so a store is what lets the assertions talk about the shape that came out:
 * how many rounds, which slot a winner advances into, which match became a BYE.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · BracketGenerationService — UC-19, UC-20, UC-21, UC-33")
class BracketGenerationServiceImplTest {

	@Mock TournamentRepository tournamentRepository;
	@Mock TournamentConfigRepository tournamentConfigRepository;
	@Mock TournamentConfigValueRepository configValueRepository;
	@Mock ParticipantRepository participantRepository;
	@Mock TournamentStageRepository stageRepository;
	@Mock MatchRepository matchRepository;
	@Mock MatchScoreEventRepository scoreEventRepository;
	@Mock TournamentRaceToRuleService raceToRuleService;
	@Mock TournamentAuditService tournamentAuditService;
	@Mock ApplicationEventPublisher eventPublisher;
	@Mock MailContextBuilder mailContextBuilder;
	@Mock MatchSchedulingService matchSchedulingService;
	@Mock UserRepository userRepository;
	@Mock BranchAccessService branchAccessService;

	@InjectMocks BracketGenerationServiceImpl service;

	private static final Long TOURNAMENT_ID = 501L;
	private static final Long ACTOR_ID = 9L;
	private static final int RACE_TO = 9;

	/** id → row, in insertion order, standing in for the two tables the algorithm writes. */
	private final Map<Long, Match> matchStore = new LinkedHashMap<>();
	private final Map<Long, TournamentStage> stageStore = new LinkedHashMap<>();
	private long matchSeq = 0;
	private long stageSeq = 0;

	@BeforeEach
	void wireInMemoryRepositories() {
		lenient().when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
			Match m = inv.getArgument(0);
			if (m.getId() == null) m.setId(++matchSeq);
			matchStore.put(m.getId(), m);
			return m;
		});
		lenient().when(stageRepository.save(any(TournamentStage.class))).thenAnswer(inv -> {
			TournamentStage s = inv.getArgument(0);
			if (s.getId() == null) s.setId(++stageSeq);
			stageStore.put(s.getId(), s);
			return s;
		});
		lenient().when(matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(anyLong()))
				.thenAnswer(inv -> matchesOfStage(inv.getArgument(0)));
		lenient().when(matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(anyLong()))
				.thenAnswer(inv -> matchStore.values().stream()
						.sorted(Comparator.comparing(Match::getRoundNo).thenComparing(Match::getPositionNo))
						.toList());
		lenient().when(matchRepository.findById(anyLong()))
				.thenAnswer(inv -> Optional.ofNullable(matchStore.get(inv.getArgument(0))));
		lenient().when(stageRepository.findById(anyLong()))
				.thenAnswer(inv -> Optional.ofNullable(stageStore.get(inv.getArgument(0))));
		lenient().when(stageRepository.findByTournamentIdOrderByOrderNoAsc(anyLong()))
				.thenAnswer(inv -> stageStore.values().stream()
						.sorted(Comparator.comparing(TournamentStage::getOrderNo))
						.toList());
		lenient().when(raceToRuleService.resolveRaceTo(anyLong(), anyString(), anyString())).thenReturn(RACE_TO);
	}

	private List<Match> matchesOfStage(Long stageId) {
		return matchStore.values().stream()
				.filter(m -> m.getStage() != null && stageId.equals(m.getStage().getId()))
				.sorted(Comparator.comparing(Match::getRoundNo).thenComparing(Match::getPositionNo))
				.toList();
	}

	private List<TournamentStage> stagesOfType(String stageType) {
		return stageStore.values().stream().filter(s -> stageType.equals(s.getStageType())).toList();
	}

	private TournamentStage stageOfType(String stageType) {
		return stagesOfType(stageType).stream().findFirst().orElse(null);
	}

	private List<Match> matchesOfBracket(String bracketType) {
		return matchStore.values().stream()
				.filter(m -> bracketType.equals(m.getBracketType()))
				.sorted(Comparator.comparing(Match::getRoundNo).thenComparing(Match::getPositionNo))
				.toList();
	}

	// ══════════════════════════ fixtures ══════════════════════════

	private static Tournament tournament(String format, String status) {
		return Tournament.builder()
				.id(TOURNAMENT_ID).name("Summer Open 2026")
				.format(format).status(status)
				.branch(Branch.builder().id(3L).name("Chi nhánh Quận 1").build())
				.build();
	}

	/** Participants numbered 1..n, each seeded with its own number so seeding order is readable. */
	private static List<Participant> seededPlayers(int n) {
		List<Participant> list = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			list.add(Participant.builder()
					.id((long) i).displayName("VĐV " + i).seedNo(i)
					.status(ParticipantStatus.ACTIVE.getValue()).build());
		}
		return list;
	}

	private static List<Participant> unseededPlayers(int n) {
		List<Participant> list = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			list.add(Participant.builder()
					.id((long) i).displayName("VĐV " + i)
					.status(ParticipantStatus.ACTIVE.getValue()).build());
		}
		return list;
	}

	/** Wires the happy-path collaborators of generate(): actor exists, has access, roster is loaded. */
	private void givenDrawableTournament(Tournament t, List<Participant> roster, String seedingMethod) {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		lenient().when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		lenient().when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(true);
		lenient().when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(roster);
		lenient().when(tournamentConfigRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(TournamentConfig.builder()
						.tournamentId(TOURNAMENT_ID).seedingMethod(seedingMethod).build()));
		lenient().when(configValueRepository.findByIdTournamentIdAndIdFieldKey(eq(TOURNAMENT_ID), anyString()))
				.thenReturn(Optional.empty());
	}

	/** Makes one tournament_config_values row readable by the service. */
	private void givenConfigValue(String key, String value) {
		lenient().when(configValueRepository.findByIdTournamentIdAndIdFieldKey(TOURNAMENT_ID, key))
				.thenReturn(Optional.of(TournamentConfigValue.builder().value(value).build()));
	}

	// ══════════════════════════ generate — guards ══════════════════════════

	@Test
	@DisplayName("TC-001 · Drawing a tournament that does not exist")
	void TC001_generate_tournamentNotFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(matchRepository, never()).save(any(Match.class));
	}

	@Test
	@DisplayName("TC-002 · Drawing under an actor account that no longer exists")
	void TC002_generate_actorNotFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue())));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-003 · A manager may not draw a tournament outside the branches granted to them")
	void TC003_generate_branchAccessDenied() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue())));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
		verify(stageRepository, never()).save(any(TournamentStage.class));
	}

	@Test
	@DisplayName("TC-004 · Drawing before registration has been closed")
	void TC004_generate_wrongStatus() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.OPEN_FOR_REGISTRATION.getValue()),
				seededPlayers(8), SeedingMethod.MANUAL.name());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-005 · Drawing twice from REGISTRATION_CLOSED is refused")
	void TC005_generate_alreadyHasStages() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(8), SeedingMethod.MANUAL.name());
		stageStore.put(99L, TournamentStage.builder().id(99L).orderNo(1).stageType("KNOCKOUT").build());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.DUPLICATE_RESOURCE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-006 · Fewer than two active players cannot make a bracket")
	void TC006_generate_notEnoughPlayers() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(1), SeedingMethod.MANUAL.name());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-007 · Manual seeding with fewer seeds assigned than the configuration demands")
	void TC007_generate_seedCountInsufficient() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(true);
		List<Participant> roster = unseededPlayers(8);
		roster.get(0).setSeedNo(1);
		roster.get(1).setSeedNo(2);
		when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(roster);
		when(tournamentConfigRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(TournamentConfig.builder()
				.tournamentId(TOURNAMENT_ID).seedingMethod(SeedingMethod.MANUAL.name()).seedCount(4).build()));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.TOURNAMENT_SEED_COUNT_INSUFFICIENT, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-008 · Redrawing from DRAW_PREVIEW clears the previous bracket first")
	void TC008_generate_redrawClearsPreviousBracket() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.DRAW_PREVIEW.getValue());
		givenDrawableTournament(t, seededPlayers(4), SeedingMethod.MANUAL.name());
		TournamentStage old = TournamentStage.builder().id(88L).orderNo(1).stageType("KNOCKOUT")
				.tournament(t).status(TournamentStageStatus.PENDING.getValue()).build();
		stageStore.put(88L, old);
		matchStore.put(77L, Match.builder().id(77L).tournament(t).stage(old)
				.roundNo(1).positionNo(1).matchCode("R1-M1").status(MatchStatus.PENDING.getValue()).build());

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		verify(scoreEventRepository).deleteByMatchIdIn(List.of(77L));
		verify(matchRepository).deleteAll(any());
		verify(stageRepository).deleteAll(any());
	}

	// ══════════════════════════ generate — SINGLE_ELIMINATION ══════════════════════════

	@Test
	@DisplayName("TC-009 · A power-of-two field produces a full knockout bracket")
	void TC009_generate_singleElimination_eightPlayers() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(8), SeedingMethod.MANUAL.name());
		givenConfigValue("third_place_match", "false");

		DrawResultResponse response = service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(1, response.getStagesCreated());
		assertEquals(8, response.getParticipantsUsed());
		assertEquals(7, response.getMatchesCreated());          // 4 + 2 + 1
		assertEquals(TournamentStatus.DRAW_PREVIEW.getValue(), response.getNewStatus());
		List<Match> r1 = matchesOfStage(stageOfType("KNOCKOUT").getId()).stream()
				.filter(m -> m.getRoundNo() == 1).toList();
		assertEquals(4, r1.size());
		assertTrue(r1.stream().noneMatch(m -> Boolean.TRUE.equals(m.getIsBye())));
	}

	@Test
	@DisplayName("TC-010 · Round-one winners advance into alternating slots of the next match")
	void TC010_generate_singleElimination_advancementWiring() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(4), SeedingMethod.MANUAL.name());
		givenConfigValue("third_place_match", "false");

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		List<Match> matches = matchesOfStage(stageOfType("KNOCKOUT").getId());
		Match r1m1 = matches.stream().filter(m -> "R1-M1".equals(m.getMatchCode())).findFirst().orElseThrow();
		Match r1m2 = matches.stream().filter(m -> "R1-M2".equals(m.getMatchCode())).findFirst().orElseThrow();
		Match finalMatch = matches.stream().filter(m -> "R2-M1".equals(m.getMatchCode())).findFirst().orElseThrow();

		assertEquals(finalMatch.getId(), r1m1.getNextMatchWin().getId());
		assertEquals("player1", r1m1.getWinSlot());
		assertEquals(finalMatch.getId(), r1m2.getNextMatchWin().getId());
		assertEquals("player2", r1m2.getWinSlot());
	}

	@Test
	@DisplayName("TC-011 · The strongest seeds receive the byes when the field is not a power of two")
	void TC011_generate_singleElimination_byesGoToTopSeeds() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(6), SeedingMethod.MANUAL.name());
		givenConfigValue("third_place_match", "false");

		DrawResultResponse response = service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(7, response.getMatchesCreated());          // bracket of 8
		List<Match> r1 = matchesOfStage(stageOfType("KNOCKOUT").getId()).stream()
				.filter(m -> m.getRoundNo() == 1).toList();
		List<Match> byes = r1.stream().filter(m -> Boolean.TRUE.equals(m.getIsBye())).toList();
		assertEquals(2, byes.size());
		// Seeds 1 and 2 are the two who sit out round one
		List<Integer> byeSeeds = byes.stream().map(m -> m.getWinner().getSeedNo()).sorted().toList();
		assertEquals(List.of(1, 2), byeSeeds);
		assertTrue(byes.stream().allMatch(m -> MatchStatus.BYE.getValue().equals(m.getStatus())));
	}

	@Test
	@DisplayName("TC-012 · A bye winner is carried straight into the next round")
	void TC012_generate_singleElimination_byeWinnerAdvances() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(6), SeedingMethod.MANUAL.name());
		givenConfigValue("third_place_match", "false");

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		Match seedOneR1 = matchStore.values().stream()
				.filter(m -> m.getRoundNo() == 1 && Boolean.TRUE.equals(m.getIsBye()))
				.filter(m -> m.getWinner().getSeedNo() == 1).findFirst().orElseThrow();
		Match semiFinal = matchStore.get(seedOneR1.getNextMatchWin().getId());
		Participant placed = "player1".equals(seedOneR1.getWinSlot()) ? semiFinal.getPlayer1() : semiFinal.getPlayer2();
		assertNotNull(placed);
		assertEquals(1, placed.getSeedNo());
	}

	@Test
	@DisplayName("TC-013 · Seeds one and two are kept apart until the final")
	void TC013_generate_singleElimination_topSeedsInOppositeHalves() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(8), SeedingMethod.MANUAL.name());
		givenConfigValue("third_place_match", "false");

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		Match seedOne = matchOfPlayerSeed(1);
		Match seedTwo = matchOfPlayerSeed(2);
		// Positions 1-2 are the top half of a four-match round, 3-4 the bottom half
		boolean seedOneTopHalf = seedOne.getPositionNo() <= 2;
		boolean seedTwoTopHalf = seedTwo.getPositionNo() <= 2;
		assertFalse(seedOneTopHalf == seedTwoTopHalf, "seed 1 and seed 2 must start in different halves");
	}

	private Match matchOfPlayerSeed(int seedNo) {
		return matchStore.values().stream()
				.filter(m -> m.getRoundNo() == 1)
				.filter(m -> (m.getPlayer1() != null && Integer.valueOf(seedNo).equals(m.getPlayer1().getSeedNo()))
						|| (m.getPlayer2() != null && Integer.valueOf(seedNo).equals(m.getPlayer2().getSeedNo())))
				.findFirst().orElseThrow();
	}

	@Test
	@DisplayName("TC-014 · The third-place match is added when the configuration asks for it")
	void TC014_generate_singleElimination_thirdPlaceMatch() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(4), SeedingMethod.MANUAL.name());
		givenConfigValue("third_place_match", "true");

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		Match third = matchStore.values().stream()
				.filter(m -> "3RD".equals(m.getMatchCode())).findFirst().orElseThrow();
		List<Match> semis = matchStore.values().stream().filter(m -> m.getRoundNo() == 1).toList();
		assertEquals(2, semis.size());
		assertTrue(semis.stream().allMatch(m -> m.getNextMatchLose() != null
				&& third.getId().equals(m.getNextMatchLose().getId())),
				"both semi-final losers must drop into the third-place match");
		assertEquals("player1", semis.get(0).getLoseSlot());
		assertEquals("player2", semis.get(1).getLoseSlot());
	}

	@Test
	@DisplayName("TC-015 · Third place is on by default when nothing has been configured")
	void TC015_generate_singleElimination_thirdPlaceDefaultsOn() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(4), SeedingMethod.MANUAL.name());

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertTrue(matchStore.values().stream().anyMatch(m -> "3RD".equals(m.getMatchCode())));
	}

	@Test
	@DisplayName("TC-016 · An unknown format falls back to a knockout bracket")
	void TC016_generate_unknownFormatFallsBackToKnockout() {
		givenDrawableTournament(tournament("SOMETHING_ELSE", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(4), SeedingMethod.MANUAL.name());
		givenConfigValue("third_place_match", "false");

		DrawResultResponse response = service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(1, response.getStagesCreated());
		assertEquals("KNOCKOUT", stageOfType("KNOCKOUT").getStageType());
	}

	@Test
	@DisplayName("TC-017 · Random seeding ignores the seed numbers that were keyed in")
	void TC017_generate_randomSeedingUsesEveryPlayer() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(8), SeedingMethod.RANDOM.name());
		givenConfigValue("third_place_match", "false");

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		long placed = matchStore.values().stream().filter(m -> m.getRoundNo() == 1)
				.flatMap(m -> java.util.stream.Stream.of(m.getPlayer1(), m.getPlayer2()))
				.filter(java.util.Objects::nonNull).map(Participant::getId).distinct().count();
		assertEquals(8, placed, "every active player must be drawn exactly once");
	}

	@Test
	@DisplayName("TC-018 · Partly seeded fields put the seeded players first")
	void TC018_generate_partialSeedingRanksSeededFirst() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(true);
		List<Participant> roster = unseededPlayers(6);
		roster.get(4).setSeedNo(1);     // player 5 is the top seed
		roster.get(5).setSeedNo(2);     // player 6 is the second seed
		when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(roster);
		when(tournamentConfigRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(TournamentConfig.builder()
				.tournamentId(TOURNAMENT_ID).seedingMethod(SeedingMethod.MANUAL.name()).build()));
		when(configValueRepository.findByIdTournamentIdAndIdFieldKey(eq(TOURNAMENT_ID), anyString()))
				.thenReturn(Optional.empty());

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		List<Long> byeWinners = matchStore.values().stream()
				.filter(m -> m.getRoundNo() == 1 && Boolean.TRUE.equals(m.getIsBye()))
				.map(m -> m.getWinner().getId()).sorted().toList();
		assertEquals(List.of(5L, 6L), byeWinners, "the two seeded players take the two byes");
	}

	@Test
	@DisplayName("TC-019 · A successful draw moves the tournament to DRAW_PREVIEW and is audited")
	void TC019_generate_recordsAuditAndReschedules() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue());
		givenDrawableTournament(t, seededPlayers(4), SeedingMethod.MANUAL.name());

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(TournamentStatus.DRAW_PREVIEW.getValue(), t.getStatus());
		verify(tournamentRepository).save(t);
		verify(tournamentAuditService).recordChange(t, TournamentStatus.REGISTRATION_CLOSED.getValue(),
				TournamentStatus.DRAW_PREVIEW.getValue(), ACTOR_ID, "Bốc thăm — sinh bracket");
		verify(matchSchedulingService).reschedule(TOURNAMENT_ID);
	}

	@Test
	@DisplayName("TC-020 · Race-to comes from the rule table, and a lookup failure falls back to seven")
	void TC020_generate_raceToFallback() {
		givenDrawableTournament(tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(4), SeedingMethod.MANUAL.name());
		givenConfigValue("third_place_match", "false");
		when(raceToRuleService.resolveRaceTo(anyLong(), anyString(), anyString()))
				.thenThrow(new IllegalStateException("no rule seeded"));

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertTrue(matchStore.values().stream().allMatch(m -> m.getRaceTo() == 7),
				"an unseeded rule table must not leave race-to null");
	}

	// ══════════════════════════ generate — DOUBLE_ELIMINATION ══════════════════════════

	@Test
	@DisplayName("TC-021 · Full double elimination builds a winners, losers and grand-final stage")
	void TC021_generate_fullDoubleElimination() {
		givenDrawableTournament(tournament("DOUBLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(8), SeedingMethod.MANUAL.name());

		DrawResultResponse response = service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(3, response.getStagesCreated());
		assertNotNull(stageOfType("WINNERS"));
		assertNotNull(stageOfType("LOSERS"));
		assertNotNull(stageOfType("GRAND_FINAL"));
		assertEquals(7, matchesOfBracket("WINNERS").size());     // 4 + 2 + 1
		assertEquals(6, matchesOfBracket("LOSERS").size());      // 2 + 2 + 1 + 1
		assertEquals(1, matchesOfBracket("GRAND_FINAL").size());
	}

	@Test
	@DisplayName("TC-022 · Both bracket finals feed the grand final, one into each slot")
	void TC022_generate_doubleElimination_grandFinalWiring() {
		givenDrawableTournament(tournament("DOUBLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(8), SeedingMethod.MANUAL.name());

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		Match grandFinal = matchesOfBracket("GRAND_FINAL").get(0);
		Match winnersFinal = matchStore.values().stream()
				.filter(m -> "W-R3-M1".equals(m.getMatchCode())).findFirst().orElseThrow();
		Match losersFinal = matchStore.values().stream()
				.filter(m -> "L-R4-M1".equals(m.getMatchCode())).findFirst().orElseThrow();

		assertEquals(grandFinal.getId(), winnersFinal.getNextMatchWin().getId());
		assertEquals("player1", winnersFinal.getWinSlot());
		assertEquals(grandFinal.getId(), losersFinal.getNextMatchWin().getId());
		assertEquals("player2", losersFinal.getWinSlot());
	}

	@Test
	@DisplayName("TC-023 · Losers of the first winners round drop in pairs into one losers match")
	void TC023_generate_doubleElimination_firstRoundDrops() {
		givenDrawableTournament(tournament("DOUBLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(8), SeedingMethod.MANUAL.name());

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		Match w1 = matchStore.values().stream().filter(m -> "W-R1-M1".equals(m.getMatchCode())).findFirst().orElseThrow();
		Match w2 = matchStore.values().stream().filter(m -> "W-R1-M2".equals(m.getMatchCode())).findFirst().orElseThrow();
		Match l1 = matchStore.values().stream().filter(m -> "L-R1-M1".equals(m.getMatchCode())).findFirst().orElseThrow();

		assertEquals(l1.getId(), w1.getNextMatchLose().getId());
		assertEquals("player1", w1.getLoseSlot());
		assertEquals(l1.getId(), w2.getNextMatchLose().getId());
		assertEquals("player2", w2.getLoseSlot());
	}

	@Test
	@DisplayName("TC-024 · A bye in the winners bracket produces no drop into the losers bracket")
	void TC024_generate_doubleElimination_byeHasNoLoserDrop() {
		givenDrawableTournament(tournament("DOUBLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(6), SeedingMethod.MANUAL.name());

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		List<Match> byes = matchesOfBracket("WINNERS").stream()
				.filter(m -> Boolean.TRUE.equals(m.getIsBye())).toList();
		assertEquals(2, byes.size());
		assertTrue(byes.stream().allMatch(m -> m.getNextMatchLose() == null),
				"a walk-through has no loser, so it must not be wired into the losers bracket");
	}

	@Test
	@DisplayName("TC-025 · CUT_TO_SE builds winners, losers and a blank final knockout stage")
	void TC025_generate_cutToSe() {
		givenDrawableTournament(tournament("DOUBLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(16), SeedingMethod.MANUAL.name());
		givenConfigValue("de_mode", "CUT_TO_SE");
		givenConfigValue("se_phase_size", "4");

		DrawResultResponse response = service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(3, response.getStagesCreated());
		TournamentStage se = stageOfType("FINAL_BRACKET");
		assertNotNull(se);
		assertEquals("Last 4 — Loại trực tiếp", se.getName());
		List<Match> seMatches = matchesOfStage(se.getId());
		assertEquals(3, seMatches.size());                       // 2 + 1
		assertTrue(seMatches.stream().allMatch(m -> m.getPlayer1() == null && m.getPlayer2() == null),
				"the final bracket stays blank until the cut-off rounds are played");
	}

	@Test
	@DisplayName("TC-026 · An impossible cut-off size falls back to full double elimination")
	void TC026_generate_cutToSe_invalidConfigFallsBack() {
		givenDrawableTournament(tournament("DOUBLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(8), SeedingMethod.MANUAL.name());
		givenConfigValue("de_mode", "CUT_TO_SE");
		givenConfigValue("se_phase_size", "16");                 // larger than the bracket itself

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertNull(stageOfType("FINAL_BRACKET"));
		assertNotNull(stageOfType("GRAND_FINAL"));
	}

	// ══════════════════════════ generate — GROUP_PLAYOFF ══════════════════════════

	@Test
	@DisplayName("TC-027 · Group playoff draws a full round robin plus a blank playoff bracket")
	void TC027_generate_groupPlayoff() {
		givenDrawableTournament(tournament("GROUP_PLAYOFF", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(6), SeedingMethod.MANUAL.name());

		DrawResultResponse response = service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(2, response.getStagesCreated());
		List<Match> group = matchesOfBracket("GROUP");
		assertEquals(15, group.size());                          // 6 players → C(6,2)
		assertEquals(5, group.stream().map(Match::getRoundNo).distinct().count());
		assertTrue(group.stream().allMatch(m -> m.getPlayer1() != null && m.getPlayer2() != null));
		assertEquals(3, matchesOfBracket("PLAYOFF").size());      // top 4 → 2 + 1
	}

	@Test
	@DisplayName("TC-028 · An odd field gives each player one bye round in the round robin")
	void TC028_generate_groupPlayoff_oddField() {
		givenDrawableTournament(tournament("GROUP_PLAYOFF", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(5), SeedingMethod.MANUAL.name());

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		List<Match> group = matchesOfBracket("GROUP");
		assertEquals(10, group.size());                          // C(5,2)
		assertEquals(5, group.stream().map(Match::getRoundNo).distinct().count());
		// every round holds two matches, the fifth player sits out
		assertTrue(group.stream().collect(java.util.stream.Collectors.groupingBy(Match::getRoundNo))
				.values().stream().allMatch(list -> list.size() == 2));
	}

	@Test
	@DisplayName("TC-029 · A configured playoff size larger than the field is capped")
	void TC029_generate_groupPlayoff_playoffSizeCapped() {
		givenDrawableTournament(tournament("GROUP_PLAYOFF", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(4), SeedingMethod.MANUAL.name());
		givenConfigValue("playoff_size", "16");

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(3, matchesOfBracket("PLAYOFF").size(), "a four-player field cannot host a 16-player playoff");
	}

	@Test
	@DisplayName("TC-030 · A playoff size that is not a number falls back to the default of four")
	void TC030_generate_groupPlayoff_unparsablePlayoffSize() {
		givenDrawableTournament(tournament("GROUP_PLAYOFF", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(8), SeedingMethod.MANUAL.name());
		givenConfigValue("playoff_size", "bốn");

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(3, matchesOfBracket("PLAYOFF").size());
	}

	// ══════════════════════════ generate — PROGRESSIVE_ROUND_ROBIN ══════════════════════════

	@Test
	@DisplayName("TC-031 · Progressive round robin lays out every league stage and the playoff up front")
	void TC031_generate_progressiveRoundRobin() {
		givenDrawableTournament(tournament("PROGRESSIVE_ROUND_ROBIN", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(12), SeedingMethod.MANUAL.name());
		givenConfigValue("pe_survivors_per_stage", "10,6,4");

		DrawResultResponse response = service.generate(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(4, response.getStagesCreated());            // 3 league stages + playoff
		List<TournamentStage> league = stagesOfType("PROGRESSIVE_ROUND");
		assertEquals(3, league.size());
		assertEquals(12, league.get(0).getPeActiveCount());
		assertEquals(2, league.get(0).getPeEliminateCount());    // 12 → 10
		assertNotNull(stageOfType("PROGRESSIVE_PLAYOFF"));
	}

	@Test
	@DisplayName("TC-032 · Only the first league stage is drawn with real players")
	void TC032_generate_progressive_laterStagesAreBlank() {
		givenDrawableTournament(tournament("PROGRESSIVE_ROUND_ROBIN", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(12), SeedingMethod.MANUAL.name());
		givenConfigValue("pe_survivors_per_stage", "10,6,4");

		service.generate(TOURNAMENT_ID, ACTOR_ID);

		List<TournamentStage> league = stagesOfType("PROGRESSIVE_ROUND");
		assertTrue(matchesOfStage(league.get(0).getId()).stream()
				.allMatch(m -> m.getPlayer1() != null && m.getPlayer2() != null));
		assertTrue(matchesOfStage(league.get(1).getId()).stream()
				.allMatch(m -> m.getPlayer1() == null && m.getPlayer2() == null),
				"stage two is a placeholder until stage one has been settled");
		assertEquals(66, matchesOfStage(league.get(0).getId()).size());   // C(12,2)
		assertEquals(45, matchesOfStage(league.get(1).getId()).size());   // C(10,2)
	}

	@Test
	@DisplayName("TC-033 · A survivor list the real turnout cannot support is refused")
	void TC033_generate_progressive_turnoutMismatch() {
		givenDrawableTournament(tournament("PROGRESSIVE_ROUND_ROBIN", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(8), SeedingMethod.MANUAL.name());
		givenConfigValue("pe_survivors_per_stage", "10,6,4");

		BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.PROGRESSIVE_CONFIG_INVALID, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-034 · A malformed survivor list is reported as a configuration error, not a crash")
	void TC034_generate_progressive_unparsableSurvivors() {
		givenDrawableTournament(tournament("PROGRESSIVE_ROUND_ROBIN", TournamentStatus.REGISTRATION_CLOSED.getValue()),
				seededPlayers(12), SeedingMethod.MANUAL.name());
		givenConfigValue("pe_survivors_per_stage", "10,sáu,4");

		BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.PROGRESSIVE_CONFIG_INVALID, ex.getErrorCode());
	}

	// ══════════════════════════ confirmDraw ══════════════════════════

	@Test
	@DisplayName("TC-035 · Confirming the draw locks it in and announces it by email")
	void TC035_confirmDraw_happyPath() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.DRAW_PREVIEW.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(true);
		when(mailContextBuilder.systemContext()).thenReturn(new HashMap<>());

		service.confirmDraw(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(TournamentStatus.DRAW_DONE.getValue(), t.getStatus());
		verify(tournamentAuditService).recordChange(t, TournamentStatus.DRAW_PREVIEW.getValue(),
				TournamentStatus.DRAW_DONE.getValue(), ACTOR_ID, "Xác nhận bracket");
		ArgumentCaptor<MailDomainEvent> event = ArgumentCaptor.forClass(MailDomainEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertEquals(EmailEventType.TOURNAMENT_DRAW_COMPLETED, event.getValue().eventType());
		assertEquals("TOURNAMENT-DRAW-" + TOURNAMENT_ID, event.getValue().entityKey());
	}

	@Test
	@DisplayName("TC-036 · Confirming a draw that was never previewed")
	void TC036_confirmDraw_wrongStatus() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.REGISTRATION_CLOSED.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.confirmDraw(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
		verify(eventPublisher, never()).publishEvent(any(MailDomainEvent.class));
	}

	@Test
	@DisplayName("TC-037 · Confirming a draw for a tournament that does not exist")
	void TC037_confirmDraw_tournamentNotFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.confirmDraw(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-038 · Confirming a draw outside the branches granted to the actor")
	void TC038_confirmDraw_accessDenied() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("SINGLE_ELIMINATION", TournamentStatus.DRAW_PREVIEW.getValue())));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.confirmDraw(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	// ══════════════════════════ swapPlayers ══════════════════════════

	/** Two round-one knockout matches sharing a stage, as they exist during DRAW_PREVIEW. */
	private Match[] twoRoundOneMatches(Tournament t, String stageType) {
		TournamentStage stage = TournamentStage.builder().id(1L).tournament(t)
				.stageType(stageType).orderNo(1).build();
		stageStore.put(1L, stage);
		Match m1 = Match.builder().id(11L).tournament(t).stage(stage).roundNo(1).positionNo(1)
				.matchCode("R1-M1").status(MatchStatus.PENDING.getValue()).isBye(false)
				.player1(Participant.builder().id(1L).displayName("VĐV 1").build())
				.player2(Participant.builder().id(2L).displayName("VĐV 2").build()).build();
		Match m2 = Match.builder().id(12L).tournament(t).stage(stage).roundNo(1).positionNo(2)
				.matchCode("R1-M2").status(MatchStatus.PENDING.getValue()).isBye(false)
				.player1(Participant.builder().id(3L).displayName("VĐV 3").build())
				.player2(Participant.builder().id(4L).displayName("VĐV 4").build()).build();
		matchStore.put(11L, m1);
		matchStore.put(12L, m2);
		lenient().when(matchRepository.findByIdWithDetails(11L)).thenReturn(Optional.of(m1));
		lenient().when(matchRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(m2));
		return new Match[] { m1, m2 };
	}

	@Test
	@DisplayName("TC-039 · Swapping two round-one players exchanges exactly those two slots")
	void TC039_swapPlayers_happyPath() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.DRAW_PREVIEW.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		Match[] pair = twoRoundOneMatches(t, "KNOCKOUT");

		service.swapPlayers(TOURNAMENT_ID, 11L, "player1", 12L, "player2");

		assertEquals(4L, pair[0].getPlayer1().getId());
		assertEquals(1L, pair[1].getPlayer2().getId());
		assertEquals(2L, pair[0].getPlayer2().getId(), "the untouched slots must stay where they were");
		assertEquals(3L, pair[1].getPlayer1().getId());
	}

	@Test
	@DisplayName("TC-040 · Swapping a player against an empty slot turns the match into a bye")
	void TC040_swapPlayers_emptySlotBecomesBye() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.DRAW_PREVIEW.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		Match[] pair = twoRoundOneMatches(t, "KNOCKOUT");
		pair[1].setPlayer2(null);

		service.swapPlayers(TOURNAMENT_ID, 11L, "player2", 12L, "player2");

		assertTrue(pair[0].getIsBye());
		assertEquals(MatchStatus.BYE.getValue(), pair[0].getStatus());
		assertEquals(1L, pair[0].getWinner().getId());
		assertFalse(pair[1].getIsBye());
	}

	@Test
	@DisplayName("TC-041 · Players may not be swapped once the draw is confirmed")
	void TC041_swapPlayers_wrongStatus() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("SINGLE_ELIMINATION", TournamentStatus.DRAW_DONE.getValue())));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.swapPlayers(TOURNAMENT_ID, 11L, "player1", 12L, "player2"));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-042 · Swapping with a match from another tournament")
	void TC042_swapPlayers_matchFromAnotherTournament() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.DRAW_PREVIEW.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		Match[] pair = twoRoundOneMatches(t, "KNOCKOUT");
		pair[1].setTournament(Tournament.builder().id(999L).build());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.swapPlayers(TOURNAMENT_ID, 11L, "player1", 12L, "player2"));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-043 · Only round one may be rearranged")
	void TC043_swapPlayers_notRoundOne() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.DRAW_PREVIEW.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		Match[] pair = twoRoundOneMatches(t, "KNOCKOUT");
		pair[1].setRoundNo(2);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.swapPlayers(TOURNAMENT_ID, 11L, "player1", 12L, "player2"));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-044 · The losers bracket and the grand final are not seeded by hand")
	void TC044_swapPlayers_losersBracketRefused() {
		Tournament t = tournament("DOUBLE_ELIMINATION", TournamentStatus.DRAW_PREVIEW.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		twoRoundOneMatches(t, "LOSERS");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.swapPlayers(TOURNAMENT_ID, 11L, "player1", 12L, "player2"));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-045 · Swapping against a match id that does not exist")
	void TC045_swapPlayers_matchNotFound() {
		Tournament t = tournament("SINGLE_ELIMINATION", TournamentStatus.DRAW_PREVIEW.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(matchRepository.findByIdWithDetails(11L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.swapPlayers(TOURNAMENT_ID, 11L, "player1", 12L, "player2"));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ standings fixtures ══════════════════════════

	private TournamentStage addStage(String stageType, int orderNo) {
		TournamentStage stage = TournamentStage.builder()
				.id(++stageSeq).stageType(stageType).name(stageType).orderNo(orderNo)
				.status(TournamentStageStatus.PENDING.getValue()).build();
		stageStore.put(stage.getId(), stage);
		return stage;
	}

	private Match addMatch(TournamentStage stage, int round, int pos, Participant p1, Participant p2,
						   Integer s1, Integer s2, String status) {
		Participant winner = null, loser = null;
		if (p1 != null && p2 != null && s1 != null && s2 != null && !s1.equals(s2)) {
			winner = s1 > s2 ? p1 : p2;
			loser = s1 > s2 ? p2 : p1;
		}
		Match m = Match.builder().id(++matchSeq).stage(stage).bracketType(stage.getStageType())
				.roundNo(round).positionNo(pos).matchCode(stage.getStageType() + "-R" + round + "-M" + pos)
				.player1(p1).player2(p2).player1Score(s1).player2Score(s2)
				.winner(MatchStatus.PENDING.getValue().equals(status) ? null : winner)
				.loser(MatchStatus.PENDING.getValue().equals(status) ? null : loser)
				.status(status).isBye(false).build();
		matchStore.put(m.getId(), m);
		return m;
	}

	/** Every pair meets once; the lower list index always wins 5-0, so the ranking mirrors the list. */
	private void addFinishedRoundRobin(TournamentStage stage, List<Participant> players) {
		int round = 1, pos = 1;
		for (int i = 0; i < players.size(); i++) {
			for (int j = i + 1; j < players.size(); j++) {
				addMatch(stage, round, pos, players.get(i), players.get(j), 5, 0, MatchStatus.COMPLETED.getValue());
				if (++pos > Math.max(1, players.size() / 2)) { pos = 1; round++; }
			}
		}
	}

	private void givenLeagueRoster(List<Participant> players) {
		lenient().when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(players);
		lenient().when(configValueRepository.findByIdTournamentIdAndIdFieldKey(eq(TOURNAMENT_ID), anyString()))
				.thenReturn(Optional.empty());
	}

	// ══════════════════════════ getLeagueStandings ══════════════════════════

	@Test
	@DisplayName("TC-046 · Standings of a tournament that has no group stage")
	void TC046_getLeagueStandings_noGroupStage() {
		addStage("KNOCKOUT", 1);

		assertTrue(service.getLeagueStandings(TOURNAMENT_ID).isEmpty());
		verify(participantRepository, never()).findByTournamentIdAndStatus(anyLong(), anyString());
	}

	@Test
	@DisplayName("TC-047 · Standings are ordered by wins, then frame difference, then frames won")
	void TC047_getLeagueStandings_ordering() {
		TournamentStage group = addStage("GROUP", 1);
		List<Participant> players = seededPlayers(4);
		givenLeagueRoster(players);
		addFinishedRoundRobin(group, players);

		List<StandingsEntryResponse> standings = service.getLeagueStandings(TOURNAMENT_ID);

		assertEquals(4, standings.size());
		assertEquals(List.of(1L, 2L, 3L, 4L), standings.stream().map(StandingsEntryResponse::getParticipantId).toList());
		assertEquals(3, standings.get(0).getWins());
		assertEquals(15, standings.get(0).getFramesWon());
		assertEquals(0, standings.get(0).getFramesLost());
		assertEquals(15, standings.get(0).getFrameDiff());
		assertEquals(3, standings.get(0).getMatchesPlayed());
		assertEquals(1, standings.get(0).getRank());
	}

	@Test
	@DisplayName("TC-048 · Frame difference separates two players on the same number of wins")
	void TC048_getLeagueStandings_frameDifferenceTieBreak() {
		TournamentStage group = addStage("GROUP", 1);
		List<Participant> players = seededPlayers(3);
		givenLeagueRoster(players);
		Participant p1 = players.get(0), p2 = players.get(1), p3 = players.get(2);
		addMatch(group, 1, 1, p1, p3, 5, 4, MatchStatus.COMPLETED.getValue());   // narrow win
		addMatch(group, 2, 1, p2, p3, 5, 0, MatchStatus.COMPLETED.getValue());   // comfortable win
		addMatch(group, 3, 1, p1, p2, 2, 5, MatchStatus.COMPLETED.getValue());   // p2 also beats p1

		List<StandingsEntryResponse> standings = service.getLeagueStandings(TOURNAMENT_ID);

		assertEquals(2L, standings.get(0).getParticipantId());
		assertEquals(8, standings.get(0).getFrameDiff());        // 10 frames won, 2 conceded
		assertEquals(1L, standings.get(1).getParticipantId());
	}

	@Test
	@DisplayName("TC-049 · The top four are flagged as advancing to the playoff")
	void TC049_getLeagueStandings_advancesFlag() {
		TournamentStage group = addStage("GROUP", 1);
		List<Participant> players = seededPlayers(6);
		givenLeagueRoster(players);
		addFinishedRoundRobin(group, players);

		List<StandingsEntryResponse> standings = service.getLeagueStandings(TOURNAMENT_ID);

		assertEquals(List.of(true, true, true, true, false, false),
				standings.stream().map(StandingsEntryResponse::getAdvancesToPlayoff).toList());
	}

	@Test
	@DisplayName("TC-050 · Matches that have not been played do not count towards the table")
	void TC050_getLeagueStandings_ignoresUnplayedMatches() {
		TournamentStage group = addStage("GROUP", 1);
		List<Participant> players = seededPlayers(2);
		givenLeagueRoster(players);
		addMatch(group, 1, 1, players.get(0), players.get(1), 0, 0, MatchStatus.PENDING.getValue());

		List<StandingsEntryResponse> standings = service.getLeagueStandings(TOURNAMENT_ID);

		assertEquals(2, standings.size());
		assertTrue(standings.stream().allMatch(s -> s.getMatchesPlayed() == 0));
	}

	// ══════════════════════════ populateLeaguePlayoff ══════════════════════════

	/** A finished group stage plus a blank two-round playoff bracket, wired for advancement. */
	private List<Match> givenFinishedLeague(List<Participant> players) {
		TournamentStage group = addStage("GROUP", 1);
		addFinishedRoundRobin(group, players);
		TournamentStage playoff = addStage("PLAYOFF", 2);
		Match po1 = addMatch(playoff, 1, 1, null, null, 0, 0, MatchStatus.PENDING.getValue());
		Match po2 = addMatch(playoff, 1, 2, null, null, 0, 0, MatchStatus.PENDING.getValue());
		Match poFinal = addMatch(playoff, 2, 1, null, null, 0, 0, MatchStatus.PENDING.getValue());
		po1.setNextMatchWin(poFinal); po1.setWinSlot("player1");
		po2.setNextMatchWin(poFinal); po2.setWinSlot("player2");
		return List.of(po1, po2, poFinal);
	}

	@Test
	@DisplayName("TC-051 · The playoff cannot be filled before the tournament is under way")
	void TC051_populateLeaguePlayoff_wrongStatus() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("GROUP_PLAYOFF", TournamentStatus.DRAW_DONE.getValue())));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.populateLeaguePlayoff(TOURNAMENT_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-052 · The playoff cannot be filled while group matches are outstanding")
	void TC052_populateLeaguePlayoff_groupUnfinished() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("GROUP_PLAYOFF", TournamentStatus.IN_PROGRESS.getValue())));
		List<Participant> players = seededPlayers(4);
		givenLeagueRoster(players);
		givenFinishedLeague(players);
		TournamentStage group = stageOfType("GROUP");
		addMatch(group, 9, 1, players.get(0), players.get(1), 0, 0, MatchStatus.PENDING.getValue());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.populateLeaguePlayoff(TOURNAMENT_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-053 · The playoff pairs the leader with the bottom qualifier")
	void TC053_populateLeaguePlayoff_seedsTopAgainstBottom() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("GROUP_PLAYOFF", TournamentStatus.IN_PROGRESS.getValue())));
		List<Participant> players = seededPlayers(4);
		givenLeagueRoster(players);
		List<Match> playoff = givenFinishedLeague(players);

		service.populateLeaguePlayoff(TOURNAMENT_ID);

		assertEquals(1L, playoff.get(0).getPlayer1().getId());
		assertEquals(4L, playoff.get(0).getPlayer2().getId());
		assertEquals(2L, playoff.get(1).getPlayer1().getId());
		assertEquals(3L, playoff.get(1).getPlayer2().getId());
		assertEquals(TournamentStageStatus.COMPLETED.getValue(), stageOfType("GROUP").getStatus());
	}

	@Test
	@DisplayName("TC-054 · Filling a playoff the tournament never had")
	void TC054_populateLeaguePlayoff_noPlayoffStage() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("GROUP_PLAYOFF", TournamentStatus.IN_PROGRESS.getValue())));
		TournamentStage group = addStage("GROUP", 1);
		addFinishedRoundRobin(group, seededPlayers(4));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.populateLeaguePlayoff(TOURNAMENT_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-055 · A playoff needs at least two qualifiers")
	void TC055_populateLeaguePlayoff_notEnoughAdvancers() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("GROUP_PLAYOFF", TournamentStatus.IN_PROGRESS.getValue())));
		TournamentStage group = addStage("GROUP", 1);
		addStage("PLAYOFF", 2);
		givenLeagueRoster(List.of());
		addMatch(group, 1, 1, null, null, 0, 0, MatchStatus.BYE.getValue());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.populateLeaguePlayoff(TOURNAMENT_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	// ══════════════════════════ computeStageStandings ══════════════════════════

	@Test
	@DisplayName("TC-056 · Standings of a stage nobody has been drawn into")
	void TC056_computeStageStandings_emptyStage() {
		TournamentStage stage = addStage("PROGRESSIVE_ROUND", 1);

		assertTrue(service.computeStageStandings(stage.getId()).isEmpty());
	}

	@Test
	@DisplayName("TC-057 · Stage standings are built from the players drawn into that stage alone")
	void TC057_computeStageStandings_ranksByWins() {
		TournamentStage stage = addStage("PROGRESSIVE_ROUND", 1);
		List<Participant> players = seededPlayers(4);
		addFinishedRoundRobin(stage, players);

		List<StandingsEntryResponse> standings = service.computeStageStandings(stage.getId());

		assertEquals(List.of(1L, 2L, 3L, 4L), standings.stream().map(StandingsEntryResponse::getParticipantId).toList());
		assertEquals(3, standings.get(0).getWins());
		assertEquals(0, standings.get(3).getWins());
		verify(participantRepository, never()).findByTournamentIdAndStatus(anyLong(), anyString());
	}

	@Test
	@DisplayName("TC-058 · A head-to-head result separates two players who are level on everything else")
	void TC058_computeStageStandings_headToHeadTieBreak() {
		TournamentStage stage = addStage("PROGRESSIVE_ROUND", 1);
		List<Participant> players = seededPlayers(4);
		Participant a = players.get(0), b = players.get(1), c = players.get(2), d = players.get(3);
		// Two level pairs by design: a and b both finish 2-1 (+2), c and d both 1-2 (-2), with
		// identical racks inside each pair. Only the meeting between them can separate them.
		addMatch(stage, 1, 1, a, b, 5, 3, MatchStatus.COMPLETED.getValue());   // a beats b
		addMatch(stage, 1, 2, c, d, 3, 5, MatchStatus.COMPLETED.getValue());   // d beats c
		addMatch(stage, 2, 1, a, c, 3, 5, MatchStatus.COMPLETED.getValue());
		addMatch(stage, 2, 2, b, d, 5, 3, MatchStatus.COMPLETED.getValue());
		addMatch(stage, 3, 1, a, d, 5, 3, MatchStatus.COMPLETED.getValue());
		addMatch(stage, 3, 2, b, c, 5, 3, MatchStatus.COMPLETED.getValue());

		List<StandingsEntryResponse> standings = service.computeStageStandings(stage.getId());

		assertEquals(1L, standings.get(0).getParticipantId(), "the player who won the meeting ranks higher");
		assertEquals(2L, standings.get(1).getParticipantId());
		assertEquals(standings.get(0).getFrameDiff(), standings.get(1).getFrameDiff());
		// d ranks above c on the head-to-head alone — the opposite of what id order would give
		assertEquals(4L, standings.get(2).getParticipantId());
		assertEquals(3L, standings.get(3).getParticipantId());
	}

	@Test
	@DisplayName("TC-059 · Seeding breaks a tie between two players who never met")
	void TC059_computeStageStandings_seedTieBreak() {
		TournamentStage stage = addStage("PROGRESSIVE_ROUND", 1);
		Participant a = Participant.builder().id(1L).displayName("VĐV 1").seedNo(5).build();
		Participant b = Participant.builder().id(2L).displayName("VĐV 2").seedNo(2).build();
		Participant x = Participant.builder().id(3L).displayName("VĐV 3").build();
		Participant y = Participant.builder().id(4L).displayName("VĐV 4").build();
		addMatch(stage, 1, 1, a, x, 5, 0, MatchStatus.COMPLETED.getValue());
		addMatch(stage, 1, 2, b, y, 5, 0, MatchStatus.COMPLETED.getValue());

		List<StandingsEntryResponse> standings = service.computeStageStandings(stage.getId());

		assertEquals(2L, standings.get(0).getParticipantId(), "seed 2 outranks seed 5 when all else is equal");
		assertEquals(1L, standings.get(1).getParticipantId());
		assertEquals(3L, standings.get(2).getParticipantId(), "unseeded players fall back to id order");
	}

	@Test
	@DisplayName("TC-060 · A stage that records its cut-off marks who survives it")
	void TC060_computeStageStandings_advanceFlagFromStageCounts() {
		TournamentStage stage = addStage("PROGRESSIVE_ROUND", 1);
		stage.setPeActiveCount(4);
		stage.setPeEliminateCount(2);
		addFinishedRoundRobin(stage, seededPlayers(4));

		List<StandingsEntryResponse> standings = service.computeStageStandings(stage.getId());

		assertEquals(List.of(true, true, false, false),
				standings.stream().map(StandingsEntryResponse::getAdvancesToPlayoff).toList());
	}

	@Test
	@DisplayName("TC-061 · Without a cut-off recorded the advance flag is left unanswered")
	void TC061_computeStageStandings_advanceFlagUnknown() {
		TournamentStage stage = addStage("PROGRESSIVE_ROUND", 1);
		addFinishedRoundRobin(stage, seededPlayers(2));

		List<StandingsEntryResponse> standings = service.computeStageStandings(stage.getId());

		assertTrue(standings.stream().allMatch(s -> s.getAdvancesToPlayoff() == null));
	}

	// ══════════════════════════ advanceProgressiveStage ══════════════════════════

	@Test
	@DisplayName("TC-062 · A stage cannot be advanced before the tournament is under way")
	void TC062_advanceProgressiveStage_wrongStatus() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("PROGRESSIVE_ROUND_ROBIN", TournamentStatus.DRAW_DONE.getValue())));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.advanceProgressiveStage(TOURNAMENT_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-063 · A stage with matches still to play cannot be advanced")
	void TC063_advanceProgressiveStage_stageUnfinished() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("PROGRESSIVE_ROUND_ROBIN", TournamentStatus.IN_PROGRESS.getValue())));
		TournamentStage stage = addStage("PROGRESSIVE_ROUND", 1);
		stage.setPeRoundNo(1);
		List<Participant> players = seededPlayers(4);
		addMatch(stage, 1, 1, players.get(0), players.get(1), 5, 0, MatchStatus.COMPLETED.getValue());
		addMatch(stage, 1, 2, players.get(2), players.get(3), 0, 0, MatchStatus.PENDING.getValue());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.advanceProgressiveStage(TOURNAMENT_ID));

		assertEquals(ErrorCode.PROGRESSIVE_STAGE_NOT_FINISHED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-064 · Advancing with every league stage already settled")
	void TC064_advanceProgressiveStage_noStageLeft() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("PROGRESSIVE_ROUND_ROBIN", TournamentStatus.IN_PROGRESS.getValue())));
		TournamentStage done = addStage("PROGRESSIVE_ROUND", 1);
		done.setStatus(TournamentStageStatus.COMPLETED.getValue());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.advanceProgressiveStage(TOURNAMENT_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-065 · Advancing eliminates the tail and fills the next league stage")
	void TC065_advanceProgressiveStage_fillsNextStage() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("PROGRESSIVE_ROUND_ROBIN", TournamentStatus.IN_PROGRESS.getValue())));
		givenConfigValue("pe_survivors_per_stage", "6,4");
		TournamentStage first = addStage("PROGRESSIVE_ROUND", 1);
		first.setPeRoundNo(1);
		List<Participant> players = seededPlayers(8);
		addFinishedRoundRobin(first, players);
		TournamentStage second = addStage("PROGRESSIVE_ROUND", 2);
		second.setPeRoundNo(2);
		for (int round = 1; round <= 5; round++) {
			for (int pos = 1; pos <= 3; pos++) {
				addMatch(second, round, pos, null, null, 0, 0, MatchStatus.PENDING.getValue());
			}
		}
		when(participantRepository.findAllById(any())).thenAnswer(inv -> {
			Iterable<Long> ids = inv.getArgument(0);
			List<Participant> out = new ArrayList<>();
			for (Long id : ids) players.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(out::add);
			return out;
		});

		service.advanceProgressiveStage(TOURNAMENT_ID);

		assertEquals(TournamentStageStatus.COMPLETED.getValue(), first.getStatus());
		List<Match> filled = matchesOfStage(second.getId());
		assertTrue(filled.stream().allMatch(m -> m.getPlayer1() != null && m.getPlayer2() != null),
				"every placeholder of the next stage must now hold a player");
		assertTrue(filled.stream().flatMap(m -> java.util.stream.Stream.of(m.getPlayer1(), m.getPlayer2()))
				.allMatch(p -> p.getId() <= 6L), "only the six who survived may appear in stage two");
		assertEquals(ParticipantStatus.INACTIVE.getValue(), players.get(6).getStatus());
		assertEquals(ParticipantStatus.INACTIVE.getValue(), players.get(7).getStatus());
		verify(matchSchedulingService).reschedule(TOURNAMENT_ID);
	}

	@Test
	@DisplayName("TC-066 · Advancing out of the last league stage seeds the playoff bracket")
	void TC066_advanceProgressiveStage_seedsPlayoff() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("PROGRESSIVE_ROUND_ROBIN", TournamentStatus.IN_PROGRESS.getValue())));
		givenConfigValue("pe_survivors_per_stage", "4");
		TournamentStage last = addStage("PROGRESSIVE_ROUND", 1);
		last.setPeRoundNo(1);
		List<Participant> players = seededPlayers(6);
		addFinishedRoundRobin(last, players);
		TournamentStage playoff = addStage("PROGRESSIVE_PLAYOFF", 2);
		Match po1 = addMatch(playoff, 1, 1, null, null, 0, 0, MatchStatus.PENDING.getValue());
		Match po2 = addMatch(playoff, 1, 2, null, null, 0, 0, MatchStatus.PENDING.getValue());
		Match poFinal = addMatch(playoff, 2, 1, null, null, 0, 0, MatchStatus.PENDING.getValue());
		po1.setNextMatchWin(poFinal); po1.setWinSlot("player1");
		po2.setNextMatchWin(poFinal); po2.setWinSlot("player2");
		when(participantRepository.findAllById(any())).thenAnswer(inv -> {
			Iterable<Long> ids = inv.getArgument(0);
			List<Participant> out = new ArrayList<>();
			for (Long id : ids) players.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(out::add);
			return out;
		});

		service.advanceProgressiveStage(TOURNAMENT_ID);

		assertEquals(1L, po1.getPlayer1().getId());     // first vs fourth
		assertEquals(4L, po1.getPlayer2().getId());
		assertEquals(2L, po2.getPlayer1().getId());     // second vs third
		assertEquals(3L, po2.getPlayer2().getId());
	}

	// ══════════════════════════ eliminateBottomParticipants ══════════════════════════

	@Test
	@DisplayName("TC-067 · Eliminating nobody when the field is already smaller than the cut")
	void TC067_eliminateBottom_noop() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("GROUP_PLAYOFF", TournamentStatus.IN_PROGRESS.getValue())));
		TournamentStage group = addStage("GROUP", 1);
		List<Participant> players = seededPlayers(4);
		givenLeagueRoster(players);
		addFinishedRoundRobin(group, players);

		service.eliminateBottomParticipants(TOURNAMENT_ID, 8);

		verify(participantRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("TC-068 · Eliminating the tail retires those players and walks over their fixtures")
	void TC068_eliminateBottom_marksInactiveAndWalksOver() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("GROUP_PLAYOFF", TournamentStatus.IN_PROGRESS.getValue())));
		TournamentStage group = addStage("GROUP", 1);
		List<Participant> players = seededPlayers(4);
		givenLeagueRoster(players);
		addFinishedRoundRobin(group, players);
		Match pending = addMatch(group, 9, 1, players.get(0), players.get(3), 0, 0, MatchStatus.PENDING.getValue());
		when(participantRepository.findAllById(any())).thenAnswer(inv -> {
			Iterable<Long> ids = inv.getArgument(0);
			List<Participant> out = new ArrayList<>();
			for (Long id : ids) players.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(out::add);
			return out;
		});

		service.eliminateBottomParticipants(TOURNAMENT_ID, 2);

		assertEquals(ParticipantStatus.INACTIVE.getValue(), players.get(2).getStatus());
		assertEquals(ParticipantStatus.INACTIVE.getValue(), players.get(3).getStatus());
		assertEquals(ParticipantStatus.ACTIVE.getValue(), players.get(0).getStatus());
		assertEquals(MatchStatus.WALKOVER.getValue(), pending.getStatus());
		assertEquals(1L, pending.getWinner().getId());
		assertEquals(4L, pending.getLoser().getId());
	}

	@Test
	@DisplayName("TC-069 · A fixture between two eliminated players is written off entirely")
	void TC069_eliminateBottom_bothPlayersEliminated() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("GROUP_PLAYOFF", TournamentStatus.IN_PROGRESS.getValue())));
		TournamentStage group = addStage("GROUP", 1);
		List<Participant> players = seededPlayers(4);
		givenLeagueRoster(players);
		addFinishedRoundRobin(group, players);
		Match pending = addMatch(group, 9, 1, players.get(2), players.get(3), 0, 0, MatchStatus.PENDING.getValue());
		when(participantRepository.findAllById(any())).thenReturn(List.of(players.get(2), players.get(3)));

		service.eliminateBottomParticipants(TOURNAMENT_ID, 2);

		assertEquals(MatchStatus.BYE.getValue(), pending.getStatus());
		assertTrue(pending.getIsBye());
		assertNull(pending.getWinner());
	}

	@Test
	@DisplayName("TC-070 · Players may not be eliminated before the tournament is under way")
	void TC070_eliminateBottom_wrongStatus() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("GROUP_PLAYOFF", TournamentStatus.DRAW_DONE.getValue())));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.eliminateBottomParticipants(TOURNAMENT_ID, 2));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	// ══════════════════════════ populateFinalBracket ══════════════════════════

	/**
	 * A played-out CUT_TO_SE cut-off: two winners-bracket survivors, two losers-bracket survivors
	 * and a blank last-four knockout stage.
	 */
	private List<Match> givenPlayedCutOff(List<Participant> players, String lastRoundStatus) {
		TournamentStage w = addStage("WINNERS", 1);
		addMatch(w, 2, 1, players.get(0), players.get(1), 5, 0, lastRoundStatus);
		addMatch(w, 2, 2, players.get(2), players.get(3), 5, 0, lastRoundStatus);
		TournamentStage l = addStage("LOSERS", 2);
		addMatch(l, 2, 1, players.get(4), players.get(5), 5, 0, MatchStatus.COMPLETED.getValue());
		addMatch(l, 2, 2, players.get(6), players.get(7), 5, 0, MatchStatus.COMPLETED.getValue());
		TournamentStage se = addStage("FINAL_BRACKET", 3);
		Match se1 = addMatch(se, 1, 1, null, null, 0, 0, MatchStatus.PENDING.getValue());
		Match se2 = addMatch(se, 1, 2, null, null, 0, 0, MatchStatus.PENDING.getValue());
		Match seFinal = addMatch(se, 2, 1, null, null, 0, 0, MatchStatus.PENDING.getValue());
		se1.setNextMatchWin(seFinal); se1.setWinSlot("player1");
		se2.setNextMatchWin(seFinal); se2.setWinSlot("player2");
		return List.of(se1, se2, seFinal);
	}

	@Test
	@DisplayName("TC-071 · The final bracket cannot be filled before the draw is confirmed")
	void TC071_populateFinalBracket_wrongStatus() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("DOUBLE_ELIMINATION", TournamentStatus.DRAW_PREVIEW.getValue())));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.populateFinalBracket(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-072 · The final bracket cannot be filled while cut-off matches are outstanding")
	void TC072_populateFinalBracket_cutOffUnfinished() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("DOUBLE_ELIMINATION", TournamentStatus.DRAW_DONE.getValue())));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(true);
		givenPlayedCutOff(seededPlayers(8), MatchStatus.PENDING.getValue());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.populateFinalBracket(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-073 · Winners meet losers-bracket survivors in reverse order")
	void TC073_populateFinalBracket_snakeSeeding() {
		Tournament t = tournament("DOUBLE_ELIMINATION", TournamentStatus.DRAW_DONE.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(true);
		List<Match> se = givenPlayedCutOff(seededPlayers(8), MatchStatus.COMPLETED.getValue());

		service.populateFinalBracket(TOURNAMENT_ID, ACTOR_ID);

		assertEquals(1L, se.get(0).getPlayer1().getId());   // first winners survivor
		assertEquals(7L, se.get(0).getPlayer2().getId());   // last losers survivor
		assertEquals(3L, se.get(1).getPlayer1().getId());
		assertEquals(5L, se.get(1).getPlayer2().getId());
		assertEquals(TournamentStatus.FINAL_BRACKET_READY.getValue(), t.getStatus());
		assertEquals(TournamentStageStatus.COMPLETED.getValue(), stageOfType("WINNERS").getStatus());
		assertEquals(TournamentStageStatus.COMPLETED.getValue(), stageOfType("LOSERS").getStatus());
		verify(tournamentAuditService).recordChange(t, TournamentStatus.DRAW_DONE.getValue(),
				TournamentStatus.FINAL_BRACKET_READY.getValue(), ACTOR_ID, "Điền bracket loại trực tiếp (CUT_TO_SE)");
	}

	@Test
	@DisplayName("TC-074 · Filling a final bracket the tournament never had")
	void TC074_populateFinalBracket_noFinalStage() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament("DOUBLE_ELIMINATION", TournamentStatus.DRAW_DONE.getValue())));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(true);
		TournamentStage w = addStage("WINNERS", 1);
		addMatch(w, 1, 1, seededPlayers(2).get(0), seededPlayers(2).get(1), 5, 0, MatchStatus.COMPLETED.getValue());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.populateFinalBracket(TOURNAMENT_ID, ACTOR_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-075 · A final-bracket slot with no opponent becomes a bye")
	void TC075_populateFinalBracket_missingOpponentBecomesBye() {
		Tournament t = tournament("DOUBLE_ELIMINATION", TournamentStatus.DRAW_DONE.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(User.builder().id(ACTOR_ID).build()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(3L))).thenReturn(true);
		List<Participant> players = seededPlayers(8);
		TournamentStage w = addStage("WINNERS", 1);
		addMatch(w, 2, 1, players.get(0), players.get(1), 5, 0, MatchStatus.COMPLETED.getValue());
		addMatch(w, 2, 2, players.get(2), players.get(3), 5, 0, MatchStatus.COMPLETED.getValue());
		TournamentStage se = addStage("FINAL_BRACKET", 3);
		Match se1 = addMatch(se, 1, 1, null, null, 0, 0, MatchStatus.PENDING.getValue());
		Match se2 = addMatch(se, 1, 2, null, null, 0, 0, MatchStatus.PENDING.getValue());
		Match seFinal = addMatch(se, 2, 1, null, null, 0, 0, MatchStatus.PENDING.getValue());
		se1.setNextMatchWin(seFinal); se1.setWinSlot("player1");
		se2.setNextMatchWin(seFinal); se2.setWinSlot("player2");

		service.populateFinalBracket(TOURNAMENT_ID, ACTOR_ID);

		assertTrue(se1.getIsBye());
		assertEquals(MatchStatus.BYE.getValue(), se1.getStatus());
		assertEquals(1L, se1.getWinner().getId());
		assertEquals(1L, seFinal.getPlayer1().getId(), "the bye winner is carried into the next round");
	}
}
