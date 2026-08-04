package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.AssignMatchRequest;
import com.capstone.su26_sep490_g2_be.dto.response.StaffBriefResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.MatchScoreEvent;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.BilliardTableRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchScoreEventRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MatchSchedulingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link MatchServiceImpl}.
 *
 * <p>Mirrors the <b>MatchService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-40 (referee assignment and scheduling), UC-41 (score entry and
 * winner advancement).
 *
 * <p>This is where a result becomes irreversible: completing a match writes the winner into the
 * next round. Most cases below guard that moment — who may record it, whether the score supports
 * it, and where the winner and loser are placed afterwards.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · MatchService — UC-40, UC-41")
class MatchServiceImplTest {

	@Mock MatchRepository matchRepository;
	@Mock MatchScoreEventRepository scoreEventRepository;
	@Mock ParticipantRepository participantRepository;
	@Mock UserRepository userRepository;
	@Mock TournamentRepository tournamentRepository;
	@Mock BilliardTableRepository billiardTableRepository;
	@Mock ApplicationEventPublisher eventPublisher;
	@Mock MailContextBuilder mailContextBuilder;
	@Mock MatchSchedulingService matchSchedulingService;
	@Mock BranchAccessService branchAccessService;

	@InjectMocks MatchServiceImpl service;

	private static final Long MATCH_ID = 11L;
	private static final Long OWNER_ID = 7L;
	private static final Long STAFF_ID = 3L;
	private static final Long BRANCH_ID = 2L;
	private static final Long TOURNAMENT_ID = 500L;

	@BeforeEach
	void wireCommonInfrastructure() {
		lenient().when(mailContextBuilder.systemContext()).thenReturn(new HashMap<>());
		lenient().when(matchSchedulingService.estimateDurationMinutes(any(Match.class))).thenReturn(60L);
	}

	// ══════════════════════════ fixtures ══════════════════════════

	private static User user(Long id, String roleCode, Branch branch) {
		return User.builder().id(id).email("u" + id + "@btms.vn")
				.role(Role.builder().id(1L).code(roleCode).build())
				.branch(branch)
				.build();
	}

	private static Branch branch() {
		return Branch.builder().id(BRANCH_ID).name("Chi nhánh Quận 1")
				.owner(User.builder().id(OWNER_ID).build()).build();
	}

	private static Tournament tournament(String status) {
		return Tournament.builder()
				.id(TOURNAMENT_ID).name("Summer Open 2026").status(status)
				.format("SINGLE_ELIMINATION").tableCount(4).branch(branch())
				.startAt(Instant.now().minus(1, ChronoUnit.DAYS))
				.build();
	}

	private static Participant player(Long id, String name) {
		return Participant.builder().id(id).displayName(name)
				.registration(Registration.builder().id(id)
						.user(User.builder().id(100 + id).email("p" + id + "@btms.vn").build()).build())
				.build();
	}

	private static Match match(Tournament t, String status, Participant p1, Participant p2, Integer raceTo) {
		return Match.builder()
				.id(MATCH_ID).tournament(t).roundNo(1).positionNo(1).matchCode("R1-M1")
				.status(status).player1(p1).player2(p2)
				.player1Score(0).player2Score(0).raceTo(raceTo).isBye(false).scheduleLocked(false)
				.build();
	}

	private void givenOwnerCanOperate() {
		lenient().when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER", null)));
		lenient().when(branchAccessService.canActorAccessBranch(any(User.class), eq(BRANCH_ID))).thenReturn(true);
	}

