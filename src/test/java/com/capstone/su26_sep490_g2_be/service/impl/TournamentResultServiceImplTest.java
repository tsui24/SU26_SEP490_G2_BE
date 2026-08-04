package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.PlayerPublicProfileResponse;
import com.capstone.su26_sep490_g2_be.dto.response.StandingsEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingResponse;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import com.capstone.su26_sep490_g2_be.entity.TournamentStage;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.RankingPlacementNote;
import com.capstone.su26_sep490_g2_be.enums.TournamentFormat;
import com.capstone.su26_sep490_g2_be.enums.TournamentStageType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentResultRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentStageRepository;
import com.capstone.su26_sep490_g2_be.repository.UserProfileRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link TournamentResultServiceImpl}.
 *
 * <p>Mirrors the <b>TournamentResultService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-34 (tournament rankings), UC-18 BR-05 (finalising results
 * on completion), UC-30 (public participant profile).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · TournamentResultService — UC-18, UC-30, UC-34")
class TournamentResultServiceImplTest {

	@Mock TournamentResultRepository resultRepository;
	@Mock UserRepository userRepository;
	@Mock TournamentRepository tournamentRepository;
	@Mock MatchRepository matchRepository;
	@Mock BracketGenerationService bracketGenerationService;
	@Mock ParticipantRepository participantRepository;
	@Mock UserProfileRepository userProfileRepository;
	@Mock TournamentStageRepository stageRepository;

	@InjectMocks TournamentResultServiceImpl service;

	private static final Long TOURNAMENT_ID = 77L;
	private static final Long USER_ID = 10L;

	private static Tournament tournament(String format, String status) {
		return Tournament.builder()
				.id(TOURNAMENT_ID).name("Summer Open 2026")
				.format(format).status(status)
				.build();
	}

	private static TournamentResult result(int rank) {
		return TournamentResult.builder()
				.id((long) rank)
				.participant(Participant.builder().id((long) rank).displayName("P" + rank).build())
				.finalRank(rank)
				.build();
	}

	private static Participant player(long id, String displayName) {
		return Participant.builder().id(id).displayName(displayName).build();
	}

	/** A knockout match on the main bracket — no stage, so it is never filtered out as a group match. */
	private static Match knockout(int roundNo, int positionNo, Participant winner, Participant loser) {
		return knockout(roundNo, positionNo, winner, loser, MatchStatus.COMPLETED.getValue());
	}

	private static Match knockout(int roundNo, int positionNo, Participant winner, Participant loser, String status) {
		return Match.builder()
				.id((long) (roundNo * 100 + positionNo))
				.roundNo(roundNo).positionNo(positionNo)
				.winner(winner).loser(loser).status(status)
				.build();
	}

	private static Match thirdPlaceMatch(int roundNo, Participant winner, Participant loser) {
		return Match.builder()
				.id(999L).roundNo(roundNo).positionNo(2)
				.matchCode(MatchCode.THIRD_PLACE.getValue())
				.winner(winner).loser(loser).status(MatchStatus.COMPLETED.getValue())
				.build();
	}

	private static Match groupMatch(int roundNo, int positionNo, Participant winner, Participant loser) {
		Match match = knockout(roundNo, positionNo, winner, loser);
		match.setStage(TournamentStage.builder()
				.id(1L).stageType(TournamentStageType.GROUP.getValue()).orderNo(1)
				.build());
		return match;
	}

	private static StandingsEntryResponse standing(Integer rank, long participantId, String displayName) {
		return StandingsEntryResponse.builder()
				.rank(rank).participantId(participantId).displayName(displayName)
				.build();
	}

	private static TournamentStage stage(long id, TournamentStageType type, int orderNo) {
		return TournamentStage.builder().id(id).stageType(type.getValue()).orderNo(orderNo).build();
	}

	private static TournamentRankingEntryResponse entryOf(TournamentRankingResponse response, long participantId) {
		return response.getEntries().stream()
				.filter(e -> participantId == e.getParticipantId())
				.findFirst().orElse(null);
	}

	// ══════════════════════════ getByTournament ══════════════════════════

	@Test
	@DisplayName("TC-001 · Results come back ordered by final rank")
	void TC001_getByTournament_ordered() {
		when(resultRepository.findByTournamentIdOrderByFinalRankAsc(TOURNAMENT_ID))
				.thenReturn(List.of(result(1), result(2)));

		List<TournamentResult> results = service.getByTournament(TOURNAMENT_ID);

		assertEquals(2, results.size());
		assertEquals(1, results.get(0).getFinalRank());
		verify(resultRepository).findByTournamentIdOrderByFinalRankAsc(TOURNAMENT_ID);
	}

	// ══════════════════════════ record ══════════════════════════

