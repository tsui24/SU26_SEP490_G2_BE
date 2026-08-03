package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingResponse;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.TournamentFormat;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
