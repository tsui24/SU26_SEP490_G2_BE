package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ConfigFieldDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link ConfigFieldDefinitionServiceImpl}.
 *
 * <p>Mirrors the <b>ConfigFieldDefinitionService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-10 (FT-08, Wave 1).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · ConfigFieldDefinitionService — UC-10")
class ConfigFieldDefinitionServiceImplTest {

	@Mock ConfigFieldDefinitionRepository repository;

	@InjectMocks ConfigFieldDefinitionServiceImpl service;

	private static final String KEY = "race_to";

	private static ConfigFieldDefinition field(String key, String dataType, String scope, boolean active) {
		return ConfigFieldDefinition.builder()
				.fieldKey(key).label("Race To")
				.dataType(dataType).fieldScope(scope)
				.uiComponent("NUMBER_INPUT")
				.minValue(1).maxValue(99)
				.isActive(active)
				.build();
	}

	// ══════════════════ getAll / getActive / getByScope — UC-10.2 ══════════════════

	@Test
	@DisplayName("TC-001 · The admin list includes disabled definitions")
	void TC001_getAll_includesDisabled() {
		when(repository.findAll()).thenReturn(List.of(
				field(KEY, "INT", "TOURNAMENT", true),
				field("third_place", "BOOLEAN", "TOURNAMENT", false)));

		List<ConfigFieldDefinition> result = service.getAll();

		assertEquals(2, result.size());
		assertTrue(result.stream().anyMatch(f -> !f.getIsActive()));
	}

	@Test
	@DisplayName("TC-002 · Only enabled definitions reach the format builder")
	void TC002_getActive_returnsEnabledOnly() {
		when(repository.findByIsActiveTrueOrderByFieldScopeAsc()).thenReturn(List.of(
				field(KEY, "INT", "TOURNAMENT", true),
				field("break_rule", "ENUM", "MATCH", true)));

		List<ConfigFieldDefinition> result = service.getActive();

		assertEquals(2, result.size());
		// A disabled field leaking in here would be assignable while configuring a format
		verify(repository).findByIsActiveTrueOrderByFieldScopeAsc();
		verify(repository, never()).findAll();
	}

	@Test
	@DisplayName("TC-003 · No enabled definition exists")
	void TC003_getActive_emptyResult() {
		when(repository.findByIsActiveTrueOrderByFieldScopeAsc()).thenReturn(List.of());

		assertTrue(service.getActive().isEmpty());
	}

	@Test
	@DisplayName("TC-004 · Filtering definitions by scope")
	void TC004_getByScope_passesScopeThrough() {
		when(repository.findByFieldScope("TOURNAMENT"))
				.thenReturn(List.of(field(KEY, "INT", "TOURNAMENT", true)));

		List<ConfigFieldDefinition> result = service.getByScope("TOURNAMENT");

		assertEquals(1, result.size());
		verify(repository).findByFieldScope("TOURNAMENT");
	}

	// ══════════════════════════ getByKey — UC-10.2 ══════════════════════════

	@Test
	@DisplayName("TC-005 · Opens a definition by its key")
	void TC005_getByKey_happyPath() {
		when(repository.findById(KEY)).thenReturn(Optional.of(field(KEY, "INT", "TOURNAMENT", true)));

		ConfigFieldDefinition result = service.getByKey(KEY);

		assertEquals(KEY, result.getFieldKey());
		assertEquals("INT", result.getDataType());
		assertEquals("TOURNAMENT", result.getFieldScope());
		assertEquals(99, result.getMaxValue());
	}

