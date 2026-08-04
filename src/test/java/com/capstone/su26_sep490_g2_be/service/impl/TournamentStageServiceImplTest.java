package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.TournamentStage;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.TournamentStageStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.TournamentStageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link TournamentStageServiceImpl}.
 *
 * <p>Mirrors the <b>TournamentStageService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-19 / UC-20 (the stages a bracket is made of).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · TournamentStageService — UC-19, UC-20")
class TournamentStageServiceImplTest {

	@Mock TournamentStageRepository stageRepository;

	@InjectMocks TournamentStageServiceImpl service;

	private static final Long TOURNAMENT_ID = 900L;
	private static final Long STAGE_ID = 5L;

	private static TournamentStage stage(String status) {
		return TournamentStage.builder()
				.id(STAGE_ID).name("Loại trực tiếp").stageType("KNOCKOUT").orderNo(1)
				.status(status).build();
	}

	@Test
	@DisplayName("TC-001 · Stages of a tournament come back in playing order")
	void TC001_getByTournament_ordered() {
		when(stageRepository.findByTournamentIdOrderByOrderNoAsc(TOURNAMENT_ID))
				.thenReturn(List.of(stage(TournamentStageStatus.PENDING.getValue())));

		assertEquals(1, service.getByTournament(TOURNAMENT_ID).size());
		verify(stageRepository).findByTournamentIdOrderByOrderNoAsc(TOURNAMENT_ID);
	}

	@Test
	@DisplayName("TC-002 · Reading one stage by id")
	void TC002_getById_found() {
		when(stageRepository.findById(STAGE_ID))
				.thenReturn(Optional.of(stage(TournamentStageStatus.PENDING.getValue())));

		assertEquals("KNOCKOUT", service.getById(STAGE_ID).getStageType());
	}

	@Test
	@DisplayName("TC-003 · Reading a stage that does not exist")
	void TC003_getById_notFound() {
		when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(STAGE_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-004 · Creating a stage stores it as given")
	void TC004_create_delegates() {
		TournamentStage fresh = stage(TournamentStageStatus.PENDING.getValue());
		when(stageRepository.save(fresh)).thenReturn(fresh);

		assertEquals(fresh, service.create(fresh));
	}

	@Test
	@DisplayName("TC-005 · Marking a stage finished stores the new status")
	void TC005_updateStatus_saves() {
		TournamentStage existing = stage(TournamentStageStatus.PENDING.getValue());
		when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(existing));
		when(stageRepository.save(existing)).thenReturn(existing);

		TournamentStage updated = service.updateStatus(STAGE_ID, TournamentStageStatus.COMPLETED.getValue());

		assertEquals(TournamentStageStatus.COMPLETED.getValue(), updated.getStatus());
	}

	@Test
	@DisplayName("TC-006 · Changing the status of a stage that does not exist")
	void TC006_updateStatus_notFound() {
		when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateStatus(STAGE_ID, TournamentStageStatus.COMPLETED.getValue()));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(stageRepository, never()).save(any());
	}
}