	private void givenMatchSaveEchoes() {
		lenient().when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	// ══════════════════════════ access control ══════════════════════════

	@Test
	@DisplayName("TC-001 · Reading a match that does not exist")
	void TC001_getById_notFound() {
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(MATCH_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-002 · A manager may not operate on a match outside their branches")
	void TC002_assertActor_branchDenied() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "MANAGER", null)));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(BRANCH_ID))).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.startMatch(MATCH_ID, OWNER_ID));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-003 · A referee may only operate on the matches assigned to them")
	void TC003_assertActor_staffMustBeAssigned() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(user(STAFF_ID, "STAFF", branch())));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.startMatch(MATCH_ID, STAFF_ID));

		assertEquals(ErrorCode.MATCH_NOT_ASSIGNED, ex.getErrorCode(),
				"branch access is not enough for staff — the assignment itself is the permission");
	}

	@Test
	@DisplayName("TC-004 · The assigned referee passes the assignment check")
	void TC004_assertStaffAssigned_passes() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		m.setAssignedStaff(user(STAFF_ID, "STAFF", branch()));
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));

		service.assertStaffAssigned(MATCH_ID, STAFF_ID);      // must not throw
	}

	@Test
	@DisplayName("TC-005 · Operating under an account that no longer exists")
	void TC005_assertActor_userNotFound() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.startMatch(MATCH_ID, OWNER_ID));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ startMatch ══════════════════════════

	@Test
	@DisplayName("TC-006 · Starting a match records the kick-off in the score log")
	void TC006_startMatch_happyPath() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();

		Match started = service.startMatch(MATCH_ID, OWNER_ID);

		assertEquals(MatchStatus.IN_PROGRESS.getValue(), started.getStatus());
		ArgumentCaptor<MatchScoreEvent> event = ArgumentCaptor.forClass(MatchScoreEvent.class);
		verify(scoreEventRepository).save(event.capture());
		assertEquals("MATCH_START", event.getValue().getEventType());
	}

	@Test
	@DisplayName("TC-007 · A match cannot be started before the tournament is under way")
	void TC007_startMatch_tournamentNotStarted() {
		Match m = match(tournament(TournamentStatus.DRAW_DONE.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();

		BusinessException ex = assertThrows(BusinessException.class, () -> service.startMatch(MATCH_ID, OWNER_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-008 · Double elimination may be played straight after the draw is confirmed")
	void TC008_startMatch_doubleEliminationPlayableAtDrawDone() {
		Tournament t = tournament(TournamentStatus.DRAW_DONE.getValue());
		t.setFormat("DOUBLE_ELIMINATION");
		Match m = match(t, MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();

		assertEquals(MatchStatus.IN_PROGRESS.getValue(), service.startMatch(MATCH_ID, OWNER_ID).getStatus(),
				"the DE rounds have no separate start button, so DRAW_DONE has to be playable");
	}

	@Test
	@DisplayName("TC-009 · A match already under way cannot be started again")
	void TC009_startMatch_alreadyInProgress() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.IN_PROGRESS.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();

		BusinessException ex = assertThrows(BusinessException.class, () -> service.startMatch(MATCH_ID, OWNER_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-010 · A match waiting on a player cannot be started")
	void TC010_startMatch_missingPlayer() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), null, 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();

		BusinessException ex = assertThrows(BusinessException.class, () -> service.startMatch(MATCH_ID, OWNER_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode(),
				"the other half of the bracket has not produced an opponent yet");
	}

	// ══════════════════════════ updateScore ══════════════════════════

	@Test
	@DisplayName("TC-011 · Recording a score puts the match under way and logs the change")
	void TC011_updateScore_happyPath() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();

		Match updated = service.updateScore(MATCH_ID, 3, 2, OWNER_ID);

		assertEquals(3, updated.getPlayer1Score());
		assertEquals(2, updated.getPlayer2Score());
		assertEquals(MatchStatus.IN_PROGRESS.getValue(), updated.getStatus());
		verify(scoreEventRepository).save(any(MatchScoreEvent.class));
	}

	@Test
	@DisplayName("TC-012 · A finished match cannot have its score rewritten")
	void TC012_updateScore_completedRefused() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.COMPLETED.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateScore(MATCH_ID, 5, 0, OWNER_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-013 · A walkover cannot have a score entered against it")
	void TC013_updateScore_walkoverRefused() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.WALKOVER.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateScore(MATCH_ID, 5, 0, OWNER_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	// ══════════════════════════ incrementScore ══════════════════════════

	private Match givenLiveMatchForReferee(int p1, int p2, Integer raceTo) {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.IN_PROGRESS.getValue(), player(1L, "A"), player(2L, "B"), raceTo);
		m.setPlayer1Score(p1);
		m.setPlayer2Score(p2);
		m.setAssignedStaff(user(STAFF_ID, "STAFF", branch()));
		lenient().when(matchRepository.findByIdForUpdate(MATCH_ID)).thenReturn(Optional.of(m));
		lenient().when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(user(STAFF_ID, "STAFF", branch())));
		givenMatchSaveEchoes();
		return m;
	}

	@Test
	@DisplayName("TC-014 · A referee adds a rack to the player who won it")
	void TC014_incrementScore_addsRack() {
		givenLiveMatchForReferee(2, 1, 5);

		Match updated = service.incrementScore(MATCH_ID, 1, 1, STAFF_ID);

		assertEquals(3, updated.getPlayer1Score());
		assertEquals(1, updated.getPlayer2Score());
	}

	@Test
	@DisplayName("TC-015 · A rack awarded by mistake can be taken back")
	void TC015_incrementScore_undo() {
		givenLiveMatchForReferee(2, 1, 5);

		assertEquals(1, service.incrementScore(MATCH_ID, 1, -1, STAFF_ID).getPlayer1Score());
	}

	@Test
	@DisplayName("TC-016 · Scoring stops once somebody has reached the race")
	void TC016_incrementScore_lockedAtRaceTo() {
		givenLiveMatchForReferee(5, 3, 5);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.incrementScore(MATCH_ID, 2, 1, STAFF_ID));

		assertEquals(ErrorCode.MATCH_SCORE_LOCKED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-017 · A mistake can still be undone after the race is reached")
	void TC017_incrementScore_undoStillAllowedAtRaceTo() {
		givenLiveMatchForReferee(5, 3, 5);

		assertEquals(4, service.incrementScore(MATCH_ID, 1, -1, STAFF_ID).getPlayer1Score(),
				"the referee must be able to correct a wrongly awarded winning rack");
	}

	@Test
	@DisplayName("TC-018 · A score can never fall below zero")
	void TC018_incrementScore_belowZero() {
		givenLiveMatchForReferee(0, 0, 5);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.incrementScore(MATCH_ID, 1, -1, STAFF_ID));

		assertEquals(ErrorCode.MATCH_SCORE_OUT_OF_RANGE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-019 · Only the two player slots exist")
	void TC019_incrementScore_invalidSlot() {
		givenLiveMatchForReferee(0, 0, 5);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.incrementScore(MATCH_ID, 3, 1, STAFF_ID));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-020 · Racks are counted one at a time")
	void TC020_incrementScore_invalidDelta() {
		givenLiveMatchForReferee(0, 0, 5);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.incrementScore(MATCH_ID, 1, 3, STAFF_ID));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode(),
				"a jump of three racks is a typo, not a score");
	}

	@Test
	@DisplayName("TC-021 · Racks cannot be scored on a match that is not under way")
	void TC021_incrementScore_notInProgress() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		m.setAssignedStaff(user(STAFF_ID, "STAFF", branch()));
		when(matchRepository.findByIdForUpdate(MATCH_ID)).thenReturn(Optional.of(m));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.incrementScore(MATCH_ID, 1, 1, STAFF_ID));

		assertEquals(ErrorCode.MATCH_NOT_IN_PROGRESS, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-022 · Only the assigned referee may score the match")
	void TC022_incrementScore_notAssigned() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.IN_PROGRESS.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findByIdForUpdate(MATCH_ID)).thenReturn(Optional.of(m));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.incrementScore(MATCH_ID, 1, 1, STAFF_ID));

		assertEquals(ErrorCode.MATCH_NOT_ASSIGNED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-023 · A match with no race declared has no scoring ceiling")
	void TC023_incrementScore_noRaceTo() {
		givenLiveMatchForReferee(99, 0, null);

		assertEquals(100, service.incrementScore(MATCH_ID, 1, 1, STAFF_ID).getPlayer1Score());
	}

	// ══════════════════════════ completeMatch ══════════════════════════

	private Match givenCompletableMatch(int p1, int p2, Integer raceTo) {
		Participant a = player(1L, "VĐV A");
		Participant b = player(2L, "VĐV B");
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.IN_PROGRESS.getValue(), a, b, raceTo);
		m.setPlayer1Score(p1);
		m.setPlayer2Score(p2);
		lenient().when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		lenient().when(participantRepository.findById(1L)).thenReturn(Optional.of(a));
		lenient().when(participantRepository.findById(2L)).thenReturn(Optional.of(b));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();
		return m;
	}

	@Test
	@DisplayName("TC-024 · Completing a match records the winner, the loser and the result")
	void TC024_completeMatch_happyPath() {
		Match m = givenCompletableMatch(5, 3, 5);

		Match completed = service.completeMatch(MATCH_ID, 1L, false, OWNER_ID);

		assertEquals(MatchStatus.COMPLETED.getValue(), completed.getStatus());
		assertEquals(1L, completed.getWinner().getId());
		assertEquals(2L, completed.getLoser().getId());
		ArgumentCaptor<MatchScoreEvent> event = ArgumentCaptor.forClass(MatchScoreEvent.class);
		verify(scoreEventRepository).save(event.capture());
		assertEquals("MATCH_END", event.getValue().getEventType());
		verify(matchSchedulingService).reschedule(TOURNAMENT_ID);
	}

	@Test
	@DisplayName("TC-025 · The winner is carried into the next round automatically")
	void TC025_completeMatch_advancesWinner() {
		Match m = givenCompletableMatch(5, 3, 5);
		Match nextWin = Match.builder().id(20L).tournament(m.getTournament()).roundNo(2).positionNo(1)
				.status(MatchStatus.PENDING.getValue()).build();
		m.setNextMatchWin(nextWin);
		m.setWinSlot("player1");
		when(matchRepository.findById(20L)).thenReturn(Optional.of(nextWin));

		service.completeMatch(MATCH_ID, 1L, false, OWNER_ID);

		assertEquals(1L, nextWin.getPlayer1().getId(),
				"the bracket fills itself as results come in");
	}

	@Test
	@DisplayName("TC-026 · The loser is carried into the losers bracket where one exists")
	void TC026_completeMatch_advancesLoser() {
		Match m = givenCompletableMatch(5, 3, 5);
		Match nextLose = Match.builder().id(30L).tournament(m.getTournament()).roundNo(1).positionNo(1)
				.status(MatchStatus.PENDING.getValue()).build();
		m.setNextMatchLose(nextLose);
		m.setLoseSlot("player2");
		when(matchRepository.findById(30L)).thenReturn(Optional.of(nextLose));

		service.completeMatch(MATCH_ID, 1L, false, OWNER_ID);

		assertEquals(2L, nextLose.getPlayer2().getId());
	}

	@Test
	@DisplayName("TC-027 · Both players are told the result by email")
	void TC027_completeMatch_notifiesBothPlayers() {
		givenCompletableMatch(5, 3, 5);

		service.completeMatch(MATCH_ID, 1L, false, OWNER_ID);

		ArgumentCaptor<MailDomainEvent> event = ArgumentCaptor.forClass(MailDomainEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertEquals(EmailEventType.MATCH_COMPLETED, event.getValue().eventType());
		assertEquals(2, event.getValue().explicitRecipients().size());
	}

	@Test
	@DisplayName("TC-028 · A match between imported players notifies nobody")
	void TC028_completeMatch_noAccountsNoMail() {
		Participant a = Participant.builder().id(1L).displayName("VĐV A").build();
		Participant b = Participant.builder().id(2L).displayName("VĐV B").build();
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.IN_PROGRESS.getValue(), a, b, 5);
		m.setPlayer1Score(5);
		m.setPlayer2Score(3);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		when(participantRepository.findById(1L)).thenReturn(Optional.of(a));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();

		service.completeMatch(MATCH_ID, 1L, false, OWNER_ID);

		verify(eventPublisher, never()).publishEvent(any(MailDomainEvent.class));
	}

	@Test
	@DisplayName("TC-029 · The winner must be one of the two players")
	void TC029_completeMatch_winnerNotInMatch() {
		givenCompletableMatch(5, 3, 5);
		when(participantRepository.findById(99L)).thenReturn(Optional.of(player(99L, "Người lạ")));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.completeMatch(MATCH_ID, 99L, false, OWNER_ID));

		assertEquals(ErrorCode.MATCH_WINNER_NOT_IN_MATCH, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-030 · The player who reached the race is the one who won")
	void TC030_completeMatch_winnerMustBeRaceLeader() {
		givenCompletableMatch(5, 3, 5);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.completeMatch(MATCH_ID, 2L, false, OWNER_ID));

		assertEquals(ErrorCode.MATCH_WINNER_MUST_BE_RACE_LEADER, ex.getErrorCode(),
				"a mis-tap here would award the match to the wrong player and corrupt the bracket");
	}

	@Test
	@DisplayName("TC-031 · Ending a match before the race is reached needs an explicit confirmation")
	void TC031_completeMatch_earlyEndNeedsConfirmation() {
		givenCompletableMatch(2, 1, 5);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.completeMatch(MATCH_ID, 1L, false, OWNER_ID));

		assertEquals(ErrorCode.MATCH_EARLY_END_NOT_CONFIRMED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-032 · A retirement can be recorded once it has been confirmed")
	void TC032_completeMatch_earlyEndConfirmed() {
		givenCompletableMatch(2, 1, 5);

		Match completed = service.completeMatch(MATCH_ID, 1L, true, OWNER_ID);

		assertEquals(MatchStatus.COMPLETED.getValue(), completed.getStatus(),
				"an injury or a withdrawal is a real outcome, it just has to be deliberate");
	}

	@Test
	@DisplayName("TC-033 · With both players level on the race the referee decides")
	void TC033_completeMatch_bothReachedRace() {
		givenCompletableMatch(5, 5, 5);

		assertEquals(2L, service.completeMatch(MATCH_ID, 2L, false, OWNER_ID).getWinner().getId());
	}

	@Test
	@DisplayName("TC-034 · A match with no race declared can be completed by either player")
	void TC034_completeMatch_noRaceTo() {
		givenCompletableMatch(3, 1, null);

		assertEquals(2L, service.completeMatch(MATCH_ID, 2L, false, OWNER_ID).getWinner().getId());
	}

	@Test
	@DisplayName("TC-035 · A match that is already complete cannot be completed again")
	void TC035_completeMatch_alreadyCompleted() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.COMPLETED.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.completeMatch(MATCH_ID, 1L, false, OWNER_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-036 · Completing a match against a participant who does not exist")
	void TC036_completeMatch_participantNotFound() {
		givenCompletableMatch(5, 3, 5);
		when(participantRepository.findById(99L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.completeMatch(MATCH_ID, 99L, false, OWNER_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ walkover ══════════════════════════

	@Test
	@DisplayName("TC-037 · A walkover names a winner without a score being played")
	void TC037_walkover_happyPath() {
		Match m = givenCompletableMatch(0, 0, 5);

		Match result = service.walkover(MATCH_ID, 1L, OWNER_ID);

		assertEquals(MatchStatus.WALKOVER.getValue(), result.getStatus());
		assertEquals(1L, result.getWinner().getId());
		assertEquals(2L, result.getLoser().getId());
		ArgumentCaptor<MatchScoreEvent> event = ArgumentCaptor.forClass(MatchScoreEvent.class);
		verify(scoreEventRepository).save(event.capture());
		assertEquals(MatchStatus.WALKOVER.getValue(), event.getValue().getEventType());
	}

	@Test
	@DisplayName("TC-038 · A walkover carries the winner into the next round too")
	void TC038_walkover_advancesWinner() {
		Match m = givenCompletableMatch(0, 0, 5);
		Match nextWin = Match.builder().id(20L).tournament(m.getTournament()).roundNo(2).positionNo(1)
				.status(MatchStatus.PENDING.getValue()).build();
		m.setNextMatchWin(nextWin);
		m.setWinSlot("player2");
		when(matchRepository.findById(20L)).thenReturn(Optional.of(nextWin));

		service.walkover(MATCH_ID, 1L, OWNER_ID);

		assertEquals(1L, nextWin.getPlayer2().getId());
	}

	@Test
	@DisplayName("TC-039 · A finished match cannot be turned into a walkover")
	void TC039_walkover_completedRefused() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.COMPLETED.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.walkover(MATCH_ID, 1L, OWNER_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-040 · A walkover does not check the score at all")
	void TC040_walkover_ignoresRaceLeader() {
		givenCompletableMatch(4, 0, 5);

		assertEquals(2L, service.walkover(MATCH_ID, 2L, OWNER_ID).getWinner().getId(),
				"the trailing player wins when their opponent fails to appear");
	}

	// ══════════════════════════ assignMatch ══════════════════════════

	private AssignMatchRequest assignRequest() {
		return new AssignMatchRequest();
	}

	@Test
	@DisplayName("TC-041 · Assigning a referee stores them and tells them by email")
	void TC041_assignMatch_assignsReferee() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(user(STAFF_ID, "STAFF", branch())));
		when(matchRepository.findByAssignedStaffId(STAFF_ID, null, null, null)).thenReturn(List.of());
		givenMatchSaveEchoes();
		AssignMatchRequest request = assignRequest();
		request.setAssignedStaffId(STAFF_ID);

		service.assignMatch(MATCH_ID, request, OWNER_ID);

		assertEquals(STAFF_ID, m.getAssignedStaff().getId());
		ArgumentCaptor<MailDomainEvent> event = ArgumentCaptor.forClass(MailDomainEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertEquals(EmailEventType.MATCH_REFEREE_ASSIGNED, event.getValue().eventType());
	}

	@Test
	@DisplayName("TC-042 · Only a staff account can be made referee")
	void TC042_assignMatch_nonStaffRefused() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(user(STAFF_ID, "MANAGER", branch())));
		AssignMatchRequest request = assignRequest();
		request.setAssignedStaffId(STAFF_ID);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.assignMatch(MATCH_ID, request, OWNER_ID));

		assertEquals(ErrorCode.INVALID_EMPLOYEE_ROLE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-043 · A referee must belong to the branch hosting the tournament")
	void TC043_assignMatch_refereeFromAnotherBranch() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(
				user(STAFF_ID, "STAFF", Branch.builder().id(9L).name("Chi nhánh Quận 7").build())));
		AssignMatchRequest request = assignRequest();
		request.setAssignedStaffId(STAFF_ID);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.assignMatch(MATCH_ID, request, OWNER_ID));

		assertEquals(ErrorCode.REFEREE_NOT_IN_BRANCH, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-044 · A referee already running another match cannot take a second")
	void TC044_assignMatch_refereeBusy() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(user(STAFF_ID, "STAFF", branch())));
		Match live = Match.builder().id(99L).status(MatchStatus.IN_PROGRESS.getValue())
				.tournament(m.getTournament()).build();
		when(matchRepository.findByAssignedStaffId(STAFF_ID, null, null, null)).thenReturn(List.of(live));
		AssignMatchRequest request = assignRequest();
		request.setAssignedStaffId(STAFF_ID);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.assignMatch(MATCH_ID, request, OWNER_ID));

		assertEquals(ErrorCode.REFEREE_BUSY_ONGOING, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-045 · A referee cannot be booked for two matches at the same time")
	void TC045_assignMatch_refereeTimeConflict() {
		Instant slot = Instant.now().plus(2, ChronoUnit.HOURS);
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		m.setScheduledAt(slot);
		m.setEstimatedEndAt(slot.plus(1, ChronoUnit.HOURS));
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(user(STAFF_ID, "STAFF", branch())));
		Match overlapping = Match.builder().id(99L).status(MatchStatus.PENDING.getValue())
				.tournament(m.getTournament())
				.scheduledAt(slot.plus(30, ChronoUnit.MINUTES))
				.estimatedEndAt(slot.plus(90, ChronoUnit.MINUTES))
				.build();
		when(matchRepository.findByAssignedStaffId(STAFF_ID, null, null, null)).thenReturn(List.of(overlapping));
		AssignMatchRequest request = assignRequest();
		request.setAssignedStaffId(STAFF_ID);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.assignMatch(MATCH_ID, request, OWNER_ID));

		assertEquals(ErrorCode.REFEREE_TIME_CONFLICT, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-046 · Clearing the referee leaves the match unassigned")
	void TC046_assignMatch_clearReferee() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		m.setAssignedStaff(user(STAFF_ID, "STAFF", branch()));
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();
		AssignMatchRequest request = assignRequest();
		request.setClearAssignedStaff(true);

		service.assignMatch(MATCH_ID, request, OWNER_ID);

		assertNull(m.getAssignedStaff());
		verify(eventPublisher, never()).publishEvent(any(MailDomainEvent.class));
	}

	@Test
	@DisplayName("TC-047 · Setting a table and time by hand locks the match against the auto-scheduler")
	void TC047_assignMatch_manualScheduleLocks() {
		Instant slot = Instant.now().plus(3, ChronoUnit.HOURS);
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		when(matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(TOURNAMENT_ID))
				.thenReturn(List.of(m));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();
		AssignMatchRequest request = assignRequest();
		request.setTableNo(2);
		request.setScheduledAt(slot);

		service.assignMatch(MATCH_ID, request, OWNER_ID);

		assertEquals(2, m.getTableNo());
		assertEquals(slot, m.getScheduledAt());
		assertNotNull(m.getEstimatedEndAt());
		assertTrue(m.getScheduleLocked(), "a hand-picked slot must survive the next reschedule");
	}

	@Test
	@DisplayName("TC-048 · A table number outside the venue's tables is refused")
	void TC048_assignMatch_tableOutOfRange() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		AssignMatchRequest request = assignRequest();
		request.setTableNo(9);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.assignMatch(MATCH_ID, request, OWNER_ID));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode(), "the venue only has four tables");
	}

	@Test
	@DisplayName("TC-049 · A match cannot be scheduled before the tournament starts")
	void TC049_assignMatch_beforeTournamentStart() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();
		AssignMatchRequest request = assignRequest();
		request.setScheduledAt(Instant.now().minus(5, ChronoUnit.DAYS));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.assignMatch(MATCH_ID, request, OWNER_ID));

		assertEquals(ErrorCode.MATCH_SCHEDULE_BEFORE_START, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-050 · A match cannot be scheduled before the match that feeds it ends")
	void TC050_assignMatch_beforeFeederEnds() {
		Instant slot = Instant.now().plus(2, ChronoUnit.HOURS);
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		Match feeder = Match.builder().id(9L).tournament(m.getTournament())
				.status(MatchStatus.PENDING.getValue()).roundNo(1).positionNo(2)
				.nextMatchWin(m)
				.scheduledAt(slot.plus(1, ChronoUnit.HOURS))
				.estimatedEndAt(slot.plus(2, ChronoUnit.HOURS))
				.build();
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		when(matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(TOURNAMENT_ID))
				.thenReturn(List.of(m, feeder));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();
		AssignMatchRequest request = assignRequest();
		request.setScheduledAt(slot);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.assignMatch(MATCH_ID, request, OWNER_ID));

		assertEquals(ErrorCode.MATCH_SCHEDULE_BEFORE_FEEDER, ex.getErrorCode(),
				"the players have not been decided yet at that hour");
	}

	@Test
	@DisplayName("TC-051 · Two matches cannot share a table at the same time")
	void TC051_assignMatch_tableTimeConflict() {
		Instant slot = Instant.now().plus(2, ChronoUnit.HOURS);
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		Match other = Match.builder().id(9L).tournament(m.getTournament())
				.status(MatchStatus.PENDING.getValue()).roundNo(1).positionNo(2).tableNo(2)
				.scheduledAt(slot.plus(30, ChronoUnit.MINUTES))
				.estimatedEndAt(slot.plus(90, ChronoUnit.MINUTES))
				.build();
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		when(matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(TOURNAMENT_ID))
				.thenReturn(List.of(m, other));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();
		AssignMatchRequest request = assignRequest();
		request.setTableNo(2);
		request.setScheduledAt(slot);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.assignMatch(MATCH_ID, request, OWNER_ID));

		assertEquals(ErrorCode.MATCH_TABLE_TIME_CONFLICT, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-052 · A table clash can be accepted deliberately")
	void TC052_assignMatch_tableConflictOverridden() {
		Instant slot = Instant.now().plus(2, ChronoUnit.HOURS);
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		Match other = Match.builder().id(9L).tournament(m.getTournament())
				.status(MatchStatus.PENDING.getValue()).roundNo(1).positionNo(2).tableNo(2)
				.scheduledAt(slot).estimatedEndAt(slot.plus(1, ChronoUnit.HOURS))
				.build();
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		when(matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(TOURNAMENT_ID))
				.thenReturn(List.of(m, other));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();
		AssignMatchRequest request = assignRequest();
		request.setTableNo(2);
		request.setScheduledAt(slot);
		request.setIgnoreTableConflict(true);

		service.assignMatch(MATCH_ID, request, OWNER_ID);

		assertEquals(2, m.getTableNo(), "the organiser was warned and chose to go ahead");
	}

	@Test
	@DisplayName("TC-053 · Returning a match to automatic scheduling unlocks it")
	void TC053_assignMatch_resetToAuto() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		m.setScheduleLocked(true);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		givenMatchSaveEchoes();
		AssignMatchRequest request = assignRequest();
		request.setResetToAuto(true);

		service.assignMatch(MATCH_ID, request, OWNER_ID);

		assertFalse(m.getScheduleLocked());
		verify(matchSchedulingService).reschedule(TOURNAMENT_ID);
	}

	@Test
	@DisplayName("TC-054 · A finished match cannot have its table or time changed")
	void TC054_assignMatch_resolvedMatchRefused() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.COMPLETED.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		givenOwnerCanOperate();
		AssignMatchRequest request = assignRequest();
		request.setTableNo(3);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.assignMatch(MATCH_ID, request, OWNER_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	// ══════════════════════════ bulkAssignMatches ══════════════════════════

	@Test
	@DisplayName("TC-055 · A bulk change skips the matches it cannot touch instead of failing")
	void TC055_bulkAssign_skipsResolvedMatches() {
		Tournament t = tournament(TournamentStatus.IN_PROGRESS.getValue());
		Match pending = match(t, MatchStatus.PENDING.getValue(), player(1L, "A"), player(2L, "B"), 5);
		Match done = Match.builder().id(12L).tournament(t).roundNo(1).positionNo(2)
				.status(MatchStatus.COMPLETED.getValue()).build();
		when(matchRepository.findAllById(List.of(MATCH_ID, 12L))).thenReturn(List.of(pending, done));
		givenOwnerCanOperate();
		when(matchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
		AssignMatchRequest request = assignRequest();
		request.setTableNo(3);

		List<Match> saved = service.bulkAssignMatches(List.of(MATCH_ID, 12L), request, OWNER_ID);

		assertEquals(1, saved.size(), "one bad row must not cost the organiser the whole batch");
		assertEquals(3, pending.getTableNo());
		verify(matchSchedulingService).reschedule(TOURNAMENT_ID);
	}

	@Test
	@DisplayName("TC-056 · A bulk change over nothing reschedules nothing")
	void TC056_bulkAssign_emptySelection() {
		when(matchRepository.findAllById(List.of())).thenReturn(List.of());
		when(matchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

		assertTrue(service.bulkAssignMatches(List.of(), assignRequest(), OWNER_ID).isEmpty());
		verify(matchSchedulingService, never()).reschedule(anyLong());
	}

	// ══════════════════════════ referee lists and score events ══════════════════════════

	@Test
	@DisplayName("TC-057 · The referees on offer are the staff of the hosting branch")
	void TC057_getRefereesForTournament_listsBranchStaff() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS.getValue())));
		User staff = user(STAFF_ID, "STAFF", branch());
		staff.setProfile(UserProfile.builder().displayName("Trọng tài Minh").build());
		when(userRepository.findActiveStaffByBranch(BRANCH_ID)).thenReturn(List.of(staff));

		List<StaffBriefResponse> referees = service.getRefereesForTournament(TOURNAMENT_ID);

		assertEquals(1, referees.size());
		assertEquals("Trọng tài Minh", referees.get(0).getDisplayName());
	}

	@Test
	@DisplayName("TC-058 · A referee with no display name is listed by email")
	void TC058_getRefereesForTournament_fallsBackToEmail() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS.getValue())));
		when(userRepository.findActiveStaffByBranch(BRANCH_ID))
				.thenReturn(List.of(user(STAFF_ID, "STAFF", branch())));

		assertEquals("u3@btms.vn", service.getRefereesForTournament(TOURNAMENT_ID).get(0).getDisplayName());
	}

	@Test
	@DisplayName("TC-059 · A tournament with no branch has no referees to offer")
	void TC059_getRefereesForTournament_noBranch() {
		Tournament t = tournament(TournamentStatus.IN_PROGRESS.getValue());
		t.setBranch(null);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));

		assertTrue(service.getRefereesForTournament(TOURNAMENT_ID).isEmpty());
		verify(userRepository, never()).findActiveStaffByBranch(anyLong());
	}

	@Test
	@DisplayName("TC-060 · Listing referees of a tournament that does not exist")
	void TC060_getRefereesForTournament_notFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getRefereesForTournament(TOURNAMENT_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-061 · The score log of a match is read in the order it happened")
	void TC061_getScoreEvents_ordered() {
		Match m = match(tournament(TournamentStatus.IN_PROGRESS.getValue()),
				MatchStatus.IN_PROGRESS.getValue(), player(1L, "A"), player(2L, "B"), 5);
		when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(m));
		when(scoreEventRepository.findByMatchIdOrderByCreatedAtAsc(MATCH_ID)).thenReturn(List.of());

		assertTrue(service.getScoreEvents(MATCH_ID).isEmpty());
		verify(scoreEventRepository).findByMatchIdOrderByCreatedAtAsc(MATCH_ID);
	}

	@Test
	@DisplayName("TC-062 · A referee's own match list can be filtered as they browse")
	void TC062_getMatchesForReferee_trimsFilters() {
		when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(user(STAFF_ID, "STAFF", branch())));
		when(matchRepository.findByAssignedStaffId(STAFF_ID, TOURNAMENT_ID, "PENDING", "Summer"))
				.thenReturn(List.of());

		service.getMatchesForReferee(STAFF_ID, TOURNAMENT_ID, "  PENDING  ", "  Summer  ");

		verify(matchRepository).findByAssignedStaffId(STAFF_ID, TOURNAMENT_ID, "PENDING", "Summer");
	}

	@Test
	@DisplayName("TC-063 · Blank filters are dropped rather than matched literally")
	void TC063_getMatchesForReferee_blankFiltersBecomeNull() {
		when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(user(STAFF_ID, "STAFF", branch())));
		when(matchRepository.findByAssignedStaffId(STAFF_ID, null, null, null)).thenReturn(List.of());

		service.getMatchesForReferee(STAFF_ID, null, "   ", "   ");

		verify(matchRepository).findByAssignedStaffId(STAFF_ID, null, null, null);
	}
}
