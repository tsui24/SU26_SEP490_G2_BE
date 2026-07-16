package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;
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
import com.capstone.su26_sep490_g2_be.service.AdminRegistrationFormService;
import com.capstone.su26_sep490_g2_be.service.RegistrationFormService;
import com.capstone.su26_sep490_g2_be.util.JsonParseUtil;
import com.capstone.su26_sep490_g2_be.util.PageableUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRegistrationFormServiceImpl implements AdminRegistrationFormService {

	private final RegistrationFieldDefinitionRepository fieldDefinitionRepository;
	private final RegistrationFormTemplateRepository templateRepository;
	private final RegistrationFormTemplateFieldRepository templateFieldRepository;
	private final UserRepository userRepository;
	private final RegistrationFormService registrationFormService;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<RegistrationFieldCatalogItemResponse> getFieldCatalog(Boolean isActive, int page, int size) {
		Pageable pageable = PageableUtil.create(page, size, "fieldKey");
		Specification<RegistrationFieldDefinition> spec = buildFieldCatalogSpec(isActive);
		return PageResponse.of(fieldDefinitionRepository.findAll(spec, pageable), this::toFieldCatalogItem);
	}

	@Override
	@Transactional(readOnly = true)
	public RegistrationFieldCatalogItemResponse getFieldCatalogItem(String fieldKey) {
		return toFieldCatalogItem(getFieldDefinition(fieldKey));
	}

	@Override
	@Transactional
	public RegistrationFieldCatalogItemResponse createField(CreateRegistrationFieldRequest request) {
		if (fieldDefinitionRepository.existsById(request.getFieldKey())) {
			throw new BusinessException(ErrorCode.REG_FIELD_KEY_EXISTS);
		}
		RegistrationFieldDefinition field = RegistrationFieldDefinition.builder()
				.fieldKey(request.getFieldKey())
				.label(request.getLabel())
				.description(request.getDescription())
				.dataType(request.getDataType())
				.uiComponent(request.getUiComponent())
				.enumOptions(JsonParseUtil.toJson(request.getEnumOptions()))
				.minValue(request.getMinValue())
				.maxValue(request.getMaxValue())
				.isActive(request.getIsActive() == null || request.getIsActive())
				.build();
		return toFieldCatalogItem(fieldDefinitionRepository.save(field));
	}

	@Override
	@Transactional
	public RegistrationFieldCatalogItemResponse updateField(String fieldKey, UpdateRegistrationFieldRequest request) {
		RegistrationFieldDefinition field = getFieldDefinition(fieldKey);
		if (request.getLabel() != null) {
			field.setLabel(request.getLabel());
		}
		if (request.getDescription() != null) {
			field.setDescription(request.getDescription());
		}
		if (request.getDataType() != null) {
			field.setDataType(request.getDataType());
		}
		if (request.getUiComponent() != null) {
			field.setUiComponent(request.getUiComponent());
		}
		if (request.getEnumOptions() != null) {
			field.setEnumOptions(JsonParseUtil.toJson(request.getEnumOptions()));
		}
		if (request.getMinValue() != null) {
			field.setMinValue(request.getMinValue());
		}
		if (request.getMaxValue() != null) {
			field.setMaxValue(request.getMaxValue());
		}
		return toFieldCatalogItem(fieldDefinitionRepository.save(field));
	}

	@Override
	@Transactional
	public RegistrationFieldCatalogItemResponse patchFieldActive(
			String fieldKey, PatchRegistrationFieldActiveRequest request) {
		RegistrationFieldDefinition field = getFieldDefinition(fieldKey);
		field.setIsActive(request.getIsActive());
		return toFieldCatalogItem(fieldDefinitionRepository.save(field));
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<RegistrationFormTemplateListItemResponse> listTemplates(Boolean isActive, int page, int size) {
		Pageable pageable = PageableUtil.create(page, size, "sortOrder");
		Page<RegistrationFormTemplate> templatePage = isActive != null
				? templateRepository.findByIsActive(isActive, pageable)
				: templateRepository.findAll(pageable);
		return PageResponse.of(templatePage, this::toTemplateListItem);
	}

	@Override
	@Transactional(readOnly = true)
	public RegistrationFormTemplateDetailResponse getTemplate(Long id) {
		return toTemplateDetail(getTemplateEntity(id));
	}

	@Override
	@Transactional
	public RegistrationFormTemplateCreateResponse createTemplate(
			Long adminUserId, CreateRegistrationFormTemplateRequest request) {
		if (templateRepository.existsByCode(request.getCode())) {
			throw new BusinessException(ErrorCode.REG_TEMPLATE_CODE_EXISTS);
		}
		User creator = userRepository.findById(adminUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		RegistrationFormTemplate template = RegistrationFormTemplate.builder()
				.code(request.getCode())
				.name(request.getName())
				.description(request.getDescription())
				.sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
				.isActive(request.getIsActive() == null || request.getIsActive())
				.createdBy(creator)
				.build();
		template = templateRepository.save(template);

		return RegistrationFormTemplateCreateResponse.builder()
				.id(template.getId())
				.code(template.getCode())
				.name(template.getName())
				.nextStep("fields")
				.build();
	}

	@Override
	@Transactional
	public RegistrationFormTemplateDetailResponse updateTemplate(Long id, UpdateRegistrationFormTemplateRequest request) {
		RegistrationFormTemplate template = getTemplateEntity(id);
		if (request.getName() != null) {
			template.setName(request.getName());
		}
		if (request.getDescription() != null) {
			template.setDescription(request.getDescription());
		}
		if (request.getSortOrder() != null) {
			template.setSortOrder(request.getSortOrder());
		}
		return toTemplateDetail(templateRepository.save(template));
	}

	@Override
	@Transactional
	public RegistrationFormTemplateActivePatchResponse patchTemplateActive(
			Long id, PatchRegistrationFormTemplateActiveRequest request) {
		RegistrationFormTemplate template = getTemplateEntity(id);
		template.setIsActive(request.getIsActive());
		templateRepository.save(template);
		return RegistrationFormTemplateActivePatchResponse.builder()
				.id(template.getId())
				.isActive(template.getIsActive())
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public RegistrationFormTemplateFieldsFormResponse getTemplateFieldsForm(Long id) {
		RegistrationFormTemplate template = getTemplateEntity(id);
		List<RegistrationFormTemplateField> savedFields = templateFieldRepository
				.findByTemplateIdOrderBySortOrderAscIdAsc(id);

		List<RegistrationFormTemplateFieldsFormResponse.TemplateFieldFormItem> fields = savedFields.stream()
				.map(this::toTemplateFieldFormItem)
				.toList();

		Set<String> assignedKeys = savedFields.stream()
				.map(RegistrationFormTemplateField::getFieldKey)
				.collect(Collectors.toSet());

		List<RegistrationFormTemplateFieldsFormResponse.AvailableFieldItem> availableFields =
				fieldDefinitionRepository.findByIsActiveTrueOrderByFieldKeyAsc().stream()
						.filter(field -> !assignedKeys.contains(field.getFieldKey()))
						.map(this::toAvailableField)
						.toList();

		return RegistrationFormTemplateFieldsFormResponse.builder()
				.templateId(template.getId())
				.templateCode(template.getCode())
				.templateName(template.getName())
				.isReady(isTemplateReady(id))
				.fields(fields)
				.availableFields(availableFields)
				.build();
	}

	@Override
	@Transactional
	public RegistrationFormTemplateFieldsSaveResponse saveTemplateFields(
			Long id, UpsertRegistrationFormTemplateFieldsRequest request) {
		getTemplateEntity(id);
		int saved = 0;
		int sortOrder = 0;
		for (UpsertRegistrationFormTemplateFieldsRequest.TemplateFieldItemRequest item : request.getFields()) {
			fieldDefinitionRepository.findById(item.getFieldKey())
					.orElseThrow(() -> new BusinessException(ErrorCode.REG_FIELD_NOT_FOUND));

			RegistrationFormTemplateField field = templateFieldRepository
					.findByTemplateIdAndFieldKey(id, item.getFieldKey())
					.orElse(RegistrationFormTemplateField.builder()
							.templateId(id)
							.fieldKey(item.getFieldKey())
							.build());

			field.setLabelOverride(item.getLabelOverride());
			field.setDescriptionOverride(item.getDescriptionOverride());
			field.setPlaceholder(item.getPlaceholder());
			field.setValidationRegex(item.getValidationRegex());
			field.setDefaultValue(item.getDefaultValue());
			field.setIsRequired(item.getIsRequired() != null ? item.getIsRequired() : true);
			field.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : sortOrder);
			templateFieldRepository.save(field);
			saved++;
			sortOrder++;
		}

		boolean ready = isTemplateReady(id);
		return RegistrationFormTemplateFieldsSaveResponse.builder()
				.templateId(id)
				.fieldsSaved(saved)
				.isReady(ready)
				.nextStep(ready ? "preview" : "fields")
				.build();
	}

	@Override
	@Transactional
	public void deleteTemplateField(Long id, String fieldKey) {
		getTemplateEntity(id);
		if (!templateFieldRepository.findByTemplateIdAndFieldKey(id, fieldKey).isPresent()) {
			throw new BusinessException(ErrorCode.REG_FIELD_NOT_FOUND);
		}
		templateFieldRepository.deleteByTemplateIdAndFieldKey(id, fieldKey);
	}

	@Override
	@Transactional(readOnly = true)
	public RegistrationFormPreviewResponse getTemplatePreview(Long id) {
		return registrationFormService.resolveTemplatePreview(getTemplateEntity(id));
	}

	@Override
	@Transactional(readOnly = true)
	public RegistrationFormPreviewResponse getActiveTemplatePreview(Long id) {
		RegistrationFormTemplate template = getTemplateEntity(id);
		if (!Boolean.TRUE.equals(template.getIsActive())) {
			throw new BusinessException(ErrorCode.REG_TEMPLATE_NOT_FOUND);
		}
		return registrationFormService.resolveTemplatePreview(template);
	}

	@Override
	@Transactional(readOnly = true)
	public OwnerRegistrationFormTemplateListResponse listActiveTemplatesForOwner() {
		List<OwnerRegistrationFormTemplateListResponse.OwnerRegistrationFormTemplateItemResponse> items =
				templateRepository.findByIsActiveTrueOrderBySortOrderAscCreatedAtAsc().stream()
						.filter(template -> isTemplateReady(template.getId()))
						.map(template -> OwnerRegistrationFormTemplateListResponse
								.OwnerRegistrationFormTemplateItemResponse.builder()
								.id(template.getId())
								.code(template.getCode())
								.name(template.getName())
								.description(template.getDescription())
								.sortOrder(template.getSortOrder())
								.fieldCount(templateFieldRepository.countByTemplateId(template.getId()))
								.build())
						.toList();
		return OwnerRegistrationFormTemplateListResponse.builder()
				.items(items)
				.total(items.size())
				.build();
	}

	private Specification<RegistrationFieldDefinition> buildFieldCatalogSpec(Boolean isActive) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (isActive != null) {
				predicates.add(cb.equal(root.get("isActive"), isActive));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	private RegistrationFieldDefinition getFieldDefinition(String fieldKey) {
		return fieldDefinitionRepository.findById(fieldKey)
				.orElseThrow(() -> new BusinessException(ErrorCode.REG_FIELD_NOT_FOUND));
	}

	private RegistrationFormTemplate getTemplateEntity(Long id) {
		return templateRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.REG_TEMPLATE_NOT_FOUND));
	}

	private boolean isTemplateReady(Long templateId) {
		return templateFieldRepository.countByTemplateId(templateId) > 0;
	}

	private RegistrationFieldCatalogItemResponse toFieldCatalogItem(RegistrationFieldDefinition field) {
		return RegistrationFieldCatalogItemResponse.builder()
				.fieldKey(field.getFieldKey())
				.label(field.getLabel())
				.description(field.getDescription())
				.dataType(field.getDataType())
				.uiComponent(field.getUiComponent())
				.enumOptions(JsonParseUtil.parseStringList(field.getEnumOptions()))
				.minValue(field.getMinValue())
				.maxValue(field.getMaxValue())
				.isActive(field.getIsActive())
				.build();
	}

	private RegistrationFormTemplateListItemResponse toTemplateListItem(RegistrationFormTemplate template) {
		long fieldCount = templateFieldRepository.countByTemplateId(template.getId());
		return RegistrationFormTemplateListItemResponse.builder()
				.id(template.getId())
				.code(template.getCode())
				.name(template.getName())
				.description(template.getDescription())
				.isActive(template.getIsActive())
				.sortOrder(template.getSortOrder())
				.fieldCount(fieldCount)
				.isReady(fieldCount > 0)
				.createdAt(template.getCreatedAt())
				.build();
	}

	private RegistrationFormTemplateDetailResponse toTemplateDetail(RegistrationFormTemplate template) {
		long fieldCount = templateFieldRepository.countByTemplateId(template.getId());
		return RegistrationFormTemplateDetailResponse.builder()
				.id(template.getId())
				.code(template.getCode())
				.name(template.getName())
				.description(template.getDescription())
				.isActive(template.getIsActive())
				.sortOrder(template.getSortOrder())
				.fieldCount(fieldCount)
				.isReady(fieldCount > 0)
				.createdById(template.getCreatedBy() != null ? template.getCreatedBy().getId() : null)
				.createdAt(template.getCreatedAt())
				.updatedAt(template.getUpdatedAt())
				.build();
	}

	private RegistrationFormTemplateFieldsFormResponse.TemplateFieldFormItem toTemplateFieldFormItem(
			RegistrationFormTemplateField templateField) {
		RegistrationFieldDefinition definition = fieldDefinitionRepository
				.findById(templateField.getFieldKey()).orElse(null);
		return RegistrationFormTemplateFieldsFormResponse.TemplateFieldFormItem.builder()
				.id(templateField.getId())
				.fieldKey(templateField.getFieldKey())
				.label(definition != null ? definition.getLabel() : templateField.getFieldKey())
				.description(definition != null ? definition.getDescription() : null)
				.dataType(definition != null ? definition.getDataType() : null)
				.uiComponent(definition != null ? definition.getUiComponent() : null)
				.enumOptions(definition != null ? JsonParseUtil.parseStringList(definition.getEnumOptions()) : null)
				.minValue(definition != null ? definition.getMinValue() : null)
				.maxValue(definition != null ? definition.getMaxValue() : null)
				.labelOverride(templateField.getLabelOverride())
				.descriptionOverride(templateField.getDescriptionOverride())
				.placeholder(templateField.getPlaceholder())
				.validationRegex(templateField.getValidationRegex())
				.defaultValue(templateField.getDefaultValue())
				.isRequired(templateField.getIsRequired())
				.sortOrder(templateField.getSortOrder())
				.build();
	}

	private RegistrationFormTemplateFieldsFormResponse.AvailableFieldItem toAvailableField(
			RegistrationFieldDefinition field) {
		return RegistrationFormTemplateFieldsFormResponse.AvailableFieldItem.builder()
				.fieldKey(field.getFieldKey())
				.label(field.getLabel())
				.dataType(field.getDataType())
				.uiComponent(field.getUiComponent())
				.minValue(field.getMinValue())
				.maxValue(field.getMaxValue())
				.build();
	}
}
