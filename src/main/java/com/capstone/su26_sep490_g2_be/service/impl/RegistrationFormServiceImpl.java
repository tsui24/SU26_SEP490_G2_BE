package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.SubmitTournamentRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationFormPreviewResponse;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldValueRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFormTemplateFieldRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFormTemplateRepository;
import com.capstone.su26_sep490_g2_be.service.RegistrationFormService;
import com.capstone.su26_sep490_g2_be.util.JsonParseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistrationFormServiceImpl implements RegistrationFormService {

	private final RegistrationFormTemplateRepository templateRepository;
	private final RegistrationFormTemplateFieldRepository templateFieldRepository;
	private final RegistrationFieldDefinitionRepository fieldDefinitionRepository;
	private final RegistrationFieldValueRepository fieldValueRepository;

	@Override
	@Transactional(readOnly = true)
	public RegistrationFormPreviewResponse resolveTemplatePreview(RegistrationFormTemplate template) {
		List<RegistrationFormTemplateField> templateFields = templateFieldRepository
				.findByTemplateIdOrderBySortOrderAscIdAsc(template.getId());
		return RegistrationFormPreviewResponse.builder()
				.templateId(template.getId())
				.templateCode(template.getCode())
				.templateName(template.getName())
				.templateDescription(template.getDescription())
				.isReady(isTemplateReady(template.getId()))
				.fields(templateFields.stream().map(this::toPreviewField).toList())
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public RegistrationFormPreviewResponse resolveTournamentForm(Tournament tournament) {
		if (!tournament.isRegister() || tournament.getRegistrationFormTemplateId() == null) {
			throw new BusinessException(ErrorCode.REG_TEMPLATE_REQUIRED);
		}
		RegistrationFormTemplate template = loadTemplate(tournament.getRegistrationFormTemplateId());
		RegistrationFormPreviewResponse preview = resolveTemplatePreview(template);
		return RegistrationFormPreviewResponse.builder()
				.templateId(preview.getTemplateId())
				.templateCode(preview.getTemplateCode())
				.templateName(preview.getTemplateName())
				.templateDescription(preview.getTemplateDescription())
				.tournamentId(tournament.getId())
				.tournamentName(tournament.getName())
				.participantType(tournament.getParticipantType())
				.isReady(preview.getIsReady())
				.fields(preview.getFields())
				.build();
	}

	@Override
	public void validateActiveTemplate(RegistrationFormTemplate template) {
		if (!Boolean.TRUE.equals(template.getIsActive())) {
			throw new BusinessException(ErrorCode.REG_TEMPLATE_INACTIVE);
		}
		if (!isTemplateReady(template.getId())) {
			throw new BusinessException(ErrorCode.REG_TEMPLATE_INCOMPLETE);
		}
	}

	@Override
	public void validateRegistrationSettings(boolean isRegister, Long registrationFormTemplateId) {
		if (!isRegister) {
			return;
		}
		if (registrationFormTemplateId == null) {
			throw new BusinessException(ErrorCode.REG_TEMPLATE_REQUIRED);
		}
		validateActiveTemplate(loadTemplate(registrationFormTemplateId));
	}

	@Override
	@Transactional(readOnly = true)
	public RegistrationFormTemplate loadActiveTemplate(Long templateId) {
		RegistrationFormTemplate template = loadTemplate(templateId);
		validateActiveTemplate(template);
		return template;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, String> validateAndNormalizeFieldValues(
			Long templateId,
			List<SubmitTournamentRegistrationRequest.FieldValueItem> fieldValues) {
		List<RegistrationFormTemplateField> templateFields = templateFieldRepository
				.findByTemplateIdOrderBySortOrderAscIdAsc(templateId);
		if (templateFields.isEmpty()) {
			throw new BusinessException(ErrorCode.REG_TEMPLATE_INCOMPLETE);
		}

		Map<String, String> submitted = fieldValues.stream()
				.collect(Collectors.toMap(
						SubmitTournamentRegistrationRequest.FieldValueItem::getFieldKey,
						SubmitTournamentRegistrationRequest.FieldValueItem::getValue,
						(existing, replacement) -> replacement));

		Set<String> allowedKeys = templateFields.stream()
				.map(RegistrationFormTemplateField::getFieldKey)
				.collect(Collectors.toSet());

		for (String key : submitted.keySet()) {
			if (!allowedKeys.contains(key)) {
				throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
			}
		}

		Map<String, String> normalized = new LinkedHashMap<>();
		for (RegistrationFormTemplateField templateField : templateFields) {
			String key = templateField.getFieldKey();
			String rawValue = submitted.get(key);
			if (rawValue == null || rawValue.isBlank()) {
				if (Boolean.TRUE.equals(templateField.getIsRequired())) {
					throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
				}
				if (templateField.getDefaultValue() != null && !templateField.getDefaultValue().isBlank()) {
					normalized.put(key, templateField.getDefaultValue().trim());
				}
				continue;
			}
			RegistrationFieldDefinition definition = fieldDefinitionRepository.findById(key)
					.orElseThrow(() -> new BusinessException(ErrorCode.REG_FIELD_NOT_FOUND));
			normalized.put(key, validateValue(definition, templateField, rawValue.trim()));
		}
		return normalized;
	}

	@Override
	@Transactional
	public void saveFieldValues(Registration registration, Long templateId, Map<String, String> normalizedValues) {
		for (Map.Entry<String, String> entry : normalizedValues.entrySet()) {
			RegistrationFieldDefinition definition = fieldDefinitionRepository.findById(entry.getKey())
					.orElseThrow(() -> new BusinessException(ErrorCode.REG_FIELD_NOT_FOUND));
			RegistrationFieldValueId valueId = new RegistrationFieldValueId(registration.getId(), entry.getKey());
			RegistrationFieldValue fieldValue = fieldValueRepository.findById(valueId)
					.orElse(RegistrationFieldValue.builder()
							.id(valueId)
							.registration(registration)
							.fieldDefinition(definition)
							.build());
			fieldValue.setValue(entry.getValue());
			fieldValueRepository.save(fieldValue);
		}
	}

	private RegistrationFormTemplate loadTemplate(Long templateId) {
		return templateRepository.findById(templateId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REG_TEMPLATE_NOT_FOUND));
	}

	private boolean isTemplateReady(Long templateId) {
		return templateFieldRepository.countByTemplateId(templateId) > 0;
	}

	private RegistrationFormPreviewResponse.FormFieldItem toPreviewField(RegistrationFormTemplateField templateField) {
		RegistrationFieldDefinition definition = fieldDefinitionRepository
				.findById(templateField.getFieldKey()).orElse(null);
		return RegistrationFormPreviewResponse.FormFieldItem.builder()
				.fieldKey(templateField.getFieldKey())
				.label(resolveLabel(templateField, definition))
				.description(resolveDescription(templateField, definition))
				.dataType(definition != null ? definition.getDataType() : null)
				.uiComponent(definition != null ? definition.getUiComponent() : null)
				.enumOptions(definition != null ? JsonParseUtil.parseStringList(definition.getEnumOptions()) : null)
				.minValue(definition != null ? definition.getMinValue() : null)
				.maxValue(definition != null ? definition.getMaxValue() : null)
				.placeholder(templateField.getPlaceholder())
				.validationRegex(templateField.getValidationRegex())
				.defaultValue(templateField.getDefaultValue())
				.isRequired(templateField.getIsRequired())
				.sortOrder(templateField.getSortOrder())
				.build();
	}

	private String resolveLabel(RegistrationFormTemplateField templateField, RegistrationFieldDefinition definition) {
		if (templateField.getLabelOverride() != null && !templateField.getLabelOverride().isBlank()) {
			return templateField.getLabelOverride();
		}
		return definition != null ? definition.getLabel() : templateField.getFieldKey();
	}

	private String resolveDescription(RegistrationFormTemplateField templateField, RegistrationFieldDefinition definition) {
		if (templateField.getDescriptionOverride() != null && !templateField.getDescriptionOverride().isBlank()) {
			return templateField.getDescriptionOverride();
		}
		return definition != null ? definition.getDescription() : null;
	}

	private String validateValue(
			RegistrationFieldDefinition definition,
			RegistrationFormTemplateField templateField,
			String value) {
		if (templateField.getValidationRegex() != null && !templateField.getValidationRegex().isBlank()) {
			if (!Pattern.compile(templateField.getValidationRegex()).matcher(value).matches()) {
				throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
			}
		}

		String dataType = definition.getDataType();
		return switch (dataType) {
			case "BOOLEAN" -> validateBoolean(value);
			case "INT" -> validateInteger(value, definition.getMinValue(), definition.getMaxValue());
			case "DECIMAL" -> validateDecimal(value, definition.getMinValue(), definition.getMaxValue());
			case "ENUM" -> validateEnum(value, JsonParseUtil.parseStringList(definition.getEnumOptions()));
			case "DATE" -> validateDate(value);
			case "PHONE", "EMAIL", "STRING" -> value;
			default -> value;
		};
	}

	private String validateBoolean(String value) {
		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
			return value.toLowerCase(Locale.ROOT);
		}
		throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
	}

	private String validateInteger(String value, Integer min, Integer max) {
		int parsed;
		try {
			parsed = Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
		}
		if (min != null && parsed < min) {
			throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
		}
		if (max != null && parsed > max) {
			throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
		}
		return Integer.toString(parsed);
	}

	private String validateDecimal(String value, Integer min, Integer max) {
		BigDecimal parsed;
		try {
			parsed = new BigDecimal(value);
		} catch (NumberFormatException e) {
			throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
		}
		if (min != null && parsed.compareTo(BigDecimal.valueOf(min)) < 0) {
			throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
		}
		if (max != null && parsed.compareTo(BigDecimal.valueOf(max)) > 0) {
			throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
		}
		return parsed.stripTrailingZeros().toPlainString();
	}

	private String validateEnum(String value, List<String> options) {
		if (options == null || options.isEmpty() || !options.contains(value)) {
			throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
		}
		return value;
	}

	private String validateDate(String value) {
		try {
			return LocalDate.parse(value).toString();
		} catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.REG_FORM_VALIDATION_FAILED);
		}
	}
}
