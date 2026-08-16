package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchScoreEventRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MatchSchedulingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · MatchService — UC-41 race-to score lock")
class MatchServiceRaceToLockTest {

	@Mock MatchRepository matchRepository;
	@Mock MatchScoreEventRepository scoreEventRepository;
	@Mock ParticipantRepository participantRepository;
	@Mock UserRepository userRepository;
	@Mock MatchSchedulingService matchSchedulingService;

	@InjectMocks MatchServiceImpl matchService;

	private User staff;
	private Participant p1;
	private Participant p2;
	private Match match;

	@BeforeEach
	void setUp() {
		staff = User.builder().id(10L).role(Role.builder().code("STAFF").build()).build();
		p1 = Participant.builder().id(1L).displayName("A").build();
		p2 = Participant.builder().id(2L).displayName("B").build();
		Tournament tournament = Tournament.builder()
				.id(100L)
				.status(TournamentStatus.IN_PROGRESS.getValue())
				.format("SINGLE_ELIMINATION")
				.build();
		match = Match.builder()
				.id(42L)
				.tournament(tournament)
				.player1(p1)
				.player2(p2)
				.player1Score(0)
				.player2Score(0)
				.raceTo(5)
				.status(MatchStatus.IN_PROGRESS.getValue())
				.assignedStaff(staff)
				.build();
	}

	private void stubLoadOnly() {
		when(matchRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(match));
		when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
	}

	private void stubForUpdate() {
		stubLoadOnly();
		when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
		when(scoreEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	@DisplayName("TC-064 · Adding a point after the race target is already reached is refused")
	void TC064_incrementScore_whenAlreadyAtRaceTo_rejected() {
		match.setPlayer1Score(5);
		match.setPlayer2Score(2);
		stubLoadOnly();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> matchService.incrementScore(42L, 2, 1, 10L));

		assertEquals(ErrorCode.MATCH_SCORE_LOCKED, ex.getErrorCode());
		assertEquals(HttpStatus.CONFLICT, ex.getErrorCode().getHttpStatus());
		verify(matchRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-065 · A score may not be driven below zero")
	void TC065_incrementScore_belowZero_rejected() {
		match.setPlayer1Score(0);
		match.setPlayer2Score(0);
		stubLoadOnly();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> matchService.incrementScore(42L, 1, -1, 10L));
		assertEquals(ErrorCode.MATCH_SCORE_OUT_OF_RANGE, ex.getErrorCode());
		assertEquals(HttpStatus.BAD_REQUEST, ex.getErrorCode().getHttpStatus());
	}

	@Test
	@DisplayName("TC-066 · The point that reaches the race target exactly is accepted")
	void TC066_incrementScore_toExactlyRaceTo_accepted() {
		match.setPlayer1Score(4);
		match.setPlayer2Score(2);
		stubForUpdate();

		Match updated = matchService.incrementScore(42L, 1, 1, 10L);
		assertEquals(5, updated.getPlayer1Score());
		verify(matchRepository).save(match);
	}

	@Test
	@DisplayName("TC-067 · The next point after the race target is refused and the score is unchanged")
	void TC067_incrementScore_afterReachingRaceTo_secondAddRejected() {
		match.setPlayer1Score(4);
		match.setPlayer2Score(2);
		stubForUpdate();

		assertEquals(5, matchService.incrementScore(42L, 1, 1, 10L).getPlayer1Score());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> matchService.incrementScore(42L, 1, 1, 10L));
		assertEquals(ErrorCode.MATCH_SCORE_LOCKED, ex.getErrorCode());
		assertEquals(5, match.getPlayer1Score());
	}

	@Test
	@DisplayName("TC-068 · A score sitting at the race target may still be corrected downwards")
	void TC068_incrementScore_decrementAtRaceTo_allowed() {
		match.setPlayer1Score(5);
		match.setPlayer2Score(2);
		stubForUpdate();

		Match updated = matchService.incrementScore(42L, 1, -1, 10L);
		assertEquals(4, updated.getPlayer1Score());
	}

	@Test
	@DisplayName("TC-069 · Scoring on a match that has not started is refused")
	void TC069_incrementScore_whenPending_rejected() {
		match.setStatus(MatchStatus.PENDING.getValue());
		when(matchRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(match));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> matchService.incrementScore(42L, 1, 1, 10L));
		assertEquals(ErrorCode.MATCH_NOT_IN_PROGRESS, ex.getErrorCode());
		assertEquals(HttpStatus.CONFLICT, ex.getErrorCode().getHttpStatus());
	}

	@Test
	@DisplayName("TC-070 · Completing with a winner who did not reach the race target is refused")
	void TC070_completeMatch_winnerIsNotRaceLeader_rejected() {
		match.setPlayer1Score(5);
		match.setPlayer2Score(2);
		when(matchRepository.findById(42L)).thenReturn(Optional.of(match));
		when(participantRepository.findById(2L)).thenReturn(Optional.of(p2));
		when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> matchService.completeMatch(42L, 2L, false, 10L));
		assertEquals(ErrorCode.MATCH_WINNER_MUST_BE_RACE_LEADER, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-071 · Completing with the player who reached the race target is accepted")
	void TC071_completeMatch_winnerIsRaceLeader_accepted() {
		match.setPlayer1Score(5);
		match.setPlayer2Score(2);
		when(matchRepository.findById(42L)).thenReturn(Optional.of(match));
		when(participantRepository.findById(1L)).thenReturn(Optional.of(p1));
		when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
		when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
		when(scoreEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Match done = matchService.completeMatch(42L, 1L, false, 10L);
		assertEquals(MatchStatus.COMPLETED.getValue(), done.getStatus());
		assertEquals(p1, done.getWinner());
	}

	/**
	 * Hai request +1 đồng thời ở 4 điểm: serialize trên cùng entity (mô phỏng row-lock),
	 * chỉ một thành công lên 5, cái sau bị 409.
	 */
	@Test
	@DisplayName("TC-072 · Two simultaneous points at match point — only one is recorded")
	void TC072_incrementScore_concurrentAtMatchPoint_onlyOneSucceeds() throws Exception {
		match.setPlayer1Score(4);
		match.setPlayer2Score(0);

		AtomicInteger saveCount = new AtomicInteger(0);
		when(matchRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(match));
		when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
		when(scoreEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
			saveCount.incrementAndGet();
			return inv.getArgument(0);
		});

		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(2);
		AtomicInteger success = new AtomicInteger(0);
		AtomicInteger locked = new AtomicInteger(0);
		AtomicReference<Throwable> unexpected = new AtomicReference<>();

		Runnable task = () -> {
			try {
				startGate.await();
				synchronized (match) {
					matchService.incrementScore(42L, 1, 1, 10L);
				}
				success.incrementAndGet();
			} catch (BusinessException be) {
				if (be.getErrorCode() == ErrorCode.MATCH_SCORE_LOCKED) {
					locked.incrementAndGet();
				} else {
					unexpected.set(be);
				}
			} catch (Exception e) {
				unexpected.set(e);
			} finally {
				done.countDown();
			}
		};

		new Thread(task).start();
		new Thread(task).start();
		startGate.countDown();
		done.await();

		assertNull(unexpected.get());
		assertEquals(1, success.get());
		assertEquals(1, locked.get());
		assertEquals(5, match.getPlayer1Score());
		assertEquals(1, saveCount.get());
	}
}