	@Test
	@DisplayName("TC-002 · Recording a result stamps the time when none was given")
	void TC002_record_stampsRecordedAt() {
		TournamentResult fresh = result(1);
		when(resultRepository.save(fresh)).thenAnswer(inv -> inv.getArgument(0));

		TournamentResult saved = service.record(fresh);

		assertNotNull(saved.getRecordedAt());
	}

	@Test
	@DisplayName("TC-003 · An explicit recorded time is preserved")
	void TC003_record_keepsExplicitRecordedAt() {
		Instant backdated = Instant.parse("2026-01-01T00:00:00Z");
		TournamentResult fresh = result(1);
		fresh.setRecordedAt(backdated);
		when(resultRepository.save(fresh)).thenAnswer(inv -> inv.getArgument(0));

		// A back-dated entry keyed in by the organiser must not be overwritten with "now"
		assertEquals(backdated, service.record(fresh).getRecordedAt());
	}

	// ══════════════════════ finalizeTournamentResults — UC-18 BR-05 ══════════════════════

	@Test
	@DisplayName("TC-004 · Finalising stamps the recorder onto existing results")
	void TC004_finalize_stampsExistingResults() {
		User recorder = User.builder().id(USER_ID).email("owner@example.com").build();
		List<TournamentResult> existing = List.of(result(1), result(2));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(recorder));
		when(resultRepository.findByTournamentIdOrderByFinalRankAsc(TOURNAMENT_ID)).thenReturn(existing);

		service.finalizeTournamentResults(TOURNAMENT_ID, USER_ID);

