package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.SubmitTournamentRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationFormPreviewResponse;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFieldValue;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFieldValueId;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFormTemplate;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFormTemplateField;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldValueRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFormTemplateFieldRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFormTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link RegistrationFormServiceImpl}.
 *
 * <p>Mirrors the <b>RegistrationFormService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-14, UC-15, UC-23.1/23.2 BR-04.
 *
 * <p>This is where a player's submission is validated field by field against the catalog data
 * type. Wave 3 depends on it entirely, so the type rules are covered with boundary values.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · RegistrationFormService — UC-14, UC-15, UC-23")
class RegistrationFormServiceImplTest {

	@Mock RegistrationFormTemplateRepository templateRepository;
	@Mock RegistrationFormTemplateFieldRepository templateFieldRepository;
	@Mock RegistrationFieldDefinitionRepository fieldDefinitionRepository;
	@Mock RegistrationFieldValueRepository fieldValueRepository;

	@InjectMocks RegistrationFormServiceImpl formService;

	private static final Long TEMPLATE_ID = 5L;

	// ─────────────────────────── fixtures ───────────────────────────

	private static RegistrationFormTemplate template(boolean active) {
		return RegistrationFormTemplate.builder()
				.id(TEMPLATE_ID).code("BASIC_PLAYER").name("Basic player registration")
				.description("Standard form").isActive(active)
				.build();
	}

	private static RegistrationFormTemplateField field(String key, boolean required) {
		return RegistrationFormTemplateField.builder()
				.id(1L).templateId(TEMPLATE_ID).fieldKey(key)
				.isRequired(required).sortOrder(0)
				.build();
	}

	private static RegistrationFieldDefinition definition(String key, String dataType) {
		return RegistrationFieldDefinition.builder()
				.fieldKey(key).label("Full name").dataType(dataType).isActive(true)
				.build();
	}

	private static SubmitTournamentRegistrationRequest.FieldValueItem item(String key, String value) {
		SubmitTournamentRegistrationRequest.FieldValueItem i =
				new SubmitTournamentRegistrationRequest.FieldValueItem();
		i.setFieldKey(key);
		i.setValue(value);
		return i;
	}

