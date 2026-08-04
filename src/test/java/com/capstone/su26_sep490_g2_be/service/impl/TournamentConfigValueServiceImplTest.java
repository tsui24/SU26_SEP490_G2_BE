package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValue;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValueId;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ConfigFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentConfigValueRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * L1 unit tests for {@link TournamentConfigValueServiceImpl}.
 *
 * <p>Mirrors the <b>TournamentConfigValueService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-17 (per-tournament configuration values).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · TournamentConfigValueService — UC-17")
class TournamentConfigValueServiceImplTest {

	@Mock TournamentConfigValueRepository valueRepository;
	@Mock TournamentRepository tournamentRepository;
	@Mock ConfigFieldDefinitionRepository fieldRepository;

	@InjectMocks TournamentConfigValueServiceImpl service;

	private static final Long TOURNAMENT_ID = 700L;

	private static Tournament tournament() {
		return Tournament.builder().id(TOURNAMENT_ID).name("Summer Open 2026").build();
	}

	private static ConfigFieldDefinition fieldDefinition(String key) {
		return ConfigFieldDefinition.builder().fieldKey(key).label(key).dataType("INT").build();
	}

	private void givenTournamentAndField(String fieldKey) {
		lenient().when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
		lenient().when(fieldRepository.findById(fieldKey)).thenReturn(Optional.of(fieldDefinition(fieldKey)));
		lenient().when(valueRepository.save(any(TournamentConfigValue.class)))
				.thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	@DisplayName("TC-001 · Reading every value stored for a tournament")
	void TC001_getByTournament_delegates() {
		when(valueRepository.findByIdTournamentId(TOURNAMENT_ID)).thenReturn(List.of());

		assertTrue(service.getByTournament(TOURNAMENT_ID).isEmpty());
		verify(valueRepository).findByIdTournamentId(TOURNAMENT_ID);
	}

	@Test
	@DisplayName("TC-002 · Reading the values of several tournaments in one query")
	void TC002_getByTournamentIds_batch() {
		when(valueRepository.findByIdTournamentIdIn(List.of(1L, 2L))).thenReturn(List.of());

		service.getByTournamentIds(List.of(1L, 2L));

		verify(valueRepository).findByIdTournamentIdIn(List.of(1L, 2L));
	}

	@Test
	@DisplayName("TC-003 · Asking for no tournaments at all queries nothing")
	void TC003_getByTournamentIds_emptyList() {
		assertTrue(service.getByTournamentIds(List.of()).isEmpty());
		verify(valueRepository, never()).findByIdTournamentIdIn(any());
	}

	@Test
	@DisplayName("TC-004 · A null tournament list queries nothing")
	void TC004_getByTournamentIds_null() {
		assertTrue(service.getByTournamentIds(null).isEmpty());
		verify(valueRepository, never()).findByIdTournamentIdIn(any());
	}

	@Test
	@DisplayName("TC-005 · Saving a value the tournament has never set creates a row")
	void TC005_save_createsNewRow() {
		givenTournamentAndField("bracket_size");
		when(valueRepository.findById(any(TournamentConfigValueId.class))).thenReturn(Optional.empty());

		TournamentConfigValue saved = service.save(TOURNAMENT_ID, "bracket_size", "16");

		assertEquals("16", saved.getValue());
		assertEquals(TOURNAMENT_ID, saved.getId().getTournamentId());
		assertEquals("bracket_size", saved.getId().getFieldKey());
	}

	@Test
	@DisplayName("TC-006 · Saving a value that already exists overwrites it in place")
	void TC006_save_updatesExistingRow() {
		givenTournamentAndField("bracket_size");
		TournamentConfigValue existing = TournamentConfigValue.builder()
				.id(new TournamentConfigValueId(TOURNAMENT_ID, "bracket_size"))
				.tournament(tournament()).fieldDefinition(fieldDefinition("bracket_size"))
				.value("16").build();
		when(valueRepository.findById(any(TournamentConfigValueId.class))).thenReturn(Optional.of(existing));

		TournamentConfigValue saved = service.save(TOURNAMENT_ID, "bracket_size", "32");

		assertEquals("32", saved.getValue());
		assertEquals(existing, saved, "the stored row is updated rather than duplicated");
	}

	@Test
	@DisplayName("TC-007 · Saving a value for a tournament that does not exist")
	void TC007_save_tournamentNotFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.save(TOURNAMENT_ID, "bracket_size", "16"));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(valueRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-008 · Saving a value against a field the admin has never defined")
	void TC008_save_fieldNotFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
		when(fieldRepository.findById("mystery_field")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.save(TOURNAMENT_ID, "mystery_field", "1"));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-009 · Saving several values writes each of them")
	void TC009_saveAll_writesEveryEntry() {
		givenTournamentAndField("bracket_size");
		lenient().when(fieldRepository.findById("third_place_match"))
				.thenReturn(Optional.of(fieldDefinition("third_place_match")));
		when(valueRepository.findById(any(TournamentConfigValueId.class))).thenReturn(Optional.empty());
		Map<String, String> values = new LinkedHashMap<>();
		values.put("bracket_size", "16");
		values.put("third_place_match", "true");

		List<TournamentConfigValue> saved = service.saveAll(TOURNAMENT_ID, values);

		assertEquals(2, saved.size());
		verify(valueRepository, times(2)).save(any(TournamentConfigValue.class));
	}

	@Test
	@DisplayName("TC-010 · Saving an empty batch writes nothing")
	void TC010_saveAll_emptyMap() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));

		assertTrue(service.saveAll(TOURNAMENT_ID, Map.of()).isEmpty());
		verify(valueRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-011 · Clearing a tournament removes all of its configuration values")
	void TC011_deleteByTournament_delegates() {
		service.deleteByTournament(TOURNAMENT_ID);

		verify(valueRepository).deleteByIdTournamentId(TOURNAMENT_ID);
	}

	@Test
	@DisplayName("TC-012 · Reading one field of one tournament")
	void TC012_getByTournamentAndField_delegates() {
		when(valueRepository.findByIdTournamentIdAndIdFieldKey(TOURNAMENT_ID, "bracket_size"))
				.thenReturn(Optional.empty());

		assertTrue(service.getByTournamentAndField(TOURNAMENT_ID, "bracket_size").isEmpty());
	}
}
