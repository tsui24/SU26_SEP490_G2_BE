package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.CreateRegistrationFieldRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateRegistrationFormTemplateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchRegistrationFieldActiveRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchRegistrationFormTemplateActiveRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateRegistrationFieldRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateRegistrationFormTemplateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpsertRegistrationFormTemplateFieldsRequest;
import com.capstone.su26_sep490_g2_be.dto.response.OwnerRegistrationFormTemplateListResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationFieldCatalogItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationFormTemplateCreateResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationFormTemplateFieldsSaveResponse;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFormTemplate;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFormTemplateField;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFormTemplateFieldRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFormTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.RegistrationFormService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link AdminRegistrationFormServiceImpl}.
 *
 * <p>Mirrors the <b>AdminRegistrationFormService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-14 (registration field catalog) and UC-15 (form templates).
 *
 * <p>Worth noting while reading: this class honours UC-14.2 BR-04, which distinguishes an
 * explicitly empty enum option list (clear the stored options) from an omitted field (keep
 * them). {@link ConfigFieldDefinitionServiceImpl} does not make that distinction — DEF-W1-05.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · AdminRegistrationFormService — UC-14, UC-15")
class AdminRegistrationFormServiceImplTest {

	@Mock RegistrationFieldDefinitionRepository fieldDefinitionRepository;
	@Mock RegistrationFormTemplateRepository templateRepository;
	@Mock RegistrationFormTemplateFieldRepository templateFieldRepository;
	@Mock UserRepository userRepository;
	@Mock RegistrationFormService registrationFormService;

	@InjectMocks AdminRegistrationFormServiceImpl service;

	private static final String FIELD_KEY = "full_name";
	private static final Long TEMPLATE_ID = 5L;
	private static final Long ADMIN_ID = 1L;

	private static RegistrationFieldDefinition field(String key, boolean active) {
		return RegistrationFieldDefinition.builder()
				.fieldKey(key).label("Full name").dataType("STRING")
				.uiComponent("TEXT_INPUT").isActive(active)
				.build();
	}

	private static RegistrationFormTemplate template(boolean active) {
		return RegistrationFormTemplate.builder()
				.id(TEMPLATE_ID).code("BASIC_PLAYER").name("Basic player registration")
				.sortOrder(0).isActive(active)
				.build();
	}

	private static CreateRegistrationFieldRequest createFieldRequest(Boolean isActive, List<String> options) {
		CreateRegistrationFieldRequest r = new CreateRegistrationFieldRequest();
		r.setFieldKey(FIELD_KEY);
		r.setLabel("Full name");
		r.setDataType("STRING");
		r.setUiComponent("TEXT_INPUT");
		r.setIsActive(isActive);
		r.setEnumOptions(options);
		return r;
	}

	private static CreateRegistrationFormTemplateRequest createTemplateRequest(
			Integer sortOrder, Boolean isActive) {
		CreateRegistrationFormTemplateRequest r = new CreateRegistrationFormTemplateRequest();
		r.setCode("BASIC_PLAYER");
		r.setName("Basic player registration");
		r.setSortOrder(sortOrder);
		r.setIsActive(isActive);
		return r;
	}

	private static UpsertRegistrationFormTemplateFieldsRequest.TemplateFieldItemRequest fieldItem(
			String key, Boolean required, Integer sortOrder) {
		UpsertRegistrationFormTemplateFieldsRequest.TemplateFieldItemRequest i =
				new UpsertRegistrationFormTemplateFieldsRequest.TemplateFieldItemRequest();
		i.setFieldKey(key);
		i.setIsRequired(required);
		i.setSortOrder(sortOrder);
		return i;
	}

	private static UpsertRegistrationFormTemplateFieldsRequest fieldsRequest(
			UpsertRegistrationFormTemplateFieldsRequest.TemplateFieldItemRequest... items) {
		UpsertRegistrationFormTemplateFieldsRequest r = new UpsertRegistrationFormTemplateFieldsRequest();
		r.setFields(List.of(items));
		return r;
	}