	/** Wires one template field plus its catalog definition, then submits a single value. */
	private Map<String, String> submitSingle(
			RegistrationFormTemplateField templateField,
			RegistrationFieldDefinition def,
			String value) {
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(templateField));
		lenient().when(fieldDefinitionRepository.findById(templateField.getFieldKey()))
				.thenReturn(Optional.ofNullable(def));
		return formService.validateAndNormalizeFieldValues(
				TEMPLATE_ID, List.of(item(templateField.getFieldKey(), value)));
	}

	/** Asserts that submitting the given value is refused as a validation failure. */
	private void assertRejected(
			RegistrationFormTemplateField templateField,
			RegistrationFieldDefinition def,
			String value) {
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(templateField));
		lenient().when(fieldDefinitionRepository.findById(templateField.getFieldKey()))
				.thenReturn(Optional.ofNullable(def));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.validateAndNormalizeFieldValues(
						TEMPLATE_ID, List.of(item(templateField.getFieldKey(), value))));
		assertEquals(ErrorCode.REG_FORM_VALIDATION_FAILED, ex.getErrorCode());
	}

	// ══════════════ validateAndNormalizeFieldValues — submission handling ══════════════

	@Test
	@DisplayName("TC-001 · A complete, valid submission is normalised")
	void TC001_validate_happyPath() {
		RegistrationFormTemplateField name = field("full_name", true);
		RegistrationFormTemplateField age = field("age", false);
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(name, age));
		when(fieldDefinitionRepository.findById("full_name"))
				.thenReturn(Optional.of(definition("full_name", "STRING")));
		when(fieldDefinitionRepository.findById("age"))
				.thenReturn(Optional.of(definition("age", "INT")));

		Map<String, String> result = formService.validateAndNormalizeFieldValues(
				TEMPLATE_ID, List.of(item("full_name", "Nguyễn Văn A"), item("age", "25")));

		assertEquals("Nguyễn Văn A", result.get("full_name"));
		assertEquals("25", result.get("age"));
		assertEquals(2, result.size());
	}

	@Test
	@DisplayName("TC-002 · Template with no fields assigned")
	void TC002_validate_emptyTemplate() {
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.validateAndNormalizeFieldValues(TEMPLATE_ID, List.of()));

		assertEquals(ErrorCode.REG_TEMPLATE_INCOMPLETE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-003 · Submitting a field the template does not define")
	void TC003_validate_unknownFieldKey() {
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(field("full_name", true)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.validateAndNormalizeFieldValues(
						TEMPLATE_ID, List.of(item("full_name", "A"), item("is_admin", "true"))));

		// Blocks a crafted payload smuggling values into fields the Admin never put on the form
		assertEquals(ErrorCode.REG_FORM_VALIDATION_FAILED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-004 · Required field omitted entirely")
	void TC004_validate_requiredFieldMissing() {
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(field("full_name", true)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.validateAndNormalizeFieldValues(TEMPLATE_ID, List.of()));

		assertEquals(ErrorCode.REG_FORM_VALIDATION_FAILED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-005 · Required field sent as whitespace only")
	void TC005_validate_requiredFieldBlank() {
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(field("full_name", true)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.validateAndNormalizeFieldValues(
						TEMPLATE_ID, List.of(item("full_name", "   "))));

		assertEquals(ErrorCode.REG_FORM_VALIDATION_FAILED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-006 · Optional field omitted falls back to its default")
	void TC006_validate_optionalUsesDefault() {
		RegistrationFormTemplateField shirt = field("shirt_size", false);
		shirt.setDefaultValue("  M  ");
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(shirt));

		Map<String, String> result = formService.validateAndNormalizeFieldValues(TEMPLATE_ID, List.of());

		assertEquals("M", result.get("shirt_size"));
	}

	@Test
	@DisplayName("TC-007 · Optional field omitted with no default is left out")
	void TC007_validate_optionalWithoutDefaultOmitted() {
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(field("note", false)));

		Map<String, String> result = formService.validateAndNormalizeFieldValues(TEMPLATE_ID, List.of());

		// An empty string would later be indistinguishable from a real answer of ""
		assertFalse(result.containsKey("note"));
		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("TC-008 · The same field submitted twice")
	void TC008_validate_duplicateKeyKeepsLast() {
		Map<String, String> result = null;
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(field("full_name", true)));
		when(fieldDefinitionRepository.findById("full_name"))
				.thenReturn(Optional.of(definition("full_name", "STRING")));

		// Collectors.toMap throws on a duplicate key unless a merge function is supplied,
		// so a double-submit would otherwise surface as a 500
		result = formService.validateAndNormalizeFieldValues(
				TEMPLATE_ID, List.of(item("full_name", "A"), item("full_name", "B")));

		assertEquals("B", result.get("full_name"));
	}

	@Test
	@DisplayName("TC-009 · Template references a field missing from the catalog")
	void TC009_validate_definitionMissing() {
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(field("full_name", true)));
		when(fieldDefinitionRepository.findById("full_name")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.validateAndNormalizeFieldValues(
						TEMPLATE_ID, List.of(item("full_name", "A"))));

		assertEquals(ErrorCode.REG_FIELD_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-010 · Surrounding whitespace is trimmed before validation")
	void TC010_validate_trimsBeforeTypeCheck() {
		RegistrationFieldDefinition age = definition("age", "INT");
		age.setMinValue(18);

		Map<String, String> result = submitSingle(field("age", true), age, "  25  ");

		assertEquals("25", result.get("age"));
	}

	// ══════════════════════ validationRegex override ══════════════════════

	@Test
	@DisplayName("TC-011 · Value matching the template regex is accepted")
	void TC011_validate_regexMatches() {
		RegistrationFormTemplateField phone = field("phone", true);
		phone.setValidationRegex("^(0[3|5|7|8|9])[0-9]{8}$");

		Map<String, String> result = submitSingle(phone, definition("phone", "PHONE"), "0901234567");

		assertEquals("0901234567", result.get("phone"));
	}

	@Test
	@DisplayName("TC-012 · Value failing the template regex is rejected")
	void TC012_validate_regexRejects() {
		RegistrationFormTemplateField phone = field("phone", true);
		phone.setValidationRegex("^(0[3|5|7|8|9])[0-9]{8}$");

		assertRejected(phone, definition("phone", "PHONE"), "12345");
	}

	@Test
	@DisplayName("TC-013 · Blank regex is treated as no constraint")
	void TC013_validate_blankRegexIgnored() {
		RegistrationFormTemplateField note = field("note", true);
		note.setValidationRegex("   ");

		// Compiling and matching a blank regex would reject everything
		Map<String, String> result = submitSingle(note, definition("note", "STRING"), "anything at all");

		assertEquals("anything at all", result.get("note"));
	}

	// ══════════════════════════ BOOLEAN ══════════════════════════

	@Test
	@DisplayName("TC-014 · Boolean accepted in either case and normalised")
	void TC014_validate_booleanNormalised() {
		RegistrationFieldDefinition def = definition("agree_terms", "BOOLEAN");

		assertEquals("true", submitSingle(field("agree_terms", true), def, "TRUE").get("agree_terms"));
	}

	@Test
	@DisplayName("TC-015 · Anything other than true or false is rejected")
	void TC015_validate_booleanRejectsOther() {
		assertRejected(field("agree_terms", true), definition("agree_terms", "BOOLEAN"), "yes");
	}

	// ══════════════════════════ INT — boundaries ══════════════════════════

	private RegistrationFieldDefinition intField(Integer min, Integer max) {
		RegistrationFieldDefinition def = definition("age", "INT");
		def.setMinValue(min);
		def.setMaxValue(max);
		return def;
	}

	@Test
	@DisplayName("TC-016 · Integer inside the configured range")
	void TC016_validate_intInRange() {
		assertEquals("35", submitSingle(field("age", true), intField(18, 60), "35").get("age"));
	}

	@Test
	@DisplayName("TC-017 · Integer exactly at the minimum")
	void TC017_validate_intAtMin() {
		// The comparison is strictly less-than, so the boundary itself is valid
		assertEquals("18", submitSingle(field("age", true), intField(18, 60), "18").get("age"));
	}

	@Test
	@DisplayName("TC-018 · Integer one below the minimum")
	void TC018_validate_intBelowMin() {
		assertRejected(field("age", true), intField(18, 60), "17");
	}

	@Test
	@DisplayName("TC-019 · Integer exactly at the maximum")
	void TC019_validate_intAtMax() {
		assertEquals("60", submitSingle(field("age", true), intField(18, 60), "60").get("age"));
	}

	@Test
	@DisplayName("TC-020 · Integer one above the maximum")
	void TC020_validate_intAboveMax() {
		assertRejected(field("age", true), intField(18, 60), "61");
	}

	@Test
	@DisplayName("TC-021 · Non-numeric value for an integer field")
	void TC021_validate_intUnparseable() {
		assertRejected(field("age", true), intField(null, null), "twenty");
	}

	@Test
	@DisplayName("TC-022 · Integer field with no range configured")
	void TC022_validate_intWithoutBounds() {
		// An absent bound must not be treated as zero
		assertEquals("-999999",
				submitSingle(field("lucky_number", true), intField(null, null), "-999999").get("lucky_number"));
	}

	@Test
	@DisplayName("TC-023 · Leading zeros are normalised away")
	void TC023_validate_intStripsLeadingZeros() {
		assertEquals("7", submitSingle(field("age", true), intField(null, null), "007").get("age"));
	}

	// ══════════════════════════ DECIMAL ══════════════════════════

	private RegistrationFieldDefinition decimalField(Integer min, Integer max) {
		RegistrationFieldDefinition def = definition("handicap", "DECIMAL");
		def.setMinValue(min);
		def.setMaxValue(max);
		return def;
	}

	@Test
	@DisplayName("TC-024 · Decimal inside range, trailing zeros stripped")
	void TC024_validate_decimalStripsTrailingZeros() {
		assertEquals("7.5",
				submitSingle(field("handicap", true), decimalField(0, 10), "7.500").get("handicap"));
	}

	@Test
	@DisplayName("TC-025 · Decimal above the maximum")
	void TC025_validate_decimalAboveMax() {
		assertRejected(field("handicap", true), decimalField(0, 10), "10.5");
	}

	@Test
	@DisplayName("TC-026 · Non-numeric value for a decimal field")
	void TC026_validate_decimalUnparseable() {
		// A comma separator is the normal Vietnamese convention, so this is realistic input
		assertRejected(field("handicap", true), decimalField(0, 10), "7,5");
	}

	// ══════════════════════════ ENUM ══════════════════════════

	private RegistrationFieldDefinition enumField(String optionsJson) {
		RegistrationFieldDefinition def = definition("shirt_size", "ENUM");
		def.setEnumOptions(optionsJson);
		return def;
	}

	@Test
	@DisplayName("TC-027 · Enum value among the allowed options")
	void TC027_validate_enumMember() {
		assertEquals("M",
				submitSingle(field("shirt_size", true), enumField("[\"S\",\"M\",\"L\"]"), "M").get("shirt_size"));
	}

	@Test
	@DisplayName("TC-028 · Enum value outside the allowed options")
	void TC028_validate_enumNonMember() {
		assertRejected(field("shirt_size", true), enumField("[\"S\",\"M\",\"L\"]"), "XXL");
	}

	@Test
	@DisplayName("TC-029 · Enum field with no options configured")
	void TC029_validate_enumWithoutOptions() {
		// An enum with no options accepts nothing, failing closed
		assertRejected(field("shirt_size", true), enumField(null), "M");
	}

	@Test
	@DisplayName("TC-030 · Enum matching is case sensitive")
	void TC030_validate_enumCaseSensitive() {
		// Unlike BOOLEAN, the comparison here is exact
		assertRejected(field("shirt_size", true), enumField("[\"S\",\"M\",\"L\"]"), "m");
	}

	// ══════════════════════════ DATE ══════════════════════════

	@Test
	@DisplayName("TC-031 · ISO date accepted")
	void TC031_validate_dateIso() {
		assertEquals("1998-05-20",
				submitSingle(field("birth_date", true), definition("birth_date", "DATE"), "1998-05-20")
						.get("birth_date"));
	}

	@Test
	@DisplayName("TC-032 · Non-ISO date format rejected")
	void TC032_validate_dateNonIso() {
		// Day-first is the everyday Vietnamese format, so the client has to convert first
		assertRejected(field("birth_date", true), definition("birth_date", "DATE"), "20/05/1998");
	}

	@Test
	@DisplayName("TC-033 · Calendar-invalid date rejected")
	void TC033_validate_dateImpossible() {
		assertRejected(field("birth_date", true), definition("birth_date", "DATE"), "2026-02-30");
	}

	// ══════════════════════ STRING / PHONE / EMAIL ══════════════════════

	/**
	 * DEF-W1-07 — documents behaviour rather than spec.
	 *
	 * <p>UC-14.1 BR-07 says the data type dictates the validation applied, but PHONE and EMAIL
	 * fall through to the same switch branch as STRING and are returned untouched. Format
	 * checking depends entirely on an Admin filling in the template validation regex.
	 */
	@Test
	@DisplayName("TC-034 · PHONE and EMAIL carry no built-in format check")
	void TC034_validate_phoneAndEmailUnchecked() {
		assertEquals("not-a-phone",
				submitSingle(field("contact_phone", true), definition("contact_phone", "PHONE"), "not-a-phone")
						.get("contact_phone"));
	}

	@Test
	@DisplayName("TC-035 · Unknown data type falls through unchanged")
	void TC035_validate_unknownDataType() {
		// A data type added to the catalog later degrades gracefully
		assertEquals("21.03,105.85",
				submitSingle(field("custom_field", true), definition("custom_field", "GEOLOCATION"), "21.03,105.85")
						.get("custom_field"));
	}

	// ══════════════════════ resolveTournamentForm ══════════════════════

	private static Tournament tournament(Boolean isRegister, Long templateId) {
		return Tournament.builder()
				.id(77L).name("Summer Open 2026")
				.participantType("SINGLES")
				.entryFee(new BigDecimal("200000"))
				.isRegister(isRegister)
				.registrationFormTemplateId(templateId)
				.build();
	}

	@Test
	@DisplayName("TC-036 · Resolving the form for a tournament that collects registrations")
	void TC036_resolveTournamentForm_happyPath() {
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(field("full_name", true), field("age", false)));
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(2L);
		when(fieldDefinitionRepository.findById(any()))
				.thenReturn(Optional.of(definition("full_name", "STRING")));

		RegistrationFormPreviewResponse preview =
				formService.resolveTournamentForm(tournament(true, TEMPLATE_ID));

		assertEquals(77L, preview.getTournamentId());
		assertEquals("Summer Open 2026", preview.getTournamentName());
		assertEquals("SINGLES", preview.getParticipantType());
		assertEquals(new BigDecimal("200000"), preview.getEntryFee());
		assertEquals(2, preview.getFields().size());
		assertTrue(preview.getIsReady());
	}

	@Test
	@DisplayName("TC-037 · Tournament that does not collect registrations")
	void TC037_resolveTournamentForm_registrationDisabled() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.resolveTournamentForm(tournament(false, TEMPLATE_ID)));

		assertEquals(ErrorCode.REG_TEMPLATE_REQUIRED, ex.getErrorCode());
		verifyNoInteractions(templateRepository);
	}

	@Test
	@DisplayName("TC-038 · Registrations enabled but no template assigned")
	void TC038_resolveTournamentForm_noTemplateId() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.resolveTournamentForm(tournament(true, null)));

		assertEquals(ErrorCode.REG_TEMPLATE_REQUIRED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-039 · Assigned template no longer exists")
	void TC039_resolveTournamentForm_templateGone() {
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.resolveTournamentForm(tournament(true, TEMPLATE_ID)));

		assertEquals(ErrorCode.REG_TEMPLATE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════ validateActiveTemplate / validateRegistrationSettings ══════════

	@Test
	@DisplayName("TC-040 · Deactivated template is refused")
	void TC040_validateActiveTemplate_inactive() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.validateActiveTemplate(template(false)));

		assertEquals(ErrorCode.REG_TEMPLATE_INACTIVE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-041 · Active template holding no fields is refused")
	void TC041_validateActiveTemplate_notReady() {
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(0L);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.validateActiveTemplate(template(true)));

		// UC-15.3 AF-04: activating an empty template still leaves it unusable
		assertEquals(ErrorCode.REG_TEMPLATE_INCOMPLETE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-042 · Active template holding fields passes")
	void TC042_validateActiveTemplate_ready() {
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(3L);

		assertDoesNotThrow(() -> formService.validateActiveTemplate(template(true)));
	}

	@Test
	@DisplayName("TC-043 · Registration disabled skips every template check")
	void TC043_validateRegistrationSettings_disabled() {
		assertDoesNotThrow(() -> formService.validateRegistrationSettings(false, null));

		verifyNoInteractions(templateRepository);
		verifyNoInteractions(templateFieldRepository);
	}

	@Test
	@DisplayName("TC-044 · Registration enabled with no template id")
	void TC044_validateRegistrationSettings_missingTemplateId() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.validateRegistrationSettings(true, null));

		assertEquals(ErrorCode.REG_TEMPLATE_REQUIRED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-045 · Loading a template that does not exist")
	void TC045_loadActiveTemplate_notFound() {
		when(templateRepository.findById(9999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.loadActiveTemplate(9999L));

		assertEquals(ErrorCode.REG_TEMPLATE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════ resolveTemplatePreview ══════════════════

	@Test
	@DisplayName("TC-046 · Template override wins over the catalog label")
	void TC046_preview_labelOverride() {
		RegistrationFormTemplateField f = field("full_name", true);
		f.setLabelOverride("Họ và tên thí sinh");
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(f));
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(1L);
		when(fieldDefinitionRepository.findById("full_name"))
				.thenReturn(Optional.of(definition("full_name", "STRING")));

		RegistrationFormPreviewResponse preview = formService.resolveTemplatePreview(template(true));

		assertEquals("Họ và tên thí sinh", preview.getFields().get(0).getLabel());
	}

	@Test
	@DisplayName("TC-047 · Blank override falls back to the catalog label")
	void TC047_preview_blankOverrideFallsBack() {
		RegistrationFormTemplateField f = field("full_name", true);
		f.setLabelOverride("   ");
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(f));
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(1L);
		when(fieldDefinitionRepository.findById("full_name"))
				.thenReturn(Optional.of(definition("full_name", "STRING")));

		RegistrationFormPreviewResponse preview = formService.resolveTemplatePreview(template(true));

		assertEquals("Full name", preview.getFields().get(0).getLabel());
	}

	@Test
	@DisplayName("TC-048 · Field missing from the catalog degrades to its key")
	void TC048_preview_missingDefinitionDegrades() {
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of(field("full_name", true)));
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(1L);
		when(fieldDefinitionRepository.findById("full_name")).thenReturn(Optional.empty());

		RegistrationFormPreviewResponse preview = formService.resolveTemplatePreview(template(true));

		// The preview degrades rather than throwing, so one broken catalog row cannot take
		// down the whole form
		assertEquals("full_name", preview.getFields().get(0).getLabel());
		assertNull(preview.getFields().get(0).getDataType());
		assertNull(preview.getFields().get(0).getEnumOptions());
	}

	@Test
	@DisplayName("TC-049 · Readiness reported from the field count")
	void TC049_preview_readinessFromFieldCount() {
		when(templateFieldRepository.findByTemplateIdOrderBySortOrderAscIdAsc(TEMPLATE_ID))
				.thenReturn(List.of());
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(0L);

		assertFalse(formService.resolveTemplatePreview(template(true)).getIsReady());
	}

	// ══════════════════════════ saveFieldValues ══════════════════════════

	@Test
	@DisplayName("TC-050 · Saving values for a fresh registration")
	void TC050_saveFieldValues_insert() {
		Registration registration = Registration.builder().id(42L).build();
		when(fieldDefinitionRepository.findById(any()))
				.thenReturn(Optional.of(definition("full_name", "STRING")));
		when(fieldValueRepository.findById(any(RegistrationFieldValueId.class))).thenReturn(Optional.empty());

		formService.saveFieldValues(registration, TEMPLATE_ID, Map.of("full_name", "A", "age", "25"));

		verify(fieldValueRepository, times(2)).save(any(RegistrationFieldValue.class));
	}

	@Test
	@DisplayName("TC-051 · Resubmitting updates the existing row")
	void TC051_saveFieldValues_update() {
		Registration registration = Registration.builder().id(42L).build();
		RegistrationFieldValue existing = RegistrationFieldValue.builder()
				.id(new RegistrationFieldValueId(42L, "full_name"))
				.registration(registration)
				.value("A")
				.build();
		when(fieldDefinitionRepository.findById("full_name"))
				.thenReturn(Optional.of(definition("full_name", "STRING")));
		when(fieldValueRepository.findById(any(RegistrationFieldValueId.class)))
				.thenReturn(Optional.of(existing));

		formService.saveFieldValues(registration, TEMPLATE_ID, Map.of("full_name", "B"));

		ArgumentCaptor<RegistrationFieldValue> saved =
				ArgumentCaptor.forClass(RegistrationFieldValue.class);
		verify(fieldValueRepository, times(1)).save(saved.capture());
		// The existing row is reused, keeping a resubmission idempotent under the composite key
		assertEquals("B", saved.getValue().getValue());
		assertEquals(existing, saved.getValue());
	}

	@Test
	@DisplayName("TC-052 · Saving a value whose catalog definition vanished")
	void TC052_saveFieldValues_definitionMissing() {
		Registration registration = Registration.builder().id(42L).build();
		when(fieldDefinitionRepository.findById("full_name")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formService.saveFieldValues(registration, TEMPLATE_ID, Map.of("full_name", "A")));

		assertEquals(ErrorCode.REG_FIELD_NOT_FOUND, ex.getErrorCode());
		verify(fieldValueRepository, never()).save(any());
	}
}
