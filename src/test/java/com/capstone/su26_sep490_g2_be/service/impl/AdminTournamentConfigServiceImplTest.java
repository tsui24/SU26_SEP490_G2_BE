package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.BootstrapDefaultsRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateFormatRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchFormatActiveRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateFormatRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpsertFormatConfigFieldsRequest;
import com.capstone.su26_sep490_g2_be.dto.response.FormatActivateResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatConfigFieldsSaveResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatCreateResponse;
import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.FormatConfigField;
import com.capstone.su26_sep490_g2_be.entity.FormatRaceToRule;
import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ConfigFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.FormatConfigFieldRepository;
import com.capstone.su26_sep490_g2_be.repository.FormatRaceToRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentFormatDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link AdminTournamentConfigServiceImpl}.
 *
 * <p>Mirrors the <b>AdminTournamentConfigService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-12 (format definitions) and UC-13 (default format settings).
 *
 * <p>This is the Admin-facing setup wizard behind UC-13. Note that its {@code createFormat}
 * guards both the code and the handler key, which is exactly what
 * {@link TournamentFormatServiceImpl#create} fails to do (DEF-W1-02, DEF-W1-03) — the correct
 * implementation already exists here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · AdminTournamentConfigService — UC-12, UC-13")
class AdminTournamentConfigServiceImplTest {

	@Mock ConfigFieldDefinitionRepository configFieldRepository;
	@Mock TournamentFormatDefinitionRepository formatRepository;
	@Mock FormatConfigFieldRepository formatConfigFieldRepository;
	@Mock FormatRaceToRuleRepository formatRaceToRuleRepository;
	@Mock GameTypeDefinitionRepository gameTypeRepository;
	@Mock TournamentRepository tournamentRepository;

	@InjectMocks AdminTournamentConfigServiceImpl service;

	private static final String CODE = "SINGLE_ELIM";
	private static final String HANDLER = "single-elimination";

	private static TournamentFormatDefinition format() {
		return TournamentFormatDefinition.builder()
				.code(CODE).name("Single Elimination").handlerKey(HANDLER)
				.schemaVersion("1.0").isActive(false)
				.build();
	}

	private static CreateFormatRequest createRequest(String schemaVersion, Boolean isActive) {
		CreateFormatRequest r = new CreateFormatRequest();
		r.setCode(CODE);
		r.setName("Single Elimination");
		r.setHandlerKey(HANDLER);
		r.setSchemaVersion(schemaVersion);
		r.setIsActive(isActive);
		return r;
	}

	private static FormatConfigField configField(String key, String defaultValue) {
		return FormatConfigField.builder()
				.formatCode(CODE).fieldKey(key).defaultValue(defaultValue)
				.isRequired(true).isVisibleToOwner(true)
				.build();
	}

	private static FormatRaceToRule raceToRule(String roundKey, Integer raceTo) {
		return FormatRaceToRule.builder()
				.formatCode(CODE).roundKey(roundKey).raceTo(raceTo).bracketPhase("PLAYOFF")
				.build();
	}

	private static UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest fieldItem(
			String key, String defaultValue, Boolean required, Boolean visible) {
		UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest i =
				new UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest();
		i.setFieldKey(key);
		i.setDefaultValue(defaultValue);
		i.setIsRequired(required);
		i.setIsVisibleToOwner(visible);
		return i;
	}

	private static UpsertFormatConfigFieldsRequest fieldsRequest(
			UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest... items) {
		UpsertFormatConfigFieldsRequest r = new UpsertFormatConfigFieldsRequest();
		r.setFields(List.of(items));
		return r;
	}

	// ══════════════════════════ createFormat — UC-12.1 ══════════════════════════

	@Test
	@DisplayName("TC-001 · Creating a format with a fresh code and handler key")
	void TC001_createFormat_happyPath() {
		when(formatRepository.existsById(CODE)).thenReturn(false);
		when(formatRepository.existsByHandlerKey(HANDLER)).thenReturn(false);

		FormatCreateResponse response = service.createFormat(createRequest("2.0", true));

		ArgumentCaptor<TournamentFormatDefinition> saved =
				ArgumentCaptor.forClass(TournamentFormatDefinition.class);
		verify(formatRepository).save(saved.capture());
		assertEquals(CODE, saved.getValue().getCode());
		assertEquals("2.0", saved.getValue().getSchemaVersion());
		assertTrue(saved.getValue().getIsActive());
		assertEquals("config-fields", response.getNextStep());
	}

	@Test
	@DisplayName("TC-002 · Duplicate format code is rejected")
	void TC002_createFormat_duplicateCode() {
		when(formatRepository.existsById(CODE)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createFormat(createRequest(null, true)));

		// The guard TournamentFormatServiceImpl.create is missing (DEF-W1-02)
		assertEquals(ErrorCode.FORMAT_CODE_EXISTS, ex.getErrorCode());
		verify(formatRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-003 · Duplicate handler key is rejected")
	void TC003_createFormat_duplicateHandlerKey() {
		when(formatRepository.existsById(CODE)).thenReturn(false);
		when(formatRepository.existsByHandlerKey(HANDLER)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createFormat(createRequest(null, true)));

		// The guard TournamentFormatServiceImpl.create is missing (DEF-W1-03)
		assertEquals(ErrorCode.DUPLICATE_RESOURCE, ex.getErrorCode());
		verify(formatRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-004 · Omitted schema version defaults to 1.0")
	void TC004_createFormat_defaultSchemaVersion() {
		when(formatRepository.existsById(CODE)).thenReturn(false);
		when(formatRepository.existsByHandlerKey(HANDLER)).thenReturn(false);

		service.createFormat(createRequest(null, true));

		ArgumentCaptor<TournamentFormatDefinition> saved =
				ArgumentCaptor.forClass(TournamentFormatDefinition.class);
		verify(formatRepository).save(saved.capture());
		assertEquals("1.0", saved.getValue().getSchemaVersion());
	}

	@Test
	@DisplayName("TC-005 · Omitted active flag creates an inactive format")
	void TC005_createFormat_nullIsActiveMeansInactive() {
		when(formatRepository.existsById(CODE)).thenReturn(false);
		when(formatRepository.existsByHandlerKey(HANDLER)).thenReturn(false);

		service.createFormat(createRequest(null, null));

		ArgumentCaptor<TournamentFormatDefinition> saved =
				ArgumentCaptor.forClass(TournamentFormatDefinition.class);
		verify(formatRepository).save(saved.capture());
		// Boolean.TRUE.equals(null) is false, so a format created through the wizard stays
		// inactive until the Admin completes the setup and activates it explicitly
		assertFalse(saved.getValue().getIsActive());
	}

	// ══════════════════════════ updateFormat — UC-12.3 ══════════════════════════

	@Test
	@DisplayName("TC-006 · Handler key already used by another format is rejected")
	void TC006_updateFormat_duplicateHandlerKey() {
		TournamentFormatDefinition existing = format();
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(existing));
		when(formatRepository.existsByHandlerKeyAndCodeNot("double-elimination", CODE)).thenReturn(true);

		UpdateFormatRequest request = new UpdateFormatRequest();
		request.setName("Renamed");
		request.setHandlerKey("double-elimination");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateFormat(CODE, request));

		assertEquals(ErrorCode.DUPLICATE_RESOURCE, ex.getErrorCode());
		assertEquals(HANDLER, existing.getHandlerKey());
	}

	@Test
	@DisplayName("TC-007 · Updating a format that does not exist")
	void TC007_updateFormat_notFound() {
		when(formatRepository.findById("NO_SUCH")).thenReturn(Optional.empty());

		UpdateFormatRequest request = new UpdateFormatRequest();
		request.setHandlerKey(HANDLER);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateFormat("NO_SUCH", request));

		assertEquals(ErrorCode.FORMAT_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ patchFormatActive — UC-12.4 ══════════════════════════

	@Test
	@DisplayName("TC-008 · Toggling the active flag")
	void TC008_patchFormatActive_toggles() {
		TournamentFormatDefinition existing = format();
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(existing));

		PatchFormatActiveRequest request = new PatchFormatActiveRequest();
		request.setIsActive(true);

		assertTrue(service.patchFormatActive(CODE, request).getIsActive());
		verify(formatRepository).save(existing);
	}

	// ══════════ saveConfigFields — UC-13 plus the in-use guard ══════════

	@Test
	@DisplayName("TC-009 · Saving config fields for a format not yet in use")
	void TC009_saveConfigFields_happyPath() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(tournamentRepository.existsByFormatAndStatusNotIn(anyString(), any())).thenReturn(false);
		when(configFieldRepository.findById("bracket_size"))
				.thenReturn(Optional.of(ConfigFieldDefinition.builder().fieldKey("bracket_size").build()));
		when(formatConfigFieldRepository.findByFormatCodeAndFieldKey(CODE, "bracket_size"))
				.thenReturn(Optional.empty());

		FormatConfigFieldsSaveResponse response =
				service.saveConfigFields(CODE, fieldsRequest(fieldItem("bracket_size", "32", true, true)));

		assertEquals(1, response.getFieldsSaved());
		assertEquals("race-to-rules", response.getNextStep());
		verify(formatConfigFieldRepository, times(1)).save(any(FormatConfigField.class));
	}

	@Test
	@DisplayName("TC-010 · Editing a format already used by a live tournament is blocked")
	void TC010_saveConfigFields_formatInUse() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(tournamentRepository.existsByFormatAndStatusNotIn(anyString(), any())).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.saveConfigFields(CODE, fieldsRequest(fieldItem("bracket_size", "32", true, true))));

		// Otherwise a tournament already running would silently change configuration mid-flight,
		// because any field without its own override resolves to the format default
		assertEquals(ErrorCode.FORMAT_IN_USE_CANNOT_EDIT, ex.getErrorCode());
		verify(formatConfigFieldRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-011 · Assigning a field key that is not in the catalog")
	void TC011_saveConfigFields_unknownFieldKey() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(tournamentRepository.existsByFormatAndStatusNotIn(anyString(), any())).thenReturn(false);
		when(configFieldRepository.findById("no_such")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.saveConfigFields(CODE, fieldsRequest(fieldItem("no_such", "1", true, true))));

		assertEquals(ErrorCode.INVALID_FIELD_KEY, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-012 · Omitted flags default to required and visible")
	void TC012_saveConfigFields_flagDefaults() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(tournamentRepository.existsByFormatAndStatusNotIn(anyString(), any())).thenReturn(false);
		when(configFieldRepository.findById("bracket_size"))
				.thenReturn(Optional.of(ConfigFieldDefinition.builder().fieldKey("bracket_size").build()));
		when(formatConfigFieldRepository.findByFormatCodeAndFieldKey(CODE, "bracket_size"))
				.thenReturn(Optional.empty());

		service.saveConfigFields(CODE, fieldsRequest(fieldItem("bracket_size", "32", null, null)));

		ArgumentCaptor<FormatConfigField> saved = ArgumentCaptor.forClass(FormatConfigField.class);
		verify(formatConfigFieldRepository).save(saved.capture());
		// UC-13 Request Fields makes both flags default to Yes
		assertTrue(saved.getValue().getIsRequired());
		assertTrue(saved.getValue().getIsVisibleToOwner());
	}

	@Test
	@DisplayName("TC-013 · Reassigning an existing field updates it in place")
	void TC013_saveConfigFields_upsertsExisting() {
		FormatConfigField existing = configField("bracket_size", "16");
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(tournamentRepository.existsByFormatAndStatusNotIn(anyString(), any())).thenReturn(false);
		when(configFieldRepository.findById("bracket_size"))
				.thenReturn(Optional.of(ConfigFieldDefinition.builder().fieldKey("bracket_size").build()));
		when(formatConfigFieldRepository.findByFormatCodeAndFieldKey(CODE, "bracket_size"))
				.thenReturn(Optional.of(existing));

		service.saveConfigFields(CODE, fieldsRequest(fieldItem("bracket_size", "32", true, true)));

		// UC-13 BR-03 makes the format-plus-field pair unique, so this has to update rather
		// than insert a second row
		assertEquals("32", existing.getDefaultValue());
		verify(formatConfigFieldRepository).save(existing);
	}

	// ══════════════════════════ activateFormat — UC-13 BR-06 ══════════════════════════

	@Test
	@DisplayName("TC-014 · Activating a fully configured format")
	void TC014_activateFormat_happyPath() {
		TournamentFormatDefinition existing = format();
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(existing));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(1L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(1L);
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(configField("bracket_size", "32")));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(raceToRule("final", 9)));

		FormatActivateResponse response = service.activateFormat(CODE);

		assertTrue(response.getIsActive());
		assertTrue(existing.getIsActive());
		verify(formatRepository).save(existing);
	}

	@Test
	@DisplayName("TC-015 · Activating with no config field assigned")
	void TC015_activateFormat_noConfigFields() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(0L);
		lenient().when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(1L);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.activateFormat(CODE));

		assertEquals(ErrorCode.SETUP_INCOMPLETE, ex.getErrorCode());
		verify(formatRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-016 · Activating with no race-to rule")
	void TC016_activateFormat_noRaceToRules() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(1L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(0L);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.activateFormat(CODE));

		assertEquals(ErrorCode.SETUP_INCOMPLETE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-017 · Activating with a blank default value")
	void TC017_activateFormat_blankDefaultValue() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(1L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(1L);
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(configField("bracket_size", "   ")));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(raceToRule("final", 9)));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.activateFormat(CODE));

		// UC-13 BR-02: every assigned config field must carry a non-empty default
		assertEquals(ErrorCode.SETUP_INCOMPLETE, ex.getErrorCode());
		verify(formatRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-018 · Activating with a non-positive race-to value")
	void TC018_activateFormat_invalidRaceTo() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(1L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(1L);
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(configField("bracket_size", "32")));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(raceToRule("final", 0)));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.activateFormat(CODE));

		// UC-13 BR-04: a race-to rule needs a positive target
		assertEquals(ErrorCode.SETUP_INCOMPLETE, ex.getErrorCode());
	}

	// ══════════════════════════ bootstrapDefaults — UC-13 AF-01 ══════════════════════════

	@Test
	@DisplayName("TC-019 · Bootstrapping a format that already holds configuration")
	void TC019_bootstrap_alreadyConfigured() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(3L);
		lenient().when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(2L);

		BootstrapDefaultsRequest request = new BootstrapDefaultsRequest();
		request.setOverwrite(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.bootstrapDefaults(CODE, request));

		assertEquals(ErrorCode.ALREADY_BOOTSTRAPPED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-020 · Bootstrapping a format with no template available")
	void TC020_bootstrap_unsupportedFormat() {
		when(formatRepository.findById("CUSTOM_FMT"))
				.thenReturn(Optional.of(TournamentFormatDefinition.builder().code("CUSTOM_FMT").build()));
		when(formatConfigFieldRepository.countByFormatCode("CUSTOM_FMT")).thenReturn(0L);
		when(formatRaceToRuleRepository.countByFormatCode("CUSTOM_FMT")).thenReturn(0L);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.bootstrapDefaults("CUSTOM_FMT", null));

		// A format the bootstrap templates do not cover has to be configured by hand
		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-021 · A null request is read as no overwrite")
	void TC021_bootstrap_nullRequestMeansNoOverwrite() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(1L);
		lenient().when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(0L);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.bootstrapDefaults(CODE, null));

		// Failing closed: a missing body must not be taken as permission to overwrite
		assertEquals(ErrorCode.ALREADY_BOOTSTRAPPED, ex.getErrorCode());
	}

	// ══════════════════════════ getFormat / getSetupStatus ══════════════════════════

	@Test
	@DisplayName("TC-022 · Opening a format that does not exist")
	void TC022_getFormat_notFound() {
		when(formatRepository.findById("NO_SUCH")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.getFormat("NO_SUCH"));

		assertEquals(ErrorCode.FORMAT_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-023 · Setup status of a format with no configuration")
	void TC023_getSetupStatus_emptyFormat() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE)).thenReturn(List.of());
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE)).thenReturn(List.of());

		var status = service.getSetupStatus(CODE);

		// An empty format cannot be activated and the response says which steps are missing
		assertFalse(status.isCanActivate());
		assertFalse(status.getMissingSteps().isEmpty());
		assertEquals(0L, status.getConfigFieldCount());
		assertEquals(0L, status.getRaceToRuleCount());
	}
}
