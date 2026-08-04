package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link ParticipantServiceImpl}.
 *
 * <p>Mirrors the <b>ParticipantService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-28 (the roster of a tournament).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · ParticipantService — UC-28")
class ParticipantServiceImplTest {

	@Mock ParticipantRepository participantRepository;

	@InjectMocks ParticipantServiceImpl service;

	private static final Long TOURNAMENT_ID = 900L;
	private static final Long PARTICIPANT_ID = 4L;

	private static Participant participant(Long id, String displayName, String status) {
		return Participant.builder().id(id).displayName(displayName).status(status).build();
	}

	@Test
	@DisplayName("TC-001 · Creating a participant stores it as given")
	void TC001_create_delegates() {
		Participant fresh = participant(PARTICIPANT_ID, "Nguyễn Văn A", ParticipantStatus.ACTIVE.getValue());
		when(participantRepository.save(fresh)).thenReturn(fresh);

		assertEquals(fresh, service.create(fresh));
	}

	@Test
	@DisplayName("TC-002 · Reading a participant by id")
	void TC002_getById_found() {
		when(participantRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.of(
				participant(PARTICIPANT_ID, "Nguyễn Văn A", ParticipantStatus.ACTIVE.getValue())));

		assertEquals("Nguyễn Văn A", service.getById(PARTICIPANT_ID).getDisplayName());
	}

	@Test
	@DisplayName("TC-003 · Reading a participant that does not exist")
	void TC003_getById_notFound() {
		when(participantRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(PARTICIPANT_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-004 · The full roster includes players who have withdrawn")
	void TC004_getByTournament_includesEveryone() {
		when(participantRepository.findByTournamentId(TOURNAMENT_ID)).thenReturn(List.of(
				participant(1L, "Nguyễn Văn A", ParticipantStatus.ACTIVE.getValue()),
				participant(2L, "Trần Thị B", ParticipantStatus.WITHDRAWN.getValue())));

		assertEquals(2, service.getByTournament(TOURNAMENT_ID).size());
	}

	@Test
	@DisplayName("TC-005 · The active roster is the one a draw is made from")
	void TC005_getActiveByTournament_filtersByStatus() {
		when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(List.of(participant(1L, "Nguyễn Văn A", ParticipantStatus.ACTIVE.getValue())));

		assertEquals(1, service.getActiveByTournament(TOURNAMENT_ID).size());
		verify(participantRepository).findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue());
	}

	@Test
	@DisplayName("TC-006 · Withdrawing a player stores the new status")
	void TC006_updateStatus_saves() {
		Participant existing = participant(PARTICIPANT_ID, "Nguyễn Văn A", ParticipantStatus.ACTIVE.getValue());
		when(participantRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.of(existing));
		when(participantRepository.save(existing)).thenReturn(existing);

		Participant updated = service.updateStatus(PARTICIPANT_ID, ParticipantStatus.WITHDRAWN.getValue());

		assertEquals(ParticipantStatus.WITHDRAWN.getValue(), updated.getStatus());
	}

	@Test
	@DisplayName("TC-007 · Changing the status of a participant that does not exist")
	void TC007_updateStatus_notFound() {
		when(participantRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateStatus(PARTICIPANT_ID, ParticipantStatus.WITHDRAWN.getValue()));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(participantRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-008 · Seeding numbers the active roster from one upwards")
	void TC008_assignSeedNumbers_numbersInOrder() {
		List<Participant> roster = new ArrayList<>(List.of(
				participant(1L, "Nguyễn Văn A", ParticipantStatus.ACTIVE.getValue()),
				participant(2L, "Trần Thị B", ParticipantStatus.ACTIVE.getValue()),
				participant(3L, "Lê Văn C", ParticipantStatus.ACTIVE.getValue())));
		when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(roster);

		service.assignSeedNumbers(TOURNAMENT_ID);

		assertEquals(1, roster.get(0).getSeedNo());
		assertEquals(2, roster.get(1).getSeedNo());
		assertEquals(3, roster.get(2).getSeedNo());
		verify(participantRepository).saveAll(roster);
	}

	@Test
	@DisplayName("TC-009 · Seeding an empty roster writes an empty batch")
	void TC009_assignSeedNumbers_emptyRoster() {
		when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(new ArrayList<>());

		service.assignSeedNumbers(TOURNAMENT_ID);

		ArgumentCaptor<List<Participant>> saved = ArgumentCaptor.forClass(List.class);
		verify(participantRepository).saveAll(saved.capture());
		assertTrue(saved.getValue().isEmpty());
	}
}