	// ══════════════════════════ createField — UC-14.1 ══════════════════════════

	@Test
	@DisplayName("TC-001 · Creating a catalog field with a fresh key")
	void TC001_createField_happyPath() {
		when(fieldDefinitionRepository.existsById(FIELD_KEY)).thenReturn(false);
		when(fieldDefinitionRepository.save(any(RegistrationFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		RegistrationFieldCatalogItemResponse response = service.createField(createFieldRequest(true, null));

		assertEquals(FIELD_KEY, response.getFieldKey());
		verify(fieldDefinitionRepository, times(1)).save(any(RegistrationFieldDefinition.class));
	}

	@Test
	@DisplayName("TC-002 · Duplicate field key is rejected")
	void TC002_createField_duplicateKey() {
		when(fieldDefinitionRepository.existsById(FIELD_KEY)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createField(createFieldRequest(true, null)));

		assertEquals(ErrorCode.REG_FIELD_KEY_EXISTS, ex.getErrorCode());
		verify(fieldDefinitionRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-003 · Omitted active flag defaults to active")
	void TC003_createField_defaultsToActive() {
		when(fieldDefinitionRepository.existsById(FIELD_KEY)).thenReturn(false);
		when(fieldDefinitionRepository.save(any(RegistrationFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		service.createField(createFieldRequest(null, null));

		ArgumentCaptor<RegistrationFieldDefinition> saved =
				ArgumentCaptor.forClass(RegistrationFieldDefinition.class);
		verify(fieldDefinitionRepository).save(saved.capture());
		// UC-14.1 BR-06: an omitted active status defaults to Yes
		assertTrue(saved.getValue().getIsActive());
	}

	@Test
	@DisplayName("TC-004 · An explicit inactive flag is honoured")
	void TC004_createField_explicitInactive() {
		when(fieldDefinitionRepository.existsById(FIELD_KEY)).thenReturn(false);
		when(fieldDefinitionRepository.save(any(RegistrationFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		service.createField(createFieldRequest(false, null));

		ArgumentCaptor<RegistrationFieldDefinition> saved =
				ArgumentCaptor.forClass(RegistrationFieldDefinition.class);
		verify(fieldDefinitionRepository).save(saved.capture());
		// The default applies only when the caller says nothing — false must not be read as absent
		assertFalse(saved.getValue().getIsActive());
	}

	// ══════════════════════════ updateField — UC-14.2 ══════════════════════════

	@Test
	@DisplayName("TC-005 · Updating only the label leaves the rest alone")
	void TC005_updateField_partialUpdate() {
		RegistrationFieldDefinition existing = field(FIELD_KEY, true);
		when(fieldDefinitionRepository.findById(FIELD_KEY)).thenReturn(Optional.of(existing));
		when(fieldDefinitionRepository.save(any(RegistrationFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		UpdateRegistrationFieldRequest request = new UpdateRegistrationFieldRequest();
		request.setLabel("Họ và tên");

		service.updateField(FIELD_KEY, request);

		// UC-14.2 BR-03: updates are strictly partial
		assertEquals("Họ và tên", existing.getLabel());
		assertEquals("STRING", existing.getDataType());
		assertEquals("TEXT_INPUT", existing.getUiComponent());
	}

	@Test
	@DisplayName("TC-006 · The active status cannot be changed through the update form")
	void TC006_updateField_cannotToggleActive() {
		RegistrationFieldDefinition existing = field(FIELD_KEY, true);
		when(fieldDefinitionRepository.findById(FIELD_KEY)).thenReturn(Optional.of(existing));
		when(fieldDefinitionRepository.save(any(RegistrationFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		UpdateRegistrationFieldRequest request = new UpdateRegistrationFieldRequest();
		request.setLabel("Renamed");

		service.updateField(FIELD_KEY, request);

		// UC-14.2 BR-02 reserves the active flag for the dedicated activate/deactivate action,
		// and the request DTO has no such field at all
		assertTrue(existing.getIsActive());
	}

	@Test
	@DisplayName("TC-007 · An empty enum option list clears the stored options")
	void TC007_updateField_emptyEnumOptionsClears() {
		RegistrationFieldDefinition existing = field("shirt_size", true);
		existing.setEnumOptions("[\"S\",\"M\",\"L\"]");
		when(fieldDefinitionRepository.findById("shirt_size")).thenReturn(Optional.of(existing));
		when(fieldDefinitionRepository.save(any(RegistrationFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		UpdateRegistrationFieldRequest request = new UpdateRegistrationFieldRequest();
		request.setEnumOptions(List.of());

		service.updateField("shirt_size", request);

		// UC-14.2 BR-04: an explicitly empty list clears the options
		assertEquals("[]", existing.getEnumOptions());
	}

	@Test
	@DisplayName("TC-008 · Omitting enum options keeps the stored ones")
	void TC008_updateField_omittedEnumOptionsKept() {
		RegistrationFieldDefinition existing = field("shirt_size", true);
		existing.setEnumOptions("[\"S\",\"M\",\"L\"]");
		when(fieldDefinitionRepository.findById("shirt_size")).thenReturn(Optional.of(existing));
		when(fieldDefinitionRepository.save(any(RegistrationFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		UpdateRegistrationFieldRequest request = new UpdateRegistrationFieldRequest();
		request.setLabel("Renamed");

		service.updateField("shirt_size", request);

		// The other half of UC-14.2 BR-04 — and the distinction
		// ConfigFieldDefinitionServiceImpl fails to make (DEF-W1-05)
		assertEquals("[\"S\",\"M\",\"L\"]", existing.getEnumOptions());
	}

	@Test
	@DisplayName("TC-009 · Updating a field that does not exist")
	void TC009_updateField_notFound() {
		when(fieldDefinitionRepository.findById("no_such")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateField("no_such", new UpdateRegistrationFieldRequest()));

		assertEquals(ErrorCode.REG_FIELD_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ patchFieldActive — UC-14.3 ══════════════════════════

	@Test
	@DisplayName("TC-010 · Deactivating a catalog field keeps the record")
	void TC010_patchFieldActive_deactivate() {
		RegistrationFieldDefinition existing = field(FIELD_KEY, true);
		when(fieldDefinitionRepository.findById(FIELD_KEY)).thenReturn(Optional.of(existing));
		when(fieldDefinitionRepository.save(any(RegistrationFieldDefinition.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		PatchRegistrationFieldActiveRequest request = new PatchRegistrationFieldActiveRequest();
		request.setIsActive(false);

		service.patchFieldActive(FIELD_KEY, request);

		// UC-14.3 BR-05: there is no hard delete, removal is deactivation
		assertFalse(existing.getIsActive());
		verify(fieldDefinitionRepository, never()).delete(any(RegistrationFieldDefinition.class));
		verify(fieldDefinitionRepository, never()).deleteById(anyString());
	}

	// ══════════════════════════ createTemplate — UC-15.1 ══════════════════════════

	@Test
	@DisplayName("TC-011 · Creating a template points the Admin at the fields step")
	void TC011_createTemplate_happyPath() {
		when(templateRepository.existsByCode("BASIC_PLAYER")).thenReturn(false);
		when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(User.builder().id(ADMIN_ID).build()));
		when(templateRepository.save(any(RegistrationFormTemplate.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		RegistrationFormTemplateCreateResponse response =
				service.createTemplate(ADMIN_ID, createTemplateRequest(3, true));

		// UC-15.1 BR-06 requires the UI to be steered straight to field configuration
		assertEquals("fields", response.getNextStep());
		assertEquals("BASIC_PLAYER", response.getCode());
	}

	@Test
	@DisplayName("TC-012 · Duplicate template code is rejected")
	void TC012_createTemplate_duplicateCode() {
		when(templateRepository.existsByCode("BASIC_PLAYER")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTemplate(ADMIN_ID, createTemplateRequest(0, true)));

		assertEquals(ErrorCode.REG_TEMPLATE_CODE_EXISTS, ex.getErrorCode());
		verify(templateRepository, never()).save(any());
		verify(userRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("TC-013 · Creating a template as an unknown admin")
	void TC013_createTemplate_unknownAdmin() {
		when(templateRepository.existsByCode("BASIC_PLAYER")).thenReturn(false);
		when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTemplate(ADMIN_ID, createTemplateRequest(0, true)));

		// UC-15.1 AF-03: the session has to resolve to a real account
		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
		verify(templateRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-014 · Omitted sort order and active flag take their defaults")
	void TC014_createTemplate_defaults() {
		when(templateRepository.existsByCode("BASIC_PLAYER")).thenReturn(false);
		when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(User.builder().id(ADMIN_ID).build()));
		when(templateRepository.save(any(RegistrationFormTemplate.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		service.createTemplate(ADMIN_ID, createTemplateRequest(null, null));

		ArgumentCaptor<RegistrationFormTemplate> saved =
				ArgumentCaptor.forClass(RegistrationFormTemplate.class);
		verify(templateRepository).save(saved.capture());
		// UC-15.1 BR-04: active defaults to Yes and sort order to 0
		assertEquals(0, saved.getValue().getSortOrder());
		assertTrue(saved.getValue().getIsActive());
	}

	// ══════════════════════════ updateTemplate — UC-15.2 ══════════════════════════

	@Test
	@DisplayName("TC-015 · The template code cannot be changed")
	void TC015_updateTemplate_codeImmutable() {
		RegistrationFormTemplate existing = template(true);
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));
		when(templateRepository.save(any(RegistrationFormTemplate.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		lenient().when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(2L);

		UpdateRegistrationFormTemplateRequest request = new UpdateRegistrationFormTemplateRequest();
		request.setName("Renamed");

		service.updateTemplate(TEMPLATE_ID, request);

		// UC-15.2 BR-01: the code is a fixed identifier, and the request DTO has no such field
		assertEquals("BASIC_PLAYER", existing.getCode());
		assertEquals("Renamed", existing.getName());
	}

	@Test
	@DisplayName("TC-016 · An empty update changes nothing")
	void TC016_updateTemplate_emptyPayload() {
		RegistrationFormTemplate existing = template(true);
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));
		when(templateRepository.save(any(RegistrationFormTemplate.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		lenient().when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(2L);

		service.updateTemplate(TEMPLATE_ID, new UpdateRegistrationFormTemplateRequest());

		// UC-15.2 AF-03: an empty submission succeeds and leaves the data identical
		assertEquals("Basic player registration", existing.getName());
		assertEquals(0, existing.getSortOrder());
	}

	@Test
	@DisplayName("TC-017 · Updating a template that does not exist")
	void TC017_updateTemplate_notFound() {
		when(templateRepository.findById(9999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateTemplate(9999L, new UpdateRegistrationFormTemplateRequest()));

		assertEquals(ErrorCode.REG_TEMPLATE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ patchTemplateActive — UC-15.3 ══════════════════════════

	@Test
	@DisplayName("TC-018 · Deactivating a template keeps the record")
	void TC018_patchTemplateActive_deactivate() {
		RegistrationFormTemplate existing = template(true);
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));

		PatchRegistrationFormTemplateActiveRequest request =
				new PatchRegistrationFormTemplateActiveRequest();
		request.setIsActive(false);

		assertFalse(service.patchTemplateActive(TEMPLATE_ID, request).getIsActive());
		// UC-15.3 BR-06: no hard delete exists for templates
		verify(templateRepository, never()).delete(any(RegistrationFormTemplate.class));
	}

	// ══════════════════════════ saveTemplateFields — UC-15 ══════════════════════════

	@Test
	@DisplayName("TC-019 · Assigning fields makes the template ready")
	void TC019_saveTemplateFields_becomesReady() {
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(fieldDefinitionRepository.findById(FIELD_KEY)).thenReturn(Optional.of(field(FIELD_KEY, true)));
		when(templateFieldRepository.findByTemplateIdAndFieldKey(TEMPLATE_ID, FIELD_KEY))
				.thenReturn(Optional.empty());
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(1L);

		RegistrationFormTemplateFieldsSaveResponse response =
				service.saveTemplateFields(TEMPLATE_ID, fieldsRequest(fieldItem(FIELD_KEY, true, 0)));

		// UC-15 BR-03: readiness is the presence of at least one field
		assertEquals(1, response.getFieldsSaved());
		assertTrue(response.getIsReady());
		assertEquals("preview", response.getNextStep());
	}

	@Test
	@DisplayName("TC-020 · Assigning a field that is not in the catalog")
	void TC020_saveTemplateFields_unknownField() {
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(fieldDefinitionRepository.findById("no_such")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.saveTemplateFields(TEMPLATE_ID, fieldsRequest(fieldItem("no_such", true, 0))));

		// UC-15 BR-04: an assigned field has to exist in the catalog
		assertEquals(ErrorCode.REG_FIELD_NOT_FOUND, ex.getErrorCode());
		verify(templateFieldRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-021 · Omitted required flag defaults to required")
	void TC021_saveTemplateFields_requiredDefault() {
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(fieldDefinitionRepository.findById(FIELD_KEY)).thenReturn(Optional.of(field(FIELD_KEY, true)));
		when(templateFieldRepository.findByTemplateIdAndFieldKey(TEMPLATE_ID, FIELD_KEY))
				.thenReturn(Optional.empty());
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(1L);

		service.saveTemplateFields(TEMPLATE_ID, fieldsRequest(fieldItem(FIELD_KEY, null, 0)));

		ArgumentCaptor<RegistrationFormTemplateField> saved =
				ArgumentCaptor.forClass(RegistrationFormTemplateField.class);
		verify(templateFieldRepository).save(saved.capture());
		assertTrue(saved.getValue().getIsRequired());
	}

	@Test
	@DisplayName("TC-022 · Omitted sort order falls back to the submitted order")
	void TC022_saveTemplateFields_sortOrderFallsBackToPosition() {
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(fieldDefinitionRepository.findById(anyString()))
				.thenReturn(Optional.of(field(FIELD_KEY, true)));
		when(templateFieldRepository.findByTemplateIdAndFieldKey(anyLong(), anyString()))
				.thenReturn(Optional.empty());
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(2L);

		service.saveTemplateFields(TEMPLATE_ID,
				fieldsRequest(fieldItem("full_name", true, null), fieldItem("phone", true, null)));

		ArgumentCaptor<RegistrationFormTemplateField> saved =
				ArgumentCaptor.forClass(RegistrationFormTemplateField.class);
		verify(templateFieldRepository, times(2)).save(saved.capture());
		// Without the fallback every field would share one sort order and the form would render
		// in an arbitrary sequence
		assertEquals(0, saved.getAllValues().get(0).getSortOrder());
		assertEquals(1, saved.getAllValues().get(1).getSortOrder());
	}

	@Test
	@DisplayName("TC-023 · Reassigning an existing field updates it in place")
	void TC023_saveTemplateFields_upsert() {
		RegistrationFormTemplateField existing = RegistrationFormTemplateField.builder()
				.id(9L).templateId(TEMPLATE_ID).fieldKey(FIELD_KEY)
				.labelOverride("Old label").isRequired(false)
				.build();
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(fieldDefinitionRepository.findById(FIELD_KEY)).thenReturn(Optional.of(field(FIELD_KEY, true)));
		when(templateFieldRepository.findByTemplateIdAndFieldKey(TEMPLATE_ID, FIELD_KEY))
				.thenReturn(Optional.of(existing));
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(1L);

		service.saveTemplateFields(TEMPLATE_ID, fieldsRequest(fieldItem(FIELD_KEY, true, 0)));

		// The same row is reused, so a field cannot end up on one template twice
		verify(templateFieldRepository).save(existing);
		assertTrue(existing.getIsRequired());
		assertNull(existing.getLabelOverride());
	}

	@Test
	@DisplayName("TC-024 · A template left with no fields is not ready")
	void TC024_saveTemplateFields_emptyListNotReady() {
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(0L);

		RegistrationFormTemplateFieldsSaveResponse response =
				service.saveTemplateFields(TEMPLATE_ID, fieldsRequest());

		assertEquals(0, response.getFieldsSaved());
		assertFalse(response.getIsReady());
		// The wizard keeps the Admin on the fields step until at least one is assigned
		assertEquals("fields", response.getNextStep());
	}

	// ══════════════════════════ deleteTemplateField ══════════════════════════

	@Test
	@DisplayName("TC-025 · Removing a field from a template")
	void TC025_deleteTemplateField_happyPath() {
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(templateFieldRepository.findByTemplateIdAndFieldKey(TEMPLATE_ID, FIELD_KEY))
				.thenReturn(Optional.of(RegistrationFormTemplateField.builder().id(9L).build()));

		service.deleteTemplateField(TEMPLATE_ID, FIELD_KEY);

		verify(templateFieldRepository).deleteByTemplateIdAndFieldKey(TEMPLATE_ID, FIELD_KEY);
	}

	@Test
	@DisplayName("TC-026 · Removing a field the template does not hold")
	void TC026_deleteTemplateField_notAssigned() {
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(templateFieldRepository.findByTemplateIdAndFieldKey(TEMPLATE_ID, "no_such"))
				.thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.deleteTemplateField(TEMPLATE_ID, "no_such"));

		assertEquals(ErrorCode.REG_FIELD_NOT_FOUND, ex.getErrorCode());
		verify(templateFieldRepository, never()).deleteByTemplateIdAndFieldKey(anyLong(), anyString());
	}

	// ══════════════ getActiveTemplatePreview / listActiveTemplatesForOwner — UC-15 ══════════════

	@Test
	@DisplayName("TC-027 · An Owner previewing an inactive template is refused")
	void TC027_getActiveTemplatePreview_inactiveHidden() {
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(false)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getActiveTemplatePreview(TEMPLATE_ID));

		// UC-15.3 BR-04: an inactive template answers not-found rather than forbidden, so its
		// existence is not disclosed
		assertEquals(ErrorCode.REG_TEMPLATE_NOT_FOUND, ex.getErrorCode());
		verify(registrationFormService, never()).resolveTemplatePreview(any());
	}

	@Test
	@DisplayName("TC-028 · An Owner previewing an active template succeeds")
	void TC028_getActiveTemplatePreview_active() {
		RegistrationFormTemplate active = template(true);
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(active));

		service.getActiveTemplatePreview(TEMPLATE_ID);

		verify(registrationFormService).resolveTemplatePreview(active);
	}

	@Test
	@DisplayName("TC-029 · The Owner list hides active templates that hold no fields")
	void TC029_listActiveTemplatesForOwner_filtersNotReady() {
		RegistrationFormTemplate ready = template(true);
		RegistrationFormTemplate empty = RegistrationFormTemplate.builder()
				.id(6L).code("EMPTY").name("Empty template").sortOrder(1).isActive(true).build();
		when(templateRepository.findByIsActiveTrueOrderBySortOrderAscCreatedAtAsc())
				.thenReturn(List.of(ready, empty));
		when(templateFieldRepository.countByTemplateId(TEMPLATE_ID)).thenReturn(2L);
		when(templateFieldRepository.countByTemplateId(6L)).thenReturn(0L);

		OwnerRegistrationFormTemplateListResponse response = service.listActiveTemplatesForOwner();

		// UC-15 BR-03 and UC-15.3 AF-04: active is not enough, the template must also be ready
		assertEquals(1, response.getTotal());
		assertEquals(TEMPLATE_ID, response.getItems().get(0).getId());
	}

	@Test
	@DisplayName("TC-030 · No active template leaves the Owner with an empty list")
	void TC030_listActiveTemplatesForOwner_empty() {
		when(templateRepository.findByIsActiveTrueOrderBySortOrderAscCreatedAtAsc()).thenReturn(List.of());

		OwnerRegistrationFormTemplateListResponse response = service.listActiveTemplatesForOwner();

		assertEquals(0, response.getTotal());
		assertTrue(response.getItems().isEmpty());
	}
}
