package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.BootstrapDefaultsRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateConfigFieldCatalogRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateFormatRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateGameTypeRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchFormatActiveRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateConfigFieldCatalogRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateFormatRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateGameTypeRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpsertFormatConfigFieldsRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpsertFormatRaceToRulesRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ConfigFieldCatalogItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatActivateResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatBootstrapResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatConfigFieldsFormResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatConfigFieldsSaveResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatCreateResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatListItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatRaceToRulesFormResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatRaceToRulesSaveResponse;
import com.capstone.su26_sep490_g2_be.dto.response.FormatSetupSummaryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.GameTypeDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.FormatConfigField;
import com.capstone.su26_sep490_g2_be.entity.FormatRaceToRule;
import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ConfigFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.FormatConfigFieldRepository;
import com.capstone.su26_sep490_g2_be.repository.FormatRaceToRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentFormatDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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

	private static UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest ruleItem(
			String roundKey, String label, String phase, Integer raceTo) {
		UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest i =
				new UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest();
		i.setRoundKey(roundKey);
		i.setLabel(label);
		i.setBracketPhase(phase);
		i.setRaceTo(raceTo);
		return i;
	}

	private static UpsertFormatRaceToRulesRequest rulesRequest(
			UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest... items) {
		UpsertFormatRaceToRulesRequest r = new UpsertFormatRaceToRulesRequest();
		r.setRules(List.of(items));
		return r;
	}

	private static ConfigFieldDefinition catalogField(String key, String dataType, String uiComponent) {
		return ConfigFieldDefinition.builder()
				.fieldKey(key).label("Kích thước nhánh").description("Số tay cơ vào nhánh")
				.dataType(dataType).fieldScope("KNOCKOUT").uiComponent(uiComponent)
				.minValue(4).maxValue(128).isActive(true)
				.build();
	}

	private static CreateConfigFieldCatalogRequest catalogCreateRequest(
			String dataType, String scope, String uiComponent) {
		CreateConfigFieldCatalogRequest r = new CreateConfigFieldCatalogRequest();
		r.setFieldKey("bracket_size");
		r.setLabel("Kích thước nhánh");
		r.setDataType(dataType);
		r.setFieldScope(scope);
		r.setUiComponent(uiComponent);
		return r;
	}

	private static GameTypeDefinition gameType(String code) {
		return GameTypeDefinition.builder()
				.code(code).name("9-Ball").description("Race-to, alternate break")
				.defaultRaceTo(7).compatibleTableTypes("[\"POOL\"]").isActive(true)
				.build();
	}

	/**
	 * Runs a captured {@link Specification} against a mocked Criteria API so the lambda body — the
	 * part a plain repository mock never reaches — actually executes and can be asserted on.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> CriteriaBuilder runSpecification(Specification<T> spec, Root<T> root) {
		CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
		spec.toPredicate(root, mock(CriteriaQuery.class), cb);
		return cb;
	}

	@SuppressWarnings("unchecked")
	private Specification<ConfigFieldDefinition> captureCatalogSpecification() {
		ArgumentCaptor<Specification> captor = ArgumentCaptor.forClass(Specification.class);
		verify(configFieldRepository).findAll(captor.capture(), any(Pageable.class));
		return captor.getValue();
	}

	@SuppressWarnings("unchecked")
	private Specification<GameTypeDefinition> captureGameTypeSpecification() {
		ArgumentCaptor<Specification> captor = ArgumentCaptor.forClass(Specification.class);
		verify(gameTypeRepository).findAll(captor.capture(), any(Pageable.class));
		return captor.getValue();
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

	// ══════════════ config field catalog — UC-10.2 (read) ══════════════

	@Test
	@DisplayName("TC-024 · Listing the catalog returns the stored enum options as a list")
	void TC024_getConfigFieldCatalog_mapsEntities() {
		ConfigFieldDefinition def = catalogField("break_rule", "ENUM", "SELECT");
		def.setEnumOptions("[\"ALTERNATE_BREAK\",\"WINNER_BREAK\"]");
		when(configFieldRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(def)));

		PageResponse<ConfigFieldCatalogItemResponse> response =
				service.getConfigFieldCatalog(null, null, 0, 20);

		assertEquals(1, response.getContent().size());
		ConfigFieldCatalogItemResponse item = response.getContent().get(0);
		assertEquals("break_rule", item.getFieldKey());
		// The column stores JSON; the catalog screen needs the options back as a real list
		assertEquals(List.of("ALTERNATE_BREAK", "WINNER_BREAK"), item.getEnumOptions());
	}

	@Test
	@DisplayName("TC-025 · Scope filter accepts both a comma-separated value and repeated parameters")
	void TC025_getConfigFieldCatalog_scopeFilterNormalised() {
		when(configFieldRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		service.getConfigFieldCatalog(List.of("common,knockout", " group ", "COMMON"), null, 0, 20);

		Root<ConfigFieldDefinition> root = mock(Root.class, RETURNS_DEEP_STUBS);
		Path<Object> scopePath = root.get("fieldScope");
		runSpecification(captureCatalogSpecification(), root);

		ArgumentCaptor<Collection> scopes = ArgumentCaptor.forClass(Collection.class);
		verify(scopePath).in(scopes.capture());
		// Trimmed, upper-cased and de-duplicated, so scope=COMMON,KNOCKOUT and
		// scope=COMMON&scope=KNOCKOUT (what axios sends) behave the same way
		assertEquals(List.of("COMMON", "KNOCKOUT", "GROUP"), List.copyOf(scopes.getValue()));
	}

	@Test
	@DisplayName("TC-026 · No filter builds a specification that matches everything")
	void TC026_getConfigFieldCatalog_noFilter() {
		when(configFieldRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		service.getConfigFieldCatalog(List.of(), null, 0, 20);

		CriteriaBuilder cb = runSpecification(captureCatalogSpecification(),
				mock(Root.class, RETURNS_DEEP_STUBS));
		verify(cb).conjunction();
		verify(cb, never()).equal(any(Expression.class), any(Object.class));
	}

	@Test
	@DisplayName("TC-027 · Opening a catalog field that does not exist")
	void TC027_getConfigFieldCatalogItem_notFound() {
		when(configFieldRepository.findById("no_such")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getConfigFieldCatalogItem("no_such"));

		assertEquals(ErrorCode.INVALID_FIELD_KEY, ex.getErrorCode());
	}

	// ══════════════ createConfigFieldCatalogItem — UC-10.1 ══════════════

	@Test
	@DisplayName("TC-028 · Creating a catalog field normalises its three type columns")
	void TC028_createConfigField_normalisesAndDefaultsActive() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);
		when(configFieldRepository.save(any(ConfigFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		CreateConfigFieldCatalogRequest request = catalogCreateRequest(" int ", " knockout ", " number ");
		ConfigFieldCatalogItemResponse response = service.createConfigFieldCatalogItem(request);

		// Everything downstream compares these three columns as upper-case constants
		assertEquals("INT", response.getDataType());
		assertEquals("KNOCKOUT", response.getFieldScope());
		assertEquals("NUMBER", response.getUiComponent());
		// UC-10.1 Request Fields: isActive defaults to true when omitted
		assertTrue(response.getIsActive());
		assertNull(response.getEnumOptions());
	}

	@Test
	@DisplayName("TC-029 · A field key already in the catalog is rejected")
	void TC029_createConfigField_duplicateKey() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createConfigFieldCatalogItem(catalogCreateRequest("INT", "KNOCKOUT", "NUMBER")));

		assertEquals(ErrorCode.DUPLICATE_RESOURCE, ex.getErrorCode());
		verify(configFieldRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-030 · An unsupported data type is rejected")
	void TC030_createConfigField_invalidDataType() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createConfigFieldCatalogItem(catalogCreateRequest("DECIMAL", "KNOCKOUT", "NUMBER")));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		verify(configFieldRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-031 · An unsupported field scope is rejected")
	void TC031_createConfigField_invalidScope() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createConfigFieldCatalogItem(catalogCreateRequest("INT", "LEAGUE", "NUMBER")));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-032 · A UI component that contradicts the data type is rejected")
	void TC032_createConfigField_uiComponentMismatch() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createConfigFieldCatalogItem(catalogCreateRequest("INT", "KNOCKOUT", "SELECT")));

		// An INT rendered as a dropdown would give the Owner no valid way to enter a value
		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		verify(configFieldRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-033 · An ENUM field with no options is rejected")
	void TC033_createConfigField_enumWithoutOptions() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createConfigFieldCatalogItem(catalogCreateRequest("ENUM", "COMMON", "SELECT")));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-034 · An INT field whose minimum exceeds its maximum is rejected")
	void TC034_createConfigField_minAboveMax() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);

		CreateConfigFieldCatalogRequest request = catalogCreateRequest("INT", "KNOCKOUT", "NUMBER");
		request.setMinValue(64);
		request.setMaxValue(16);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createConfigFieldCatalogItem(request));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-035 · An ENUM field with options is stored as JSON")
	void TC035_createConfigField_enumOptionsStoredAsJson() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);
		when(configFieldRepository.save(any(ConfigFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		CreateConfigFieldCatalogRequest request = catalogCreateRequest("ENUM", "COMMON", "SELECT");
		request.setEnumOptions(List.of("ALTERNATE_BREAK", "WINNER_BREAK"));
		request.setIsActive(false);

		ConfigFieldCatalogItemResponse response = service.createConfigFieldCatalogItem(request);

		ArgumentCaptor<ConfigFieldDefinition> saved = ArgumentCaptor.forClass(ConfigFieldDefinition.class);
		verify(configFieldRepository).save(saved.capture());
		assertEquals("[\"ALTERNATE_BREAK\",\"WINNER_BREAK\"]", saved.getValue().getEnumOptions());
		// An explicit false is honoured, so a field can be seeded before it is offered to Admins
		assertFalse(response.getIsActive());
	}

	// ══════════════ updateConfigFieldCatalogItem — UC-10.3 / UC-10.4 ══════════════

	@Test
	@DisplayName("TC-036 · Updating a catalog field leaves its data type and scope untouched")
	void TC036_updateConfigField_happyPath() {
		ConfigFieldDefinition existing = catalogField("bracket_size", "INT", "NUMBER");
		when(configFieldRepository.findById("bracket_size")).thenReturn(Optional.of(existing));
		when(configFieldRepository.save(existing)).thenReturn(existing);

		UpdateConfigFieldCatalogRequest request = new UpdateConfigFieldCatalogRequest();
		request.setLabel("Số tay cơ");
		request.setDescription("Số tay cơ vào nhánh loại trực tiếp");
		request.setUiComponent(" number ");
		request.setMinValue(8);
		request.setMaxValue(64);

		ConfigFieldCatalogItemResponse response = service.updateConfigFieldCatalogItem("bracket_size", request);

		assertEquals("Số tay cơ", response.getLabel());
		assertEquals("NUMBER", response.getUiComponent());
		assertEquals(8, response.getMinValue());
		assertEquals(64, response.getMaxValue());
		// UC-10.3 forbids changing these two once definitions are in use by a format
		assertEquals("INT", response.getDataType());
		assertEquals("KNOCKOUT", response.getFieldScope());
	}

	@Test
	@DisplayName("TC-037 · Clearing the options of an ENUM field is rejected")
	void TC037_updateConfigField_enumOptionsCleared() {
		when(configFieldRepository.findById("break_rule"))
				.thenReturn(Optional.of(catalogField("break_rule", "ENUM", "SELECT")));

		UpdateConfigFieldCatalogRequest request = new UpdateConfigFieldCatalogRequest();
		request.setLabel("Luật phá");
		request.setUiComponent("SELECT");
		request.setEnumOptions(List.of());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateConfigFieldCatalogItem("break_rule", request));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		verify(configFieldRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-038 · A UI component that no longer matches the stored data type is rejected")
	void TC038_updateConfigField_uiComponentMismatch() {
		when(configFieldRepository.findById("bracket_size"))
				.thenReturn(Optional.of(catalogField("bracket_size", "INT", "NUMBER")));

		UpdateConfigFieldCatalogRequest request = new UpdateConfigFieldCatalogRequest();
		request.setLabel("Kích thước nhánh");
		request.setUiComponent("CHECKBOX");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateConfigFieldCatalogItem("bracket_size", request));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-039 · Updating an INT field with a minimum above its maximum is rejected")
	void TC039_updateConfigField_minAboveMax() {
		when(configFieldRepository.findById("bracket_size"))
				.thenReturn(Optional.of(catalogField("bracket_size", "INT", "NUMBER")));

		UpdateConfigFieldCatalogRequest request = new UpdateConfigFieldCatalogRequest();
		request.setLabel("Kích thước nhánh");
		request.setUiComponent("NUMBER");
		request.setMinValue(64);
		request.setMaxValue(16);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateConfigFieldCatalogItem("bracket_size", request));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-040 · Disabling a catalog field keeps the row")
	void TC040_patchConfigFieldCatalogActive_disables() {
		ConfigFieldDefinition existing = catalogField("bracket_size", "INT", "NUMBER");
		when(configFieldRepository.findById("bracket_size")).thenReturn(Optional.of(existing));
		when(configFieldRepository.save(existing)).thenReturn(existing);

		PatchFormatActiveRequest request = new PatchFormatActiveRequest();
		request.setIsActive(false);

		assertFalse(service.patchConfigFieldCatalogActive("bracket_size", request).getIsActive());
		// UC-10.4 is a soft disable — formats already referencing the field keep working
		verify(configFieldRepository, never()).delete(any(ConfigFieldDefinition.class));
	}

	// ══════════════ listFormats — UC-12.2 ══════════════

	@Test
	@DisplayName("TC-041 · Listing every format, unfiltered")
	void TC041_listFormats_unfiltered() {
		when(formatRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(format())));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(7L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(5L);

		PageResponse<FormatListItemResponse> response = service.listFormats(null, null, 0, 20);

		FormatListItemResponse item = response.getContent().get(0);
		assertEquals(7L, item.getConfigFieldCount());
		assertEquals(5L, item.getRaceToRuleCount());
		// Fully configured but still inactive — the wizard's last step is outstanding
		assertEquals(FormatSetupStatus.READY_TO_ACTIVATE, item.getSetupStatus());
	}

	@Test
	@DisplayName("TC-042 · Filtering by the active flag alone stays a database query")
	void TC042_listFormats_activeFilterOnly() {
		when(formatRepository.findByIsActive(any(Boolean.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(format())));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(0L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(0L);

		PageResponse<FormatListItemResponse> response = service.listFormats(false, null, 0, 20);

		assertEquals(1, response.getContent().size());
		assertEquals(FormatSetupStatus.INFO_DONE, response.getContent().get(0).getSetupStatus());
		verify(formatRepository, never()).findAll(any(Pageable.class));
	}

	@Test
	@DisplayName("TC-043 · Filtering by setup status falls back to in-memory paging")
	void TC043_listFormats_setupStatusFilter() {
		TournamentFormatDefinition ready = format();
		TournamentFormatDefinition untouched = TournamentFormatDefinition.builder()
				.code("GROUP_PLAYOFF").name("Group + Playoff").handlerKey("group-playoff").isActive(false)
				.build();
		when(formatRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(ready, untouched));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(7L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(5L);
		when(formatConfigFieldRepository.countByFormatCode("GROUP_PLAYOFF")).thenReturn(0L);
		when(formatRaceToRuleRepository.countByFormatCode("GROUP_PLAYOFF")).thenReturn(0L);

		PageResponse<FormatListItemResponse> response =
				service.listFormats(null, FormatSetupStatus.READY_TO_ACTIVATE, 0, 20);

		// setupStatus is derived, not stored, so it cannot be pushed down into the query
		assertEquals(1, response.getContent().size());
		assertEquals(CODE, response.getContent().get(0).getCode());
		assertEquals(1L, response.getTotalElements());
	}

	@Test
	@DisplayName("TC-044 · Setup status combined with the active flag narrows the source list first")
	void TC044_listFormats_setupStatusAndActiveFilter() {
		when(formatRepository.findByIsActiveOrderByCreatedAtAsc(false)).thenReturn(List.of(format()));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(0L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(0L);

		PageResponse<FormatListItemResponse> response =
				service.listFormats(false, FormatSetupStatus.READY_TO_ACTIVATE, 0, 20);

		// The only inactive format is still empty, so nothing matches READY_TO_ACTIVATE
		assertTrue(response.getContent().isEmpty());
		verify(formatRepository, never()).findAllByOrderByCreatedAtAsc();
	}

	// ══════════════ the wizard forms — UC-13 ══════════════

	@Test
	@DisplayName("TC-045 · An unconfigured format offers the whole active catalog")
	void TC045_getConfigFieldsForm_offersCatalog() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE)).thenReturn(List.of());
		when(configFieldRepository.findByIsActiveTrueOrderByFieldScopeAsc())
				.thenReturn(List.of(catalogField("bracket_size", "INT", "NUMBER"),
						catalogField("allow_bye", "BOOLEAN", "CHECKBOX")));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(0L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(0L);

		FormatConfigFieldsFormResponse response = service.getConfigFieldsForm(CODE);

		assertTrue(response.getFields().isEmpty());
		assertEquals(2, response.getAvailableFields().size());
		assertEquals(FormatSetupStatus.INFO_DONE, response.getSetupStatus());
	}

	@Test
	@DisplayName("TC-046 · A configured format returns its fields with the catalog metadata attached")
	void TC046_getConfigFieldsForm_returnsAssignedFields() {
		FormatConfigField assigned = configField("bracket_size", "32");
		assigned.setFieldDefinition(catalogField("bracket_size", "INT", "NUMBER"));
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE)).thenReturn(List.of(assigned));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(1L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(1L);

		FormatConfigFieldsFormResponse response = service.getConfigFieldsForm(CODE);

		assertEquals(1, response.getFields().size());
		assertEquals("Kích thước nhánh", response.getFields().get(0).getLabel());
		assertEquals("INT", response.getFields().get(0).getDataType());
		assertEquals("32", response.getFields().get(0).getDefaultValue());
		// The picker is only shown while the format is still empty
		assertTrue(response.getAvailableFields().isEmpty());
		verify(configFieldRepository, never()).findByIsActiveTrueOrderByFieldScopeAsc();
	}

	@Test
	@DisplayName("TC-047 · Reading the race-to form of a configured format")
	void TC047_getRaceToRulesForm_returnsRules() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(raceToRule("final", 9)));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(1L);

		FormatRaceToRulesFormResponse response = service.getRaceToRulesForm(CODE);

		assertEquals(1, response.getRules().size());
		assertEquals("final", response.getRules().get(0).getRoundKey());
		assertEquals(9, response.getRules().get(0).getRaceTo());
		assertEquals(FormatSetupStatus.READY_TO_ACTIVATE, response.getSetupStatus());
	}

	@Test
	@DisplayName("TC-048 · Saving race-to rules moves the wizard to its review step")
	void TC048_saveRaceToRules_happyPath() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(tournamentRepository.existsByFormatAndStatusNotIn(anyString(), any())).thenReturn(false);
		when(formatRaceToRuleRepository.findByFormatCodeAndRoundKey(CODE, "final")).thenReturn(Optional.empty());
		when(formatRaceToRuleRepository.findByFormatCodeAndRoundKey(CODE, "semi_final")).thenReturn(Optional.empty());

		FormatRaceToRulesSaveResponse response = service.saveRaceToRules(CODE, rulesRequest(
				ruleItem("semi_final", "Bán kết", "KNOCKOUT", 7),
				ruleItem("final", "Chung kết", "KNOCKOUT", 9)));

		assertEquals(2, response.getRulesSaved());
		assertEquals(FormatSetupStatus.RACE_TO_DONE, response.getSetupStatus());
		assertEquals("review", response.getNextStep());
		verify(formatRaceToRuleRepository, times(2)).save(any(FormatRaceToRule.class));
	}

	@Test
	@DisplayName("TC-049 · Editing race-to rules of a format used by a live tournament is blocked")
	void TC049_saveRaceToRules_formatInUse() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(tournamentRepository.existsByFormatAndStatusNotIn(anyString(), any())).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.saveRaceToRules(
				CODE, rulesRequest(ruleItem("final", "Chung kết", "KNOCKOUT", 9))));

		// A running tournament resolves its race-to from the format default, so changing it
		// mid-flight would alter matches that have already been played
		assertEquals(ErrorCode.FORMAT_IN_USE_CANNOT_EDIT, ex.getErrorCode());
		verify(formatRaceToRuleRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-050 · Resubmitting a round key updates the rule in place")
	void TC050_saveRaceToRules_upsertsExisting() {
		FormatRaceToRule existing = raceToRule("final", 7);
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(tournamentRepository.existsByFormatAndStatusNotIn(anyString(), any())).thenReturn(false);
		when(formatRaceToRuleRepository.findByFormatCodeAndRoundKey(CODE, "final"))
				.thenReturn(Optional.of(existing));

		service.saveRaceToRules(CODE, rulesRequest(ruleItem("final", "Chung kết", "KNOCKOUT", 11)));

		// UC-13 BR-05 makes the format-plus-round pair unique
		assertEquals(11, existing.getRaceTo());
		assertEquals("Chung kết", existing.getLabel());
		verify(formatRaceToRuleRepository).save(existing);
	}

	// ══════════════ getSetupSummary / getSetupStatus — UC-12.2, UC-13 ══════════════

	@Test
	@DisplayName("TC-051 · The review screen of a complete but inactive format")
	void TC051_getSetupSummary_readyToActivate() {
		FormatConfigField assigned = configField("bracket_size", "32");
		assigned.setFieldDefinition(catalogField("bracket_size", "INT", "NUMBER"));
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE)).thenReturn(List.of(assigned));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(raceToRule("final", 9)));

		FormatSetupSummaryResponse response = service.getSetupSummary(CODE);

		assertTrue(response.isCanActivate());
		assertTrue(response.getValidationErrors().isEmpty());
		assertEquals(FormatSetupStatus.READY_TO_ACTIVATE, response.getSetupStatus());
		assertEquals("Kích thước nhánh", response.getConfigFields().get(0).getLabel());
		assertEquals(9, response.getRaceToRules().get(0).getRaceTo());
	}

	@Test
	@DisplayName("TC-052 · The review screen names every problem instead of only the first")
	void TC052_getSetupSummary_reportsValidationErrors() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(configField("bracket_size", "  ")));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(raceToRule("final", 0)));

		FormatSetupSummaryResponse response = service.getSetupSummary(CODE);

		assertFalse(response.isCanActivate());
		// One entry per offending row, so the Admin can fix the whole screen in one pass
		assertEquals(2, response.getValidationErrors().size());
		assertTrue(response.getValidationErrors().get(0).contains("bracket_size"));
		assertTrue(response.getValidationErrors().get(1).contains("final"));
		// A field with no catalog row falls back to its key as the label
		assertEquals("bracket_size", response.getConfigFields().get(0).getLabel());
	}

	@Test
	@DisplayName("TC-053 · An already active format cannot be activated again")
	void TC053_getSetupSummary_alreadyActive() {
		TournamentFormatDefinition active = format();
		active.setIsActive(true);
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(active));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(configField("bracket_size", "32")));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(raceToRule("final", 9)));

		FormatSetupSummaryResponse response = service.getSetupSummary(CODE);

		assertFalse(response.isCanActivate());
		assertEquals(FormatSetupStatus.ACTIVE, response.getSetupStatus());
		assertTrue(response.getValidationErrors().isEmpty());
	}

	@Test
	@DisplayName("TC-054 · The setup status of an active format lists no outstanding step")
	void TC054_getSetupStatus_activeFormat() {
		TournamentFormatDefinition active = format();
		active.setIsActive(true);
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(active));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(7L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(5L);
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(configField("bracket_size", "32")));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(raceToRule("final", 9)));

		var status = service.getSetupStatus(CODE);

		assertEquals(FormatSetupStatus.ACTIVE, status.getSetupStatus());
		assertTrue(status.getMissingSteps().isEmpty());
		assertTrue(status.isBootstrapped());
		assertFalse(status.isCanActivate());
	}

	@Test
	@DisplayName("TC-055 · A complete inactive format reports activation as its only outstanding step")
	void TC055_getSetupStatus_readyToActivate() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(7L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(5L);
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(configField("bracket_size", "32")));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(raceToRule("final", 9)));

		var status = service.getSetupStatus(CODE);

		assertEquals(FormatSetupStatus.READY_TO_ACTIVATE, status.getSetupStatus());
		assertEquals(List.of("activate"), status.getMissingSteps());
		assertTrue(status.isCanActivate());
	}

	// ══════════════ bootstrapDefaults — UC-13 AF-01 ══════════════

	@Test
	@DisplayName("TC-056 · Quick-init writes the whole single-elimination template in one call")
	void TC056_bootstrap_happyPath() {
		String code = "SINGLE_ELIMINATION";
		when(formatRepository.findById(code)).thenReturn(Optional.of(
				TournamentFormatDefinition.builder().code(code).name("Single Elimination").isActive(false).build()));
		when(formatConfigFieldRepository.countByFormatCode(code)).thenReturn(0L);
		when(formatRaceToRuleRepository.countByFormatCode(code)).thenReturn(0L);
		when(tournamentRepository.existsByFormatAndStatusNotIn(anyString(), any())).thenReturn(false);
		when(configFieldRepository.findById(anyString()))
				.thenAnswer(inv -> Optional.of(ConfigFieldDefinition.builder()
						.fieldKey(inv.getArgument(0)).build()));
		when(formatConfigFieldRepository.findByFormatCodeAndFieldKey(anyString(), anyString()))
				.thenReturn(Optional.empty());
		when(formatRaceToRuleRepository.findByFormatCodeAndRoundKey(anyString(), anyString()))
				.thenReturn(Optional.empty());

		FormatBootstrapResponse response = service.bootstrapDefaults(code, null);

		assertEquals(7, response.getConfigFieldsInserted());
		assertEquals(5, response.getRaceToRulesInserted());
		// The template fills both wizard screens, so the format lands straight on the review step
		assertEquals(FormatSetupStatus.READY_TO_ACTIVATE, response.getSetupStatus());
	}

	@Test
	@DisplayName("TC-057 · Quick-init with overwrite replaces an existing configuration")
	void TC057_bootstrap_overwrite() {
		String code = "PROGRESSIVE_ROUND_ROBIN";
		when(formatRepository.findById(code)).thenReturn(Optional.of(
				TournamentFormatDefinition.builder().code(code).name("Progressive").isActive(false).build()));
		lenient().when(formatConfigFieldRepository.countByFormatCode(code)).thenReturn(6L);
		lenient().when(formatRaceToRuleRepository.countByFormatCode(code)).thenReturn(2L);
		when(tournamentRepository.existsByFormatAndStatusNotIn(anyString(), any())).thenReturn(false);
		when(configFieldRepository.findById(anyString()))
				.thenAnswer(inv -> Optional.of(ConfigFieldDefinition.builder()
						.fieldKey(inv.getArgument(0)).build()));
		when(formatConfigFieldRepository.findByFormatCodeAndFieldKey(anyString(), anyString()))
				.thenAnswer(inv -> Optional.of(configField(inv.getArgument(1), "old")));
		when(formatRaceToRuleRepository.findByFormatCodeAndRoundKey(anyString(), anyString()))
				.thenReturn(Optional.empty());

		BootstrapDefaultsRequest request = new BootstrapDefaultsRequest();
		request.setOverwrite(true);

		FormatBootstrapResponse response = service.bootstrapDefaults(code, request);

		assertEquals(6, response.getConfigFieldsInserted());
		assertEquals(2, response.getRaceToRulesInserted());
	}

	// ══════════════ game types — UC-11 ══════════════

	@Test
	@DisplayName("TC-058 · Searching game types matches the code and the name, case-insensitively")
	void TC058_listGameTypes_searchSpecification() {
		when(gameTypeRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(gameType("9_BALL"))));

		PageResponse<GameTypeDetailResponse> response = service.listGameTypes(true, "  9Ba  ", 0, 20);

		assertEquals(List.of("POOL"), response.getContent().get(0).getCompatibleTableTypes());

		CriteriaBuilder cb = runSpecification(captureGameTypeSpecification(),
				mock(Root.class, RETURNS_DEEP_STUBS));
		verify(cb).equal(any(Expression.class), eq((Object) Boolean.TRUE));
		// One LIKE for the code and one for the name, both against the trimmed lower-cased term
		verify(cb, times(2)).like(any(Expression.class), eq("%9ba%"));
		verify(cb).or(any(Predicate.class), any(Predicate.class));
	}

	@Test
	@DisplayName("TC-059 · Listing game types with no filter applies no predicate")
	void TC059_listGameTypes_noFilter() {
		when(gameTypeRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		service.listGameTypes(null, "   ", 0, 20);

		CriteriaBuilder cb = runSpecification(captureGameTypeSpecification(),
				mock(Root.class, RETURNS_DEEP_STUBS));
		// A blank search term is not a filter
		verify(cb, never()).like(any(Expression.class), anyString());
		verify(cb, never()).equal(any(Expression.class), any(Object.class));
	}

	@Test
	@DisplayName("TC-060 · Opening a game type that does not exist")
	void TC060_getGameType_notFound() {
		when(gameTypeRepository.findById("NO_SUCH")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.getGameType("NO_SUCH"));

		assertEquals(ErrorCode.GAME_TYPE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-061 · Creating a game type upper-cases its code and defaults it to active")
	void TC061_createGameType_normalisesCode() {
		when(gameTypeRepository.existsById("9_BALL")).thenReturn(false);
		when(gameTypeRepository.save(any(GameTypeDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		CreateGameTypeRequest request = new CreateGameTypeRequest();
		request.setCode(" 9_ball ");
		request.setName("9-Ball");
		request.setDefaultRaceTo(7);
		request.setCompatibleTableTypes(List.of("POOL"));

		GameTypeDetailResponse response = service.createGameType(request);

		// The code doubles as the primary key, so it is normalised before the uniqueness check
		assertEquals("9_BALL", response.getCode());
		assertTrue(response.getIsActive());
		assertEquals(List.of("POOL"), response.getCompatibleTableTypes());
	}

	@Test
	@DisplayName("TC-062 · A game type code already in use is rejected")
	void TC062_createGameType_duplicateCode() {
		when(gameTypeRepository.existsById("9_BALL")).thenReturn(true);

		CreateGameTypeRequest request = new CreateGameTypeRequest();
		request.setCode("9_BALL");
		request.setName("9-Ball");

		BusinessException ex = assertThrows(BusinessException.class, () -> service.createGameType(request));

		assertEquals(ErrorCode.DUPLICATE_RESOURCE, ex.getErrorCode());
		verify(gameTypeRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-063 · Omitting the active flag on update leaves it alone")
	void TC063_updateGameType_nullActiveFlagPreserved() {
		GameTypeDefinition existing = gameType("9_BALL");
		when(gameTypeRepository.findById("9_BALL")).thenReturn(Optional.of(existing));

		UpdateGameTypeRequest request = new UpdateGameTypeRequest();
		request.setName("9-Ball (bida lỗ)");
		request.setDefaultRaceTo(9);

		GameTypeDetailResponse response = service.updateGameType("9_BALL", request);

		assertEquals("9-Ball (bida lỗ)", response.getName());
		assertEquals(9, response.getDefaultRaceTo());
		// A partial update must not silently disable a game type in use by live tournaments
		assertTrue(response.getIsActive());
		verify(gameTypeRepository).save(existing);
	}

	@Test
	@DisplayName("TC-064 · Updating a game type that does not exist")
	void TC064_updateGameType_notFound() {
		when(gameTypeRepository.findById("NO_SUCH")).thenReturn(Optional.empty());

		UpdateGameTypeRequest request = new UpdateGameTypeRequest();
		request.setName("Whatever");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateGameType("NO_SUCH", request));

		assertEquals(ErrorCode.GAME_TYPE_NOT_FOUND, ex.getErrorCode());
		verify(gameTypeRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-065 · Disabling a game type keeps the row")
	void TC065_patchGameTypeActive_disables() {
		GameTypeDefinition existing = gameType("9_BALL");
		when(gameTypeRepository.findById("9_BALL")).thenReturn(Optional.of(existing));
		when(gameTypeRepository.save(existing)).thenReturn(existing);

		PatchFormatActiveRequest request = new PatchFormatActiveRequest();
		request.setIsActive(false);

		assertFalse(service.patchGameTypeActive("9_BALL", request).getIsActive());
		// UC-11.4 is a soft disable — tournaments already played on this game type keep their history
		verify(gameTypeRepository, never()).delete(any(GameTypeDefinition.class));
	}

	// ══════════════ the type pairings and the sparse rows ══════════════

	@Test
	@DisplayName("TC-066 · A BOOLEAN field rendered as a checkbox is accepted")
	void TC066_createConfigField_booleanCheckboxPair() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);
		when(configFieldRepository.save(any(ConfigFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		ConfigFieldCatalogItemResponse response =
				service.createConfigFieldCatalogItem(catalogCreateRequest("BOOLEAN", "COMMON", "CHECKBOX"));

		assertEquals("BOOLEAN", response.getDataType());
		assertEquals("CHECKBOX", response.getUiComponent());
	}

	@Test
	@DisplayName("TC-067 · A STRING field rendered as a text box is accepted")
	void TC067_createConfigField_stringTextPair() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);
		when(configFieldRepository.save(any(ConfigFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		assertEquals("TEXT",
				service.createConfigFieldCatalogItem(catalogCreateRequest("STRING", "COMMON", "TEXT"))
						.getUiComponent());
	}

	@Test
	@DisplayName("TC-068 · A UI component outside the four allowed values is rejected first")
	void TC068_createConfigField_unknownUiComponent() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createConfigFieldCatalogItem(catalogCreateRequest("INT", "COMMON", "SLIDER")));

		// The allowed-set check runs before the pairing check, so the message names the four values
		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		verify(configFieldRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-069 · An INT field with only a lower bound is accepted")
	void TC069_createConfigField_onlyMinValue() {
		when(configFieldRepository.existsById("bracket_size")).thenReturn(false);
		when(configFieldRepository.save(any(ConfigFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		CreateConfigFieldCatalogRequest request = catalogCreateRequest("INT", "KNOCKOUT", "NUMBER");
		request.setMinValue(4);

		ConfigFieldCatalogItemResponse response = service.createConfigFieldCatalogItem(request);

		// The comparison only runs when both bounds are present, so an open upper end is fine
		assertEquals(4, response.getMinValue());
		assertNull(response.getMaxValue());
	}

	@Test
	@DisplayName("TC-070 · Updating an INT field with only an upper bound is accepted")
	void TC070_updateConfigField_onlyMaxValue() {
		ConfigFieldDefinition existing = catalogField("bracket_size", "INT", "NUMBER");
		when(configFieldRepository.findById("bracket_size")).thenReturn(Optional.of(existing));
		when(configFieldRepository.save(existing)).thenReturn(existing);

		UpdateConfigFieldCatalogRequest request = new UpdateConfigFieldCatalogRequest();
		request.setLabel("Kích thước nhánh");
		request.setUiComponent("NUMBER");
		request.setMaxValue(64);

		ConfigFieldCatalogItemResponse response = service.updateConfigFieldCatalogItem("bracket_size", request);

		assertNull(response.getMinValue());
		assertEquals(64, response.getMaxValue());
	}

	@Test
	@DisplayName("TC-071 · Updating a non-ENUM field ignores any options sent with it")
	void TC071_updateConfigField_optionsIgnoredForNonEnum() {
		ConfigFieldDefinition existing = catalogField("bracket_size", "INT", "NUMBER");
		when(configFieldRepository.findById("bracket_size")).thenReturn(Optional.of(existing));
		when(configFieldRepository.save(existing)).thenReturn(existing);

		UpdateConfigFieldCatalogRequest request = new UpdateConfigFieldCatalogRequest();
		request.setLabel("Kích thước nhánh");
		request.setUiComponent("NUMBER");
		request.setEnumOptions(List.of());

		// The empty-list guard only applies to an ENUM field, so this is not an error — the list
		// is simply written through to a column nothing on an INT field ever reads
		assertEquals(List.of(),
				service.updateConfigFieldCatalogItem("bracket_size", request).getEnumOptions());
	}

	@Test
	@DisplayName("TC-072 · The wizard form survives a field whose catalog row has gone")
	void TC072_getConfigFieldsForm_orphanedField() {
		FormatConfigField orphan = configField("bracket_size", "32");
		orphan.setFieldDefinition(null);
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE)).thenReturn(List.of(orphan));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(1L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(0L);

		FormatConfigFieldsFormResponse response = service.getConfigFieldsForm(CODE);

		FormatConfigFieldsFormResponse.FormatConfigFieldFormItemResponse item = response.getFields().get(0);
		// The key stands in for the label and every piece of catalog metadata comes back null,
		// so a definition deleted out from under a format does not break the wizard
		assertEquals("bracket_size", item.getLabel());
		assertNull(item.getDataType());
		assertNull(item.getUiComponent());
		assertNull(item.getEnumOptions());
		assertNull(item.getMinValue());
		assertEquals("32", item.getDefaultValue());
		// Config fields but no race-to rule yet
		assertEquals(FormatSetupStatus.CONFIG_FIELDS_DONE, response.getSetupStatus());
	}

	@Test
	@DisplayName("TC-073 · A format with rules but no fields still reports the missing step")
	void TC073_getSetupStatus_raceToWithoutConfigFields() {
		when(formatRepository.findById(CODE)).thenReturn(Optional.of(format()));
		when(formatConfigFieldRepository.countByFormatCode(CODE)).thenReturn(0L);
		when(formatRaceToRuleRepository.countByFormatCode(CODE)).thenReturn(5L);
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(CODE)).thenReturn(List.of());
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(CODE))
				.thenReturn(List.of(raceToRule("final", 9)));

		var status = service.getSetupStatus(CODE);

		// Bootstrapped is true as soon as either side holds rows, but the wizard is not done
		assertTrue(status.isBootstrapped());
		assertFalse(status.isCanActivate());
		assertEquals(List.of("config-fields"), status.getMissingSteps());
		assertEquals(FormatSetupStatus.INFO_DONE, status.getSetupStatus());
	}

	@Test
	@DisplayName("TC-074 · A null entry in the scope filter is skipped")
	void TC074_getConfigFieldCatalog_nullScopeEntry() {
		when(configFieldRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		List<String> scopes = new java.util.ArrayList<>();
		scopes.add(null);
		scopes.add("KNOCKOUT");
		service.getConfigFieldCatalog(scopes, true, 0, 20);

		Root<ConfigFieldDefinition> root = mock(Root.class, RETURNS_DEEP_STUBS);
		Path<Object> scopePath = root.get("fieldScope");
		CriteriaBuilder cb = runSpecification(captureCatalogSpecification(), root);

		ArgumentCaptor<Collection> captured = ArgumentCaptor.forClass(Collection.class);
		verify(scopePath).in(captured.capture());
		// A repeated query parameter with one empty value is what an unfilled filter chip sends
		assertEquals(List.of("KNOCKOUT"), List.copyOf(captured.getValue()));
		verify(cb).equal(any(Expression.class), eq((Object) Boolean.TRUE));
	}
}