	@Test
	@DisplayName("TC-006 · Key does not exist")
	void TC006_getByKey_notFound() {
		when(repository.findById("no_such")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.getByKey("no_such"));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ create — UC-10.1 ══════════════════════════

	@Test
	@DisplayName("TC-007 · Creating a definition with a fresh key")
	void TC007_create_happyPath() {
		ConfigFieldDefinition fresh = field(KEY, "INT", "TOURNAMENT", true);
		when(repository.existsById(KEY)).thenReturn(false);
		when(repository.save(any(ConfigFieldDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		ConfigFieldDefinition saved = service.create(fresh);

		assertEquals(KEY, saved.getFieldKey());
		assertTrue(saved.getIsActive());
		verify(repository, times(1)).save(fresh);
	}

	@Test
	@DisplayName("TC-008 · Duplicate field key is rejected")
	void TC008_create_duplicateKey_rejected() {
		when(repository.existsById(KEY)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.create(field(KEY, "TEXT", "MATCH", true)));

		assertEquals(ErrorCode.DUPLICATE_RESOURCE, ex.getErrorCode());
		verify(repository, never()).save(any());
		// This service guards the key properly, unlike GameTypeServiceImpl (DEF-W1-01)
		// and TournamentFormatServiceImpl (DEF-W1-02) — it is the reference for that fix
	}

	// ══════════════════════════ update — UC-10.3 ══════════════════════════

	@Test
	@DisplayName("TC-009 · Editable attributes are saved")
	void TC009_update_savesEditableAttributes() {
		ConfigFieldDefinition existing = field(KEY, "INT", "TOURNAMENT", true);
		when(repository.findById(KEY)).thenReturn(Optional.of(existing));
		when(repository.save(any(ConfigFieldDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		ConfigFieldDefinition payload = ConfigFieldDefinition.builder()
				.label("Race To Target").uiComponent("SLIDER").minValue(3).maxValue(21).build();

		ConfigFieldDefinition result = service.update(KEY, payload);

		assertEquals("Race To Target", result.getLabel());
		assertEquals("SLIDER", result.getUiComponent());
		assertEquals(3, result.getMinValue());
		assertEquals(21, result.getMaxValue());
		assertSame(existing, result);
	}

	@Test
	@DisplayName("TC-010 · Data type and scope stay immutable")
	void TC010_update_dataTypeAndScopeImmutable() {
		ConfigFieldDefinition existing = field(KEY, "INT", "TOURNAMENT", true);
		when(repository.findById(KEY)).thenReturn(Optional.of(existing));
		when(repository.save(any(ConfigFieldDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		ConfigFieldDefinition payload = ConfigFieldDefinition.builder()
				.label("Race To").dataType("TEXT").fieldScope("MATCH").build();

		ConfigFieldDefinition result = service.update(KEY, payload);

		// Changing the data type of a field already in use would invalidate every stored value
		assertEquals("INT", result.getDataType());
		assertEquals("TOURNAMENT", result.getFieldScope());
	}

	@Test
	@DisplayName("TC-011 · The field key itself stays immutable")
	void TC011_update_fieldKeyImmutable() {
		ConfigFieldDefinition existing = field(KEY, "INT", "TOURNAMENT", true);
		when(repository.findById(KEY)).thenReturn(Optional.of(existing));
		when(repository.save(any(ConfigFieldDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		ConfigFieldDefinition payload = ConfigFieldDefinition.builder()
				.fieldKey("race_to_v2").label("Race To").build();

		assertEquals(KEY, service.update(KEY, payload).getFieldKey());
	}

	@Test
	@DisplayName("TC-012 · Updating a definition that does not exist")
	void TC012_update_notFound() {
		when(repository.findById("no_such")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.update("no_such", field("no_such", "INT", "TOURNAMENT", true)));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(repository, never()).save(any());
	}

	@Test
	@DisplayName("TC-013 · Clearing the enum options")
	void TC013_update_nullEnumOptionsWipesStoredList() {
		ConfigFieldDefinition existing = field("seeding_method", "ENUM", "TOURNAMENT", true);
		existing.setEnumOptions("[\"RANDOM\",\"MANUAL\",\"ELO\"]");
		when(repository.findById("seeding_method")).thenReturn(Optional.of(existing));
		when(repository.save(any(ConfigFieldDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		ConfigFieldDefinition payload = ConfigFieldDefinition.builder()
				.label("Seeding Method").enumOptions(null).build();

		// The field is copied unconditionally, so a null wipes the stored list. A partial update
		// cannot preserve enum options here — raised as DEF-W1-05.
		assertNull(service.update("seeding_method", payload).getEnumOptions());
	}

	// ══════════════════════════ setActive — UC-10.4 ══════════════════════════

	@Test
	@DisplayName("TC-014 · Disabling keeps the record")
	void TC014_setActive_disableKeepsRecord() {
		ConfigFieldDefinition existing = field(KEY, "INT", "TOURNAMENT", true);
		when(repository.findById(KEY)).thenReturn(Optional.of(existing));

		service.setActive(KEY, false);

		ArgumentCaptor<ConfigFieldDefinition> saved = ArgumentCaptor.forClass(ConfigFieldDefinition.class);
		verify(repository, times(1)).save(saved.capture());
		assertFalse(saved.getValue().getIsActive());
		verify(repository, never()).delete(any(ConfigFieldDefinition.class));
		verify(repository, never()).deleteById(any());
	}

	@Test
	@DisplayName("TC-015 · Re-enabling a disabled definition")
	void TC015_setActive_reEnable() {
		ConfigFieldDefinition existing = field(KEY, "INT", "TOURNAMENT", false);
		when(repository.findById(KEY)).thenReturn(Optional.of(existing));

		service.setActive(KEY, true);

		assertTrue(existing.getIsActive());
		verify(repository, times(1)).save(existing);
	}

	@Test
	@DisplayName("TC-016 · Toggling a definition that does not exist")
	void TC016_setActive_notFound() {
		when(repository.findById("no_such")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.setActive("no_such", false));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(repository, never()).save(any());
	}
}
