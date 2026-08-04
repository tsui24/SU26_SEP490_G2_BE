package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link TournamentServiceImpl}.
 *
 * <p>Mirrors the <b>TournamentService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-16 (the plain CRUD layer beneath the owner-facing service).
 *
 * <p>This class holds no branch or ownership rules of its own; those live in
 * {@link OwnerTournamentServiceImpl}. What it does own is the deletion rule of UC-16 BR-10.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · TournamentService — UC-16")
class TournamentServiceImplTest {

	@Mock TournamentRepository tournamentRepository;

	@InjectMocks TournamentServiceImpl service;

	private static final Long TOURNAMENT_ID = 1000L;

	private static Tournament tournament(String status) {
		return Tournament.builder()
				.id(TOURNAMENT_ID).name("Summer Open 2026").description("Giải mùa hè")
				.status(status).maxParticipants(16).entryFee(BigDecimal.TEN)
				.build();
	}

	@Test
	@DisplayName("TC-001 · Creating a tournament stores it as given")
	void TC001_create_delegates() {
		Tournament fresh = tournament(TournamentStatus.DRAFT.getValue());
		when(tournamentRepository.save(fresh)).thenReturn(fresh);

		assertEquals(fresh, service.create(fresh));
	}

	@Test
	@DisplayName("TC-002 · Reading a tournament by id")
	void TC002_getById_found() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament(TournamentStatus.DRAFT.getValue())));

		assertEquals("Summer Open 2026", service.getById(TOURNAMENT_ID).getName());
	}

	@Test
	@DisplayName("TC-003 · Reading a tournament that does not exist")
	void TC003_getById_notFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(TOURNAMENT_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-004 · Listing tournaments of one status, one page at a time")
	void TC004_listByStatus_delegates() {
		Pageable pageable = PageRequest.of(0, 10);
		when(tournamentRepository.findByStatus(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), pageable))
				.thenReturn(new PageImpl<>(List.of(tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue()))));

		assertEquals(1, service.listByStatus(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), pageable)
				.getTotalElements());
	}

	@Test
	@DisplayName("TC-005 · Updating rewrites the editable fields onto the stored row")
	void TC005_update_copiesEditableFields() {
		Tournament existing = tournament(TournamentStatus.DRAFT.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(existing));
		when(tournamentRepository.save(existing)).thenReturn(existing);
		Instant newStart = Instant.now().plus(20, ChronoUnit.DAYS);
		Tournament data = Tournament.builder()
				.name("Autumn Open 2026").description("Giải mùa thu")
				.maxParticipants(32).entryFee(new BigDecimal("250000"))
				.startAt(newStart)
				.build();

		Tournament updated = service.update(TOURNAMENT_ID, data);

		assertEquals("Autumn Open 2026", updated.getName());
		assertEquals("Giải mùa thu", updated.getDescription());
		assertEquals(32, updated.getMaxParticipants());
		assertEquals(new BigDecimal("250000"), updated.getEntryFee());
		assertEquals(newStart, updated.getStartAt());
	}

	@Test
	@DisplayName("TC-006 · Updating never rewrites the status")
	void TC006_update_leavesStatusAlone() {
		Tournament existing = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(existing));
		when(tournamentRepository.save(existing)).thenReturn(existing);

		Tournament updated = service.update(TOURNAMENT_ID,
				Tournament.builder().name("Tên mới").status(TournamentStatus.COMPLETED.getValue()).build());

		assertEquals(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), updated.getStatus(),
				"the lifecycle is owned by patchStatus, which is where the transition table lives");
	}

	@Test
	@DisplayName("TC-007 · Setting the status writes it straight through")
	void TC007_updateStatus_saves() {
		Tournament existing = tournament(TournamentStatus.DRAFT.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(existing));

		service.updateStatus(TOURNAMENT_ID, TournamentStatus.CANCELLED.getValue());

		assertEquals(TournamentStatus.CANCELLED.getValue(), existing.getStatus());
		verify(tournamentRepository).save(existing);
	}

	@Test
	@DisplayName("TC-008 · A draft tournament may be deleted")
	void TC008_delete_draftAllowed() {
		Tournament existing = tournament(TournamentStatus.DRAFT.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(existing));

		service.delete(TOURNAMENT_ID);

		verify(tournamentRepository).delete(existing);
	}

	@Test
	@DisplayName("TC-009 · A cancelled tournament may be deleted")
	void TC009_delete_cancelledAllowed() {
		Tournament existing = tournament(TournamentStatus.CANCELLED.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(existing));

		service.delete(TOURNAMENT_ID);

		verify(tournamentRepository).delete(existing);
	}

	@Test
	@DisplayName("TC-010 · A tournament players have entered may not be deleted")
	void TC010_delete_openTournamentRefused() {
		Tournament existing = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(existing));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(TOURNAMENT_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
		verify(tournamentRepository, never()).delete(any(Tournament.class));
	}

	@Test
	@DisplayName("TC-011 · A completed tournament may not be deleted")
	void TC011_delete_completedRefused() {
		Tournament existing = tournament(TournamentStatus.COMPLETED.getValue());
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(existing));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(TOURNAMENT_ID));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode(),
				"results and rankings must stay readable after the event");
	}

	@Test
	@DisplayName("TC-012 · Deleting a tournament that does not exist")
	void TC012_delete_notFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(TOURNAMENT_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}
}