		assertEquals(recorder, existing.get(0).getRecordedBy());
		assertNotNull(existing.get(0).getRecordedAt());
		// Both rows carry the same timestamp, so the whole batch is visibly one action
		assertEquals(existing.get(0).getRecordedAt(), existing.get(1).getRecordedAt());
		verify(resultRepository).saveAll(existing);
	}

	@Test
	@DisplayName("TC-005 · Finalising with an unknown recorder")
	void TC005_finalize_unknownRecorder() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.finalizeTournamentResults(TOURNAMENT_ID, USER_ID));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
		verify(resultRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("TC-006 · No stored results are built from the live rankings")
	void TC006_finalize_buildsFromRankings() {
		User recorder = User.builder().id(USER_ID).build();
		Tournament t = tournament(TournamentFormat.GROUP_PLAYOFF.getValue(),
				TournamentStatus.COMPLETED.getValue());
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(recorder));
		when(resultRepository.findByTournamentIdOrderByFinalRankAsc(TOURNAMENT_ID)).thenReturn(List.of());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(bracketGenerationService.getLeagueStandings(TOURNAMENT_ID)).thenReturn(List.of());

		service.finalizeTournamentResults(TOURNAMENT_ID, USER_ID);

		// The table used to be filled only by demo seeding, so an empty result set falls back
		// to the rankings the bracket already knows about
		// Loaded twice: once to build the results and once inside getRankings
		verify(tournamentRepository, times(2)).findById(TOURNAMENT_ID);
		verify(resultRepository).saveAll(any());
	}

	@Test
	@DisplayName("TC-007 · Building results for a tournament that does not exist")
	void TC007_finalize_tournamentNotFound() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).build()));
		when(resultRepository.findByTournamentIdOrderByFinalRankAsc(TOURNAMENT_ID)).thenReturn(List.of());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.finalizeTournamentResults(TOURNAMENT_ID, USER_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ getRankings — UC-34 ══════════════════════════

	@Test
	@DisplayName("TC-008 · Rankings of a tournament that does not exist")
	void TC008_getRankings_tournamentNotFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getRankings(TOURNAMENT_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-009 · Rankings are official only once the tournament is completed")
	void TC009_getRankings_officialOnlyWhenCompleted() {
		Tournament completed = tournament(TournamentFormat.GROUP_PLAYOFF.getValue(),
				TournamentStatus.COMPLETED.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(completed));
		when(bracketGenerationService.getLeagueStandings(TOURNAMENT_ID)).thenReturn(List.of());

		TournamentRankingResponse response = service.getRankings(TOURNAMENT_ID);

		// UC-34.1 BR-03: the client shows a Provisional badge whenever this flag is false
		assertTrue(response.getIsOfficial());
		assertEquals(TournamentStatus.COMPLETED.getValue(), response.getTournamentStatus());
	}

	@Test
	@DisplayName("TC-010 · Rankings of a running tournament are provisional")
	void TC010_getRankings_provisionalWhileRunning() {
		Tournament running = tournament(TournamentFormat.GROUP_PLAYOFF.getValue(),
				TournamentStatus.IN_PROGRESS.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(running));
		when(bracketGenerationService.getLeagueStandings(TOURNAMENT_ID)).thenReturn(List.of());

		assertFalse(service.getRankings(TOURNAMENT_ID).getIsOfficial());
	}

	@Test
	@DisplayName("TC-011 · Group playoff rankings come from the league standings")
	void TC011_getRankings_groupPlayoffUsesStandings() {
		Tournament t = tournament(TournamentFormat.GROUP_PLAYOFF.getValue(),
				TournamentStatus.COMPLETED.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(bracketGenerationService.getLeagueStandings(TOURNAMENT_ID)).thenReturn(List.of());

		service.getRankings(TOURNAMENT_ID);

		// Each format picks a different source; a round-robin tournament must not be ranked
		// by knockout placement
		verify(bracketGenerationService).getLeagueStandings(TOURNAMENT_ID);
		verify(matchRepository, never()).findByTournamentIdOrderByRoundNoAscPositionNoAsc(anyLong());
	}

	@Test
	@DisplayName("TC-012 · Single elimination rankings come from knockout placement")
	void TC012_getRankings_singleEliminationUsesMatches() {
		Tournament t = tournament(TournamentFormat.SINGLE_ELIMINATION.getValue(),
				TournamentStatus.COMPLETED.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		lenient().when(matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(TOURNAMENT_ID))
				.thenReturn(List.of());

		service.getRankings(TOURNAMENT_ID);

		verify(bracketGenerationService, never()).getLeagueStandings(anyLong());
	}

	@Test
	@DisplayName("TC-013 · A tournament with no matches yields an empty ranking")
	void TC013_getRankings_noMatches() {
		Tournament t = tournament(TournamentFormat.SINGLE_ELIMINATION.getValue(),
				TournamentStatus.IN_PROGRESS.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		lenient().when(matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(TOURNAMENT_ID))
				.thenReturn(List.of());

		TournamentRankingResponse response = service.getRankings(TOURNAMENT_ID);

		// UC-34.1 AF-02 expects an empty list rather than an error before any match finishes
		assertNotNull(response.getEntries());
		assertTrue(response.getEntries().isEmpty());
	}

	// ══════════════════════ getParticipantProfile — UC-30 ══════════════════════

	@Test
	@DisplayName("TC-014 · Opening a participant profile that does not exist")
	void TC014_getParticipantProfile_notFound() {
		when(participantRepository.findByIdWithDetails(9999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getParticipantProfile(9999L));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-015 · A manual participant with no linked account still resolves")
	void TC015_getParticipantProfile_manualParticipant() {
		Participant manual = Participant.builder()
				.id(50L).displayName("Nguyễn Văn A").avtarUrl("avatars/a.jpg")
				.registration(null)
				.tournament(tournament(TournamentFormat.SINGLE_ELIMINATION.getValue(), TournamentStatus.COMPLETED.getValue()))
				.build();
		when(participantRepository.findByIdWithDetails(50L)).thenReturn(Optional.of(manual));

		var profile = service.getParticipantProfile(50L);

		// UC-29 BR-06: a participant added by hand has no user account, and the profile still
		// has to render rather than dereferencing a null registration
		assertNotNull(profile);
		assertEquals("Nguyễn Văn A", profile.getDisplayName());
	}

	// ══════════════ knockout placement — UC-34.1 ══════════════

	private static final Participant P1 = player(1L, "An");
	private static final Participant P2 = player(2L, "Bảo");
	private static final Participant P3 = player(3L, "Trung");
	private static final Participant P4 = player(4L, "Dũng");
	private static final Participant P5 = player(5L, "Em");
	private static final Participant P6 = player(6L, "Phong");
	private static final Participant P7 = player(7L, "Giang");
	private static final Participant P8 = player(8L, "Hải");

	/** Round 1 → semi-finals → final, eight players, every match completed. */
	private static List<Match> eightPlayerBracket() {
		return List.of(
				knockout(1, 1, P1, P5), knockout(1, 2, P3, P6),
				knockout(1, 3, P2, P7), knockout(1, 4, P4, P8),
				knockout(2, 1, P1, P3), knockout(2, 2, P2, P4),
				knockout(3, 1, P1, P2));
	}

	private TournamentRankingResponse rankKnockout(List<Match> matches) {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentFormat.SINGLE_ELIMINATION.getValue(), TournamentStatus.COMPLETED.getValue())));
		when(matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(TOURNAMENT_ID)).thenReturn(matches);
		return service.getRankings(TOURNAMENT_ID);
	}

	@Test
	@DisplayName("TC-016 · An eight-player bracket places every eliminated round")
	void TC016_getRankings_eightPlayerPlacement() {
		TournamentRankingResponse response = rankKnockout(eightPlayerBracket());

		assertEquals(8, response.getEntries().size());
		assertEquals("#1", entryOf(response, 1L).getRankLabel());
		assertEquals(RankingPlacementNote.CHAMPION.getValue(), entryOf(response, 1L).getNote());
		assertEquals("#2", entryOf(response, 2L).getRankLabel());
		// No third-place match, so the two beaten semi-finalists share #3-4
		assertEquals("#3-4", entryOf(response, 3L).getRankLabel());
		assertEquals("#3-4", entryOf(response, 4L).getRankLabel());
		// Round one losers share #5-8 by the 2^(maxRound - elimRound) rule
		assertEquals("#5-8", entryOf(response, 5L).getRankLabel());
		assertEquals(5, entryOf(response, 8L).getRankFrom());
		assertEquals(8, entryOf(response, 8L).getRankTo());
	}

	@Test
	@DisplayName("TC-017 · Entries are ordered by rank and then alphabetically inside a shared band")
	void TC017_getRankings_sortedByRankThenName() {
		TournamentRankingResponse response = rankKnockout(eightPlayerBracket());

		List<String> names = response.getEntries().stream()
				.map(TournamentRankingEntryResponse::getDisplayName).toList();
		assertEquals("An", names.get(0));
		assertEquals("Bảo", names.get(1));
		// Both are #3-4, so the tie is broken by name rather than by match order
		assertEquals("Dũng", names.get(2));
		assertEquals("Trung", names.get(3));
	}

	@Test
	@DisplayName("TC-018 · A third-place match splits the pair into #3 and #4")
	void TC018_getRankings_thirdPlaceMatch() {
		List<Match> matches = new java.util.ArrayList<>(eightPlayerBracket());
		matches.add(thirdPlaceMatch(3, P4, P3));

		TournamentRankingResponse response = rankKnockout(matches);

		assertEquals("#3", entryOf(response, 4L).getRankLabel());
		assertEquals(RankingPlacementNote.THIRD_PLACE.getValue(), entryOf(response, 4L).getNote());
		assertEquals("#4", entryOf(response, 3L).getRankLabel());
		// The play-off for third replaces the shared band rather than adding to it
		assertEquals(8, response.getEntries().size());
	}

	@Test
	@DisplayName("TC-019 · An unfinished final leaves the top two unranked")
	void TC019_getRankings_unfinishedFinal() {
		List<Match> matches = new java.util.ArrayList<>(eightPlayerBracket().subList(0, 6));
		matches.add(knockout(3, 1, null, null, MatchStatus.IN_PROGRESS.getValue()));

		TournamentRankingResponse response = rankKnockout(matches);

		// The two finalists are still playing, so they hold no placement yet
		assertNull(entryOf(response, 1L));
		assertNull(entryOf(response, 2L));
		assertEquals("#3-4", entryOf(response, 3L).getRankLabel());
		assertEquals(6, response.getEntries().size());
	}

	@Test
	@DisplayName("TC-020 · A walkover counts as a finished match")
	void TC020_getRankings_walkoverCountsAsFinished() {
		List<Match> matches = List.of(
				knockout(1, 1, P1, P3, MatchStatus.WALKOVER.getValue()),
				knockout(1, 2, P2, P4, MatchStatus.COMPLETED.getValue()),
				knockout(2, 1, P1, P2, MatchStatus.COMPLETED.getValue()));

		TournamentRankingResponse response = rankKnockout(matches);

		// A player who advanced because the opponent never turned up is still placed
		assertEquals("#3-4", entryOf(response, 3L).getRankLabel());
		assertEquals(4, response.getEntries().size());
	}

	@Test
	@DisplayName("TC-021 · A pending match produces no placement")
	void TC021_getRankings_pendingMatchIgnored() {
		List<Match> matches = List.of(
				knockout(1, 1, P1, P3, MatchStatus.COMPLETED.getValue()),
				knockout(1, 2, null, null, MatchStatus.PENDING.getValue()),
				knockout(2, 1, P1, P2, MatchStatus.COMPLETED.getValue()));

		TournamentRankingResponse response = rankKnockout(matches);

		// Three entries only: the champion, the runner-up and the one player actually knocked out
		assertEquals(3, response.getEntries().size());
		assertNull(entryOf(response, 4L));
	}

	@Test
	@DisplayName("TC-022 · Group-stage matches are left out of the knockout placement")
	void TC022_getRankings_groupMatchesExcluded() {
		List<Match> matches = List.of(
				groupMatch(1, 1, P1, P5), groupMatch(1, 2, P2, P6),
				knockout(1, 1, P1, P3), knockout(1, 2, P2, P4),
				knockout(2, 1, P1, P2));

		TournamentRankingResponse response = rankKnockout(matches);

		// Losing a round-robin game does not knock a player out, so it must not create a placement
		assertEquals(4, response.getEntries().size());
		assertNull(entryOf(response, 5L));
		assertNull(entryOf(response, 6L));
	}

	@Test
	@DisplayName("TC-023 · A bracket holding only a third-place match yields no ranking")
	void TC023_getRankings_onlyThirdPlaceMatch() {
		TournamentRankingResponse response = rankKnockout(List.of(thirdPlaceMatch(1, P1, P2)));

		// The third-place match is excluded when the highest round is computed, so there is no
		// main bracket to rank at all
		assertTrue(response.getEntries().isEmpty());
	}

	@Test
	@DisplayName("TC-024 · A player knocked out twice is ranked once")
	void TC024_getRankings_playerPlacedOnlyOnce() {
		List<Match> matches = List.of(
				knockout(1, 1, P1, P3), knockout(1, 2, P2, P3),
				knockout(2, 1, P1, P2));

		TournamentRankingResponse response = rankKnockout(matches);

		// The losers bracket of a double elimination can knock the same player out twice
		assertEquals(3, response.getEntries().size());
		assertEquals(1, response.getEntries().stream()
				.filter(e -> e.getParticipantId() == 3L).count());
	}

	// ══════════════ league standings — UC-34.2 ══════════════

	@Test
	@DisplayName("TC-025 · Round-robin players each get their own rank")
	void TC025_getRankings_groupStandingsRankEachPlayer() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentFormat.GROUP_PLAYOFF.getValue(), TournamentStatus.COMPLETED.getValue())));
		when(bracketGenerationService.getLeagueStandings(TOURNAMENT_ID)).thenReturn(List.of(
				standing(1, 1L, "An"), standing(2, 2L, "Bảo"), standing(3, 3L, "Trung")));

		TournamentRankingResponse response = service.getRankings(TOURNAMENT_ID);

		// A league table already separates equal records, so nobody shares a band here
		assertEquals("#1", entryOf(response, 1L).getRankLabel());
		assertEquals("#2", entryOf(response, 2L).getRankLabel());
		assertEquals("#3", entryOf(response, 3L).getRankLabel());
		assertEquals(RankingPlacementNote.GROUP_LEADER.getValue(), entryOf(response, 1L).getNote());
		assertNull(entryOf(response, 2L).getNote());
	}

	@Test
	@DisplayName("TC-026 · A standings row with no rank falls back to its position")
	void TC026_getRankings_standingWithoutRank() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentFormat.GROUP_PLAYOFF.getValue(), TournamentStatus.IN_PROGRESS.getValue())));
		when(bracketGenerationService.getLeagueStandings(TOURNAMENT_ID)).thenReturn(List.of(
				standing(null, 1L, "An"), standing(null, 2L, "Bảo")));

		TournamentRankingResponse response = service.getRankings(TOURNAMENT_ID);

		// Mid-tournament the standings may not carry a rank yet; the list order stands in for it
		assertEquals(1, entryOf(response, 1L).getRankFrom());
		assertEquals(2, entryOf(response, 2L).getRankFrom());
	}

	// ══════════════ progressive round robin — UC-34.3 ══════════════

	@Test
	@DisplayName("TC-027 · The playoff decides the top places and the league stages fill the rest")
	void TC027_getRankings_progressivePlayoffFirst() {
		Tournament t = tournament(TournamentFormat.PROGRESSIVE_ROUND_ROBIN.getValue(),
				TournamentStatus.COMPLETED.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(stageRepository.findByTournamentIdOrderByOrderNoAsc(TOURNAMENT_ID)).thenReturn(List.of(
				stage(10L, TournamentStageType.PROGRESSIVE_ROUND, 1),
				stage(11L, TournamentStageType.PROGRESSIVE_ROUND, 2),
				stage(12L, TournamentStageType.PROGRESSIVE_PLAYOFF, 3)));
		when(matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(12L)).thenReturn(List.of(
				knockout(1, 1, P1, P3), knockout(1, 2, P2, P4),
				knockout(2, 1, P1, P2)));
		when(bracketGenerationService.computeStageStandings(11L)).thenReturn(List.of(
				standing(1, 5L, "Em"), standing(2, 6L, "Phong")));
		when(bracketGenerationService.computeStageStandings(10L)).thenReturn(List.of(
				standing(1, 7L, "Giang")));

		TournamentRankingResponse response = service.getRankings(TOURNAMENT_ID);

		assertEquals("#1", entryOf(response, 1L).getRankLabel());
		assertEquals("#3-4", entryOf(response, 3L).getRankLabel());
		// The later a player survived, the higher they rank, so stage 2 is read before stage 1
		assertEquals(5, entryOf(response, 5L).getRankFrom());
		assertEquals(6, entryOf(response, 6L).getRankFrom());
		assertEquals(7, entryOf(response, 7L).getRankFrom());
	}

	@Test
	@DisplayName("TC-028 · With no playoff generated the league stages carry the whole ranking")
	void TC028_getRankings_progressiveWithoutPlayoff() {
		Tournament t = tournament(TournamentFormat.PROGRESSIVE_ROUND_ROBIN.getValue(),
				TournamentStatus.IN_PROGRESS.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(stageRepository.findByTournamentIdOrderByOrderNoAsc(TOURNAMENT_ID)).thenReturn(List.of(
				stage(10L, TournamentStageType.PROGRESSIVE_ROUND, 1)));
		when(bracketGenerationService.computeStageStandings(10L)).thenReturn(List.of(
				standing(1, 1L, "An"), standing(2, 2L, "Bảo")));

		TournamentRankingResponse response = service.getRankings(TOURNAMENT_ID);

		assertEquals(2, response.getEntries().size());
		assertEquals(1, entryOf(response, 1L).getRankFrom());
		verify(matchRepository, never()).findByStageIdOrderByRoundNoAscPositionNoAsc(anyLong());
	}

	@Test
	@DisplayName("TC-029 · A player already placed by the playoff is not ranked twice")
	void TC029_getRankings_progressiveNoDoublePlacement() {
		Tournament t = tournament(TournamentFormat.PROGRESSIVE_ROUND_ROBIN.getValue(),
				TournamentStatus.COMPLETED.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(stageRepository.findByTournamentIdOrderByOrderNoAsc(TOURNAMENT_ID)).thenReturn(List.of(
				stage(10L, TournamentStageType.PROGRESSIVE_ROUND, 1),
				stage(12L, TournamentStageType.PROGRESSIVE_PLAYOFF, 2)));
		when(matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(12L)).thenReturn(List.of(
				knockout(1, 1, P1, P2)));
		// The finalists also appear in the last league table they played in
		when(bracketGenerationService.computeStageStandings(10L)).thenReturn(List.of(
				standing(1, 1L, "An"), standing(2, 2L, "Bảo"), standing(3, 3L, "Trung")));

		TournamentRankingResponse response = service.getRankings(TOURNAMENT_ID);

		assertEquals(3, response.getEntries().size());
		assertEquals("#1", entryOf(response, 1L).getRankLabel());
		assertEquals("#2", entryOf(response, 2L).getRankLabel());
		assertEquals(3, entryOf(response, 3L).getRankFrom());
	}

	@Test
	@DisplayName("TC-030 · A playoff stage with no match generated yet places nobody")
	void TC030_getRankings_progressiveEmptyPlayoffStage() {
		Tournament t = tournament(TournamentFormat.PROGRESSIVE_ROUND_ROBIN.getValue(),
				TournamentStatus.IN_PROGRESS.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(stageRepository.findByTournamentIdOrderByOrderNoAsc(TOURNAMENT_ID)).thenReturn(List.of(
				stage(10L, TournamentStageType.PROGRESSIVE_ROUND, 1),
				stage(12L, TournamentStageType.PROGRESSIVE_PLAYOFF, 2)));
		when(matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(12L)).thenReturn(List.of());
		when(bracketGenerationService.computeStageStandings(10L)).thenReturn(List.of(
				standing(1, 1L, "An")));

		TournamentRankingResponse response = service.getRankings(TOURNAMENT_ID);

		// The stage row exists before the bracket is drawn, which must not shift the league ranks
		assertEquals(1, response.getEntries().size());
		assertEquals(1, entryOf(response, 1L).getRankFrom());
	}

	// ══════════════ buildResultsFromRankings — UC-18 BR-05 ══════════════

	@Test
	@DisplayName("TC-031 · Results built from the rankings carry no prize and no points")
	void TC031_finalize_buildsResultsWithZeroPrize() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).build()));
		when(resultRepository.findByTournamentIdOrderByFinalRankAsc(TOURNAMENT_ID)).thenReturn(List.of());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentFormat.GROUP_PLAYOFF.getValue(), TournamentStatus.COMPLETED.getValue())));
		when(bracketGenerationService.getLeagueStandings(TOURNAMENT_ID)).thenReturn(List.of(
				standing(1, 1L, "An"), standing(2, 2L, "Bảo")));
		when(participantRepository.findById(1L)).thenReturn(Optional.of(P1));
		when(participantRepository.findById(2L)).thenReturn(Optional.of(P2));

		service.finalizeTournamentResults(TOURNAMENT_ID, USER_ID);

		ArgumentCaptor<List<TournamentResult>> saved = ArgumentCaptor.forClass(List.class);
		verify(resultRepository).saveAll(saved.capture());
		assertEquals(2, saved.getValue().size());
		assertEquals(1, saved.getValue().get(0).getFinalRank());
		// There is no prize formula in the system, so a guessed amount would be worse than zero
		assertEquals(BigDecimal.ZERO, saved.getValue().get(0).getPrizeAmount());
		assertEquals(0, saved.getValue().get(0).getPointsEarned());
		assertEquals(RankingPlacementNote.GROUP_LEADER.getValue(), saved.getValue().get(0).getNote());
	}

	@Test
	@DisplayName("TC-032 · A ranking entry whose participant is gone is skipped")
	void TC032_finalize_skipsMissingParticipant() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).build()));
		when(resultRepository.findByTournamentIdOrderByFinalRankAsc(TOURNAMENT_ID)).thenReturn(List.of());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentFormat.GROUP_PLAYOFF.getValue(), TournamentStatus.COMPLETED.getValue())));
		when(bracketGenerationService.getLeagueStandings(TOURNAMENT_ID)).thenReturn(List.of(
				standing(1, 1L, "An"), standing(2, 2L, "Bảo")));
		when(participantRepository.findById(1L)).thenReturn(Optional.of(P1));
		when(participantRepository.findById(2L)).thenReturn(Optional.empty());

		service.finalizeTournamentResults(TOURNAMENT_ID, USER_ID);

		ArgumentCaptor<List<TournamentResult>> saved = ArgumentCaptor.forClass(List.class);
		verify(resultRepository).saveAll(saved.capture());
		// A participant withdrawn after the ranking was computed must not abort the whole batch
		assertEquals(1, saved.getValue().size());
	}

	// ══════════════ player profiles — UC-30 ══════════════

	private static Participant linkedParticipant(String avatarUrl) {
		return Participant.builder()
				.id(50L).displayName("Nguyễn Văn A").avtarUrl(avatarUrl).seedNo(3)
				.registration(Registration.builder().id(5L).user(User.builder().id(USER_ID).build()).build())
				.tournament(tournament(TournamentFormat.SINGLE_ELIMINATION.getValue(),
						TournamentStatus.COMPLETED.getValue()))
				.build();
	}

	private static UserProfile profile() {
		return UserProfile.builder()
				.userId(USER_ID).fullName("Nguyễn Văn An").avatarUrl("profiles/an.jpg")
				.billiardRank("A").bio("Cơ thủ phong trào")
				.build();
	}

	@Test
	@DisplayName("TC-033 · A linked account fills in the rank, the bio and the account name")
	void TC033_getParticipantProfile_linkedAccount() {
		when(participantRepository.findByIdWithDetails(50L)).thenReturn(Optional.of(linkedParticipant(null)));
		when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile()));
		when(resultRepository.findByParticipantRegistrationUserId(USER_ID)).thenReturn(List.of());

		PlayerPublicProfileResponse response = service.getParticipantProfile(50L);

		assertEquals(USER_ID, response.getUserId());
		assertEquals("Nguyễn Văn An", response.getAccountName());
		assertEquals("A", response.getBilliardRank());
		assertEquals("Cơ thủ phong trào", response.getBio());
		// The entry name is what the bracket shows, so it stays the display name
		assertEquals("Nguyễn Văn A", response.getDisplayName());
		assertEquals(3, response.getSeedNo());
	}

	@Test
	@DisplayName("TC-034 · The avatar chosen for the tournament wins over the account avatar")
	void TC034_getParticipantProfile_participantAvatarWins() {
		when(participantRepository.findByIdWithDetails(50L))
				.thenReturn(Optional.of(linkedParticipant("participants/a.jpg")));
		when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile()));
		when(resultRepository.findByParticipantRegistrationUserId(USER_ID)).thenReturn(List.of());

		assertEquals("participants/a.jpg", service.getParticipantProfile(50L).getAvatarUrl());
	}

	@Test
	@DisplayName("TC-035 · A blank tournament avatar falls back to the account avatar")
	void TC035_getParticipantProfile_avatarFallback() {
		when(participantRepository.findByIdWithDetails(50L)).thenReturn(Optional.of(linkedParticipant("   ")));
		when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile()));
		when(resultRepository.findByParticipantRegistrationUserId(USER_ID)).thenReturn(List.of());

		// A whitespace-only column is as empty as null, and the profile picture is better than none
		assertEquals("profiles/an.jpg", service.getParticipantProfile(50L).getAvatarUrl());
	}

	@Test
	@DisplayName("TC-036 · An achievement counts as official only once its tournament is completed")
	void TC036_getParticipantProfile_officialAchievementsOnly() {
		TournamentResult finished = TournamentResult.builder()
				.id(1L).finalRank(1).prizeAmount(new BigDecimal("5000000")).pointsEarned(100)
				.note(RankingPlacementNote.CHAMPION.getValue())
				.tournament(tournament(TournamentFormat.SINGLE_ELIMINATION.getValue(),
						TournamentStatus.COMPLETED.getValue()))
				.build();
		TournamentResult running = TournamentResult.builder()
				.id(2L).finalRank(3).prizeAmount(BigDecimal.ZERO).pointsEarned(0)
				.tournament(Tournament.builder().id(78L).name("Autumn Cup")
						.status(TournamentStatus.IN_PROGRESS.getValue()).build())
				.build();
		when(participantRepository.findByIdWithDetails(50L)).thenReturn(Optional.of(linkedParticipant(null)));
		when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile()));
		when(resultRepository.findByParticipantRegistrationUserId(USER_ID))
				.thenReturn(List.of(finished, running));

		PlayerPublicProfileResponse response = service.getParticipantProfile(50L);

		assertEquals(2, response.getAchievements().size());
		assertEquals("#1", response.getAchievements().get(0).getRankLabel());
		assertTrue(response.getAchievements().get(0).getIsOfficial());
		// A rank taken from a tournament still being played is provisional
		assertFalse(response.getAchievements().get(1).getIsOfficial());
	}

	@Test
	@DisplayName("TC-037 · A participant with no account reads the single result of that tournament")
	void TC037_getParticipantProfile_manualParticipantAchievement() {
		Participant manual = Participant.builder()
				.id(51L).displayName("Khách mời")
				.tournament(tournament(TournamentFormat.SINGLE_ELIMINATION.getValue(),
						TournamentStatus.COMPLETED.getValue()))
				.build();
		TournamentResult stored = TournamentResult.builder()
				.id(3L).finalRank(2).prizeAmount(BigDecimal.ZERO).pointsEarned(0)
				.tournament(manual.getTournament())
				.build();
		when(participantRepository.findByIdWithDetails(51L)).thenReturn(Optional.of(manual));
		when(resultRepository.findByTournamentIdAndParticipantId(TOURNAMENT_ID, 51L))
				.thenReturn(Optional.of(stored));

		PlayerPublicProfileResponse response = service.getParticipantProfile(51L);

		// Without an account there is no history to gather, only this tournament's own result
		assertNull(response.getUserId());
		assertEquals(1, response.getAchievements().size());
		assertEquals("#2", response.getAchievements().get(0).getRankLabel());
		verify(resultRepository, never()).findByParticipantRegistrationUserId(anyLong());
	}

	@Test
	@DisplayName("TC-038 · A profile opened by account id names the most recent entry")
	void TC038_getPlayerProfileByUserId_latestParticipant() {
		when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile()));
		when(resultRepository.findByParticipantRegistrationUserId(USER_ID)).thenReturn(List.of());
		when(participantRepository.findByRegistrationUserId(USER_ID))
				.thenReturn(List.of(player(40L, "Cũ"), player(41L, "Mới")));

		PlayerPublicProfileResponse response = service.getPlayerProfileByUserId(USER_ID);

		// The last row is the newest entry, and its id is what the profile links back to
		assertEquals(41L, response.getParticipantId());
		assertEquals("Nguyễn Văn An", response.getDisplayName());
		assertEquals("A", response.getBilliardRank());
	}

	@Test
	@DisplayName("TC-039 · An account with no profile row falls back to the entry name")
	void TC039_getPlayerProfileByUserId_noProfileRow() {
		when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
		when(resultRepository.findByParticipantRegistrationUserId(USER_ID)).thenReturn(List.of());
		when(participantRepository.findByRegistrationUserId(USER_ID))
				.thenReturn(List.of(player(41L, "Nguyễn Văn A")));

		PlayerPublicProfileResponse response = service.getPlayerProfileByUserId(USER_ID);

		// A Player who signed up but never filled in their profile still gets a readable page
		assertEquals("Nguyễn Văn A", response.getDisplayName());
		assertNull(response.getAccountName());
		assertNull(response.getBilliardRank());
	}

	@Test
	@DisplayName("TC-040 · An account that has never entered a tournament still resolves")
	void TC040_getPlayerProfileByUserId_noParticipation() {
		when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile()));
		when(resultRepository.findByParticipantRegistrationUserId(USER_ID)).thenReturn(List.of());
		when(participantRepository.findByRegistrationUserId(USER_ID)).thenReturn(List.of());

		PlayerPublicProfileResponse response = service.getPlayerProfileByUserId(USER_ID);

		assertNull(response.getParticipantId());
		assertEquals("Nguyễn Văn An", response.getDisplayName());
		assertTrue(response.getAchievements().isEmpty());
	}
}
