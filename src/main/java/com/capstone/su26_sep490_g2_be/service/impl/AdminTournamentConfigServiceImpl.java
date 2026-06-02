package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.bootstrap.FormatBootstrapTemplates;
import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.FormatConfigField;
import com.capstone.su26_sep490_g2_be.entity.FormatRaceToRule;
import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.*;
import com.capstone.su26_sep490_g2_be.service.AdminTournamentConfigService;
import com.capstone.su26_sep490_g2_be.util.JsonParseUtil;
import com.capstone.su26_sep490_g2_be.util.PageableUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminTournamentConfigServiceImpl implements AdminTournamentConfigService {

	private final ConfigFieldDefinitionRepository configFieldRepository;
	private final TournamentFormatDefinitionRepository formatRepository;
	private final FormatConfigFieldRepository formatConfigFieldRepository;
	private final FormatRaceToRuleRepository formatRaceToRuleRepository;
	private final GameTypeDefinitionRepository gameTypeRepository;

	@Override
	public PageResponse<ConfigFieldCatalogItemResponse> getConfigFieldCatalog(
			String scope, Boolean isActive, int page, int size) {
		Pageable pageable = PageableUtil.create(page, size, "fieldKey");
		Specification<ConfigFieldDefinition> spec = buildCatalogSpecification(scope, isActive);
		Page<ConfigFieldDefinition> result = configFieldRepository.findAll(spec, pageable);
		return PageResponse.of(result, this::toCatalogItem);
	}

	@Override
	public ConfigFieldCatalogItemResponse getConfigFieldCatalogItem(String fieldKey) {
		return toCatalogItem(getFieldDefinition(fieldKey));
	}

	@Override
	public PageResponse<FormatListItemResponse> listFormats(
			Boolean isActive, FormatSetupStatus setupStatus, int page, int size) {
		if (setupStatus != null) {
			List<TournamentFormatDefinition> all = isActive != null
					? formatRepository.findByIsActiveOrderByCreatedAtAsc(isActive)
					: formatRepository.findAllByOrderByCreatedAtAsc();
			List<FormatListItemResponse> filtered = all.stream()
					.map(this::toFormatListItem)
					.filter(item -> item.getSetupStatus() == setupStatus)
					.toList();
			return PageResponse.of(filtered, page, size);
		}

		Pageable pageable = PageableUtil.create(page, size, "createdAt");
		Page<TournamentFormatDefinition> formatPage = isActive != null
				? formatRepository.findByIsActive(isActive, pageable)
				: formatRepository.findAll(pageable);
		return PageResponse.of(formatPage, this::toFormatListItem);
	}

	@Override
	public FormatDetailResponse getFormat(String code) {
		return toFormatDetail(getFormatDefinition(code));
	}

	@Override
	@Transactional
	public FormatCreateResponse createFormat(CreateFormatRequest request) {
		if (formatRepository.existsById(request.getCode())) {
			throw new BusinessException(ErrorCode.FORMAT_CODE_EXISTS);
		}
		if (formatRepository.existsByHandlerKey(request.getHandlerKey())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
		}

		TournamentFormatDefinition format = TournamentFormatDefinition.builder()
				.code(request.getCode())
				.name(request.getName())
				.description(request.getDescription())
				.handlerKey(request.getHandlerKey())
				.schemaVersion(request.getSchemaVersion() != null ? request.getSchemaVersion() : "1.0")
				.isActive(Boolean.TRUE.equals(request.getIsActive()))
				.build();
		formatRepository.save(format);

		return FormatCreateResponse.builder()
				.code(format.getCode())
				.name(format.getName())
				.setupStatus(FormatSetupStatus.INFO_DONE)
				.nextStep("config-fields")
				.build();
	}

	@Override
	@Transactional
	public FormatDetailResponse updateFormat(String code, UpdateFormatRequest request) {
		TournamentFormatDefinition format = getFormatDefinition(code);
		format.setName(request.getName());
		format.setDescription(request.getDescription());
		format.setHandlerKey(request.getHandlerKey());
		if (request.getSchemaVersion() != null) {
			format.setSchemaVersion(request.getSchemaVersion());
		}
		formatRepository.save(format);
		return toFormatDetail(format);
	}

	@Override
	@Transactional
	public FormatActivePatchResponse patchFormatActive(String code, PatchFormatActiveRequest request) {
		TournamentFormatDefinition format = getFormatDefinition(code);
		format.setIsActive(request.getIsActive());
		formatRepository.save(format);
		return FormatActivePatchResponse.builder()
				.code(format.getCode())
				.isActive(format.getIsActive())
				.build();
	}

	@Override
	public FormatSetupStatusResponse getSetupStatus(String code) {
		TournamentFormatDefinition format = getFormatDefinition(code);
		return buildSetupStatusResponse(format);
	}

	@Override
	@Transactional(readOnly = true)
	public FormatConfigFieldsFormResponse getConfigFieldsForm(String code) {
		TournamentFormatDefinition format = getFormatDefinition(code);
		List<FormatConfigField> savedFields = formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(code);

		List<FormatConfigFieldsFormResponse.FormatConfigFieldFormItemResponse> fields = savedFields.stream()
				.map(this::toConfigFieldFormItem)
				.toList();

		List<FormatConfigFieldsFormResponse.AvailableConfigFieldResponse> availableFields = List.of();
		if (fields.isEmpty()) {
			Set<String> assignedKeys = savedFields.stream()
					.map(FormatConfigField::getFieldKey)
					.collect(Collectors.toSet());
			availableFields = configFieldRepository.findByIsActiveTrueOrderByFieldScopeAsc().stream()
					.filter(f -> !assignedKeys.contains(f.getFieldKey()))
					.map(this::toAvailableField)
					.toList();
		}

		long configFieldCount = formatConfigFieldRepository.countByFormatCode(code);
		FormatSetupStatus setupStatus = resolveSetupStatus(format, configFieldCount,
				formatRaceToRuleRepository.countByFormatCode(code));

		return FormatConfigFieldsFormResponse.builder()
				.formatCode(format.getCode())
				.formatName(format.getName())
				.setupStatus(setupStatus)
				.fields(fields)
				.availableFields(availableFields)
				.build();
	}

	@Override
	@Transactional
	public FormatConfigFieldsSaveResponse saveConfigFields(String code, UpsertFormatConfigFieldsRequest request) {
		getFormatDefinition(code);
		int saved = 0;
		for (UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest item : request.getFields()) {
			ConfigFieldDefinition fieldDef = configFieldRepository.findById(item.getFieldKey())
					.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FIELD_KEY));

			FormatConfigField field = formatConfigFieldRepository
					.findByFormatCodeAndFieldKey(code, item.getFieldKey())
					.orElse(FormatConfigField.builder()
							.formatCode(code)
							.fieldKey(item.getFieldKey())
							.build());

			field.setDefaultValue(item.getDefaultValue());
			field.setIsRequired(item.getIsRequired() != null ? item.getIsRequired() : true);
			field.setIsVisibleToOwner(item.getIsVisibleToOwner() != null ? item.getIsVisibleToOwner() : true);
			formatConfigFieldRepository.save(field);
			saved++;
		}

		return FormatConfigFieldsSaveResponse.builder()
				.formatCode(code)
				.fieldsSaved(saved)
				.setupStatus(FormatSetupStatus.CONFIG_FIELDS_DONE)
				.nextStep("race-to-rules")
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public FormatRaceToRulesFormResponse getRaceToRulesForm(String code) {
		getFormatDefinition(code);
		List<FormatRaceToRule> rules = formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(code);
		long configFieldCount = formatConfigFieldRepository.countByFormatCode(code);
		long raceToCount = rules.size();

		return FormatRaceToRulesFormResponse.builder()
				.formatCode(code)
				.setupStatus(resolveSetupStatus(getFormatDefinition(code), configFieldCount, raceToCount))
				.rules(rules.stream().map(this::toRaceToRuleItem).toList())
				.build();
	}

	@Override
	@Transactional
	public FormatRaceToRulesSaveResponse saveRaceToRules(String code, UpsertFormatRaceToRulesRequest request) {
		getFormatDefinition(code);
		int saved = 0;
		for (UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest item : request.getRules()) {
			FormatRaceToRule rule = formatRaceToRuleRepository
					.findByFormatCodeAndRoundKey(code, item.getRoundKey())
					.orElse(FormatRaceToRule.builder()
							.formatCode(code)
							.roundKey(item.getRoundKey())
							.build());

			rule.setLabel(item.getLabel());
			rule.setBracketPhase(item.getBracketPhase());
			rule.setRaceTo(item.getRaceTo());
			formatRaceToRuleRepository.save(rule);
			saved++;
		}

		return FormatRaceToRulesSaveResponse.builder()
				.formatCode(code)
				.rulesSaved(saved)
				.setupStatus(FormatSetupStatus.RACE_TO_DONE)
				.nextStep("review")
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public FormatSetupSummaryResponse getSetupSummary(String code) {
		TournamentFormatDefinition format = getFormatDefinition(code);
		List<FormatConfigField> configFields = formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(code);
		List<FormatRaceToRule> raceToRules = formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(code);

		List<String> validationErrors = validateSetup(configFields, raceToRules);
		boolean canActivate = validationErrors.isEmpty()
				&& !configFields.isEmpty()
				&& !raceToRules.isEmpty()
				&& !Boolean.TRUE.equals(format.getIsActive());

		return FormatSetupSummaryResponse.builder()
				.format(FormatSetupSummaryResponse.FormatSummaryItem.builder()
						.code(format.getCode())
						.name(format.getName())
						.description(format.getDescription())
						.handlerKey(format.getHandlerKey())
						.schemaVersion(format.getSchemaVersion())
						.isActive(format.getIsActive())
						.build())
				.configFields(configFields.stream().map(f -> FormatSetupSummaryResponse.ConfigFieldSummaryItem.builder()
						.fieldKey(f.getFieldKey())
						.label(f.getFieldDefinition() != null ? f.getFieldDefinition().getLabel() : f.getFieldKey())
						.defaultValue(f.getDefaultValue())
						.isRequired(f.getIsRequired())
						.isVisibleToOwner(f.getIsVisibleToOwner())
						.build()).toList())
				.raceToRules(raceToRules.stream().map(r -> FormatSetupSummaryResponse.RaceToRuleSummaryItem.builder()
						.roundKey(r.getRoundKey())
						.label(r.getLabel())
						.bracketPhase(r.getBracketPhase())
						.raceTo(r.getRaceTo())
						.build()).toList())
				.setupStatus(canActivate ? FormatSetupStatus.READY_TO_ACTIVATE : resolveSetupStatus(format,
						configFields.size(), raceToRules.size()))
				.canActivate(canActivate)
				.validationErrors(validationErrors)
				.build();
	}

	@Override
	@Transactional
	public FormatActivateResponse activateFormat(String code) {
		TournamentFormatDefinition format = getFormatDefinition(code);
		long configFieldCount = formatConfigFieldRepository.countByFormatCode(code);
		long raceToCount = formatRaceToRuleRepository.countByFormatCode(code);

		if (configFieldCount == 0 || raceToCount == 0) {
			throw new BusinessException(ErrorCode.SETUP_INCOMPLETE);
		}

		List<FormatConfigField> configFields = formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(code);
		List<FormatRaceToRule> raceToRules = formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(code);
		if (!validateSetup(configFields, raceToRules).isEmpty()) {
			throw new BusinessException(ErrorCode.SETUP_INCOMPLETE);
		}

		format.setIsActive(true);
		formatRepository.save(format);

		return FormatActivateResponse.builder()
				.formatCode(code)
				.isActive(true)
				.setupStatus(FormatSetupStatus.ACTIVE)
				.build();
	}

	@Override
	@Transactional
	public FormatBootstrapResponse bootstrapDefaults(String code, BootstrapDefaultsRequest request) {
		getFormatDefinition(code);
		boolean overwrite = request != null && Boolean.TRUE.equals(request.getOverwrite());

		long existingConfig = formatConfigFieldRepository.countByFormatCode(code);
		long existingRaceTo = formatRaceToRuleRepository.countByFormatCode(code);
		if (!overwrite && (existingConfig > 0 || existingRaceTo > 0)) {
			throw new BusinessException(ErrorCode.ALREADY_BOOTSTRAPPED);
		}

		if (!FormatBootstrapTemplates.isSupported(code)) {
			throw new BusinessException(ErrorCode.INVALID_OPERATION);
		}

		UpsertFormatConfigFieldsRequest configRequest = new UpsertFormatConfigFieldsRequest();
		configRequest.setFields(FormatBootstrapTemplates.configFields(code).orElseThrow());
		FormatConfigFieldsSaveResponse configResult = saveConfigFields(code, configRequest);

		UpsertFormatRaceToRulesRequest raceRequest = new UpsertFormatRaceToRulesRequest();
		raceRequest.setRules(FormatBootstrapTemplates.raceToRules(code).orElseThrow());
		FormatRaceToRulesSaveResponse raceResult = saveRaceToRules(code, raceRequest);

		return FormatBootstrapResponse.builder()
				.formatCode(code)
				.configFieldsInserted(configResult.getFieldsSaved())
				.raceToRulesInserted(raceResult.getRulesSaved())
				.setupStatus(FormatSetupStatus.READY_TO_ACTIVATE)
				.build();
	}

	@Override
	public PageResponse<GameTypeDetailResponse> listGameTypes(int page, int size) {
		Pageable pageable = PageableUtil.create(page, size, "createdAt");
		return PageResponse.of(gameTypeRepository.findAll(pageable), this::toGameTypeDetail);
	}

	@Override
	@Transactional
	public GameTypeDetailResponse updateGameType(String code, UpdateGameTypeRequest request) {
		GameTypeDefinition gameType = gameTypeRepository.findById(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.GAME_TYPE_NOT_FOUND));

		gameType.setName(request.getName());
		gameType.setDescription(request.getDescription());
		gameType.setDefaultRaceTo(request.getDefaultRaceTo());
		if (request.getIsActive() != null) {
			gameType.setIsActive(request.getIsActive());
		}
		gameTypeRepository.save(gameType);
		return toGameTypeDetail(gameType);
	}

	private Specification<ConfigFieldDefinition> buildCatalogSpecification(String scope, Boolean isActive) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (isActive != null) {
				predicates.add(cb.equal(root.get("isActive"), isActive));
			}
			if (scope != null && !scope.isBlank()) {
				List<String> scopes = Arrays.stream(scope.split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.toList();
				if (!scopes.isEmpty()) {
					predicates.add(root.get("fieldScope").in(scopes));
				}
			}
			return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	private FormatSetupStatusResponse buildSetupStatusResponse(TournamentFormatDefinition format) {
		long configFieldCount = formatConfigFieldRepository.countByFormatCode(format.getCode());
		long raceToRuleCount = formatRaceToRuleRepository.countByFormatCode(format.getCode());
		List<String> missingSteps = new ArrayList<>();

		if (configFieldCount == 0) {
			missingSteps.add("config-fields");
		}
		if (raceToRuleCount == 0) {
			missingSteps.add("race-to-rules");
		}

		List<FormatConfigField> configFields = formatConfigFieldRepository
				.findByFormatCodeOrderByIdAsc(format.getCode());
		List<FormatRaceToRule> raceToRules = formatRaceToRuleRepository
				.findByFormatCodeOrderByIdAsc(format.getCode());
		boolean canActivate = validateSetup(configFields, raceToRules).isEmpty()
				&& configFieldCount > 0
				&& raceToRuleCount > 0
				&& !Boolean.TRUE.equals(format.getIsActive());

		if (canActivate) {
			missingSteps.add("activate");
		}

		if (Boolean.TRUE.equals(format.getIsActive())) {
			return FormatSetupStatusResponse.builder()
					.formatCode(format.getCode())
					.setupStatus(FormatSetupStatus.ACTIVE)
					.bootstrapped(configFieldCount > 0 || raceToRuleCount > 0)
					.configFieldCount(configFieldCount)
					.raceToRuleCount(raceToRuleCount)
					.isActive(true)
					.canActivate(false)
					.missingSteps(List.of())
					.build();
		}

		return FormatSetupStatusResponse.builder()
				.formatCode(format.getCode())
				.setupStatus(resolveSetupStatus(format, configFieldCount, raceToRuleCount))
				.bootstrapped(configFieldCount > 0 || raceToRuleCount > 0)
				.configFieldCount(configFieldCount)
				.raceToRuleCount(raceToRuleCount)
				.isActive(format.getIsActive())
				.canActivate(canActivate)
				.missingSteps(missingSteps)
				.build();
	}

	private FormatSetupStatus resolveSetupStatus(TournamentFormatDefinition format,
	                                             long configFieldCount,
	                                             long raceToRuleCount) {
		if (Boolean.TRUE.equals(format.getIsActive()) && configFieldCount > 0 && raceToRuleCount > 0) {
			return FormatSetupStatus.ACTIVE;
		}
		if (configFieldCount > 0 && raceToRuleCount > 0) {
			return FormatSetupStatus.READY_TO_ACTIVATE;
		}
		if (configFieldCount > 0) {
			return FormatSetupStatus.CONFIG_FIELDS_DONE;
		}
		return FormatSetupStatus.INFO_DONE;
	}

	private List<String> validateSetup(List<FormatConfigField> configFields, List<FormatRaceToRule> raceToRules) {
		List<String> errors = new ArrayList<>();
		if (configFields.isEmpty()) {
			errors.add("Missing config fields");
		}
		if (raceToRules.isEmpty()) {
			errors.add("Missing race-to rules");
		}
		for (FormatConfigField field : configFields) {
			if (field.getDefaultValue() == null || field.getDefaultValue().isBlank()) {
				errors.add("Empty default value for field: " + field.getFieldKey());
			}
		}
		for (FormatRaceToRule rule : raceToRules) {
			if (rule.getRaceTo() == null || rule.getRaceTo() <= 0) {
				errors.add("Invalid race-to for round: " + rule.getRoundKey());
			}
		}
		return errors;
	}

	private TournamentFormatDefinition getFormatDefinition(String code) {
		return formatRepository.findById(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.FORMAT_NOT_FOUND));
	}

	private ConfigFieldDefinition getFieldDefinition(String fieldKey) {
		return configFieldRepository.findById(fieldKey)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FIELD_KEY));
	}

	private ConfigFieldCatalogItemResponse toCatalogItem(ConfigFieldDefinition field) {
		return ConfigFieldCatalogItemResponse.builder()
				.fieldKey(field.getFieldKey())
				.label(field.getLabel())
				.description(field.getDescription())
				.dataType(field.getDataType())
				.fieldScope(field.getFieldScope())
				.uiComponent(field.getUiComponent())
				.enumOptions(JsonParseUtil.parseStringList(field.getEnumOptions()))
				.minValue(field.getMinValue())
				.maxValue(field.getMaxValue())
				.isActive(field.getIsActive())
				.build();
	}

	private FormatListItemResponse toFormatListItem(TournamentFormatDefinition format) {
		long configFieldCount = formatConfigFieldRepository.countByFormatCode(format.getCode());
		long raceToRuleCount = formatRaceToRuleRepository.countByFormatCode(format.getCode());
		return FormatListItemResponse.builder()
				.code(format.getCode())
				.name(format.getName())
				.description(format.getDescription())
				.handlerKey(format.getHandlerKey())
				.schemaVersion(format.getSchemaVersion())
				.isActive(format.getIsActive())
				.setupStatus(resolveSetupStatus(format, configFieldCount, raceToRuleCount))
				.configFieldCount(configFieldCount)
				.raceToRuleCount(raceToRuleCount)
				.build();
	}

	private FormatDetailResponse toFormatDetail(TournamentFormatDefinition format) {
		long configFieldCount = formatConfigFieldRepository.countByFormatCode(format.getCode());
		long raceToRuleCount = formatRaceToRuleRepository.countByFormatCode(format.getCode());
		return FormatDetailResponse.builder()
				.code(format.getCode())
				.name(format.getName())
				.description(format.getDescription())
				.handlerKey(format.getHandlerKey())
				.schemaVersion(format.getSchemaVersion())
				.isActive(format.getIsActive())
				.setupStatus(resolveSetupStatus(format, configFieldCount, raceToRuleCount))
				.build();
	}

	private FormatConfigFieldsFormResponse.FormatConfigFieldFormItemResponse toConfigFieldFormItem(FormatConfigField field) {
		ConfigFieldDefinition def = field.getFieldDefinition();
		return FormatConfigFieldsFormResponse.FormatConfigFieldFormItemResponse.builder()
				.fieldKey(field.getFieldKey())
				.label(def != null ? def.getLabel() : field.getFieldKey())
				.description(def != null ? def.getDescription() : null)
				.dataType(def != null ? def.getDataType() : null)
				.fieldScope(def != null ? def.getFieldScope() : null)
				.uiComponent(def != null ? def.getUiComponent() : null)
				.enumOptions(def != null ? JsonParseUtil.parseStringList(def.getEnumOptions()) : null)
				.minValue(def != null ? def.getMinValue() : null)
				.maxValue(def != null ? def.getMaxValue() : null)
				.defaultValue(field.getDefaultValue())
				.isRequired(field.getIsRequired())
				.isVisibleToOwner(field.getIsVisibleToOwner())
				.id(field.getId())
				.build();
	}

	private FormatConfigFieldsFormResponse.AvailableConfigFieldResponse toAvailableField(ConfigFieldDefinition field) {
		return FormatConfigFieldsFormResponse.AvailableConfigFieldResponse.builder()
				.fieldKey(field.getFieldKey())
				.label(field.getLabel())
				.dataType(field.getDataType())
				.fieldScope(field.getFieldScope())
				.uiComponent(field.getUiComponent())
				.minValue(field.getMinValue())
				.maxValue(field.getMaxValue())
				.build();
	}

	private FormatRaceToRulesFormResponse.FormatRaceToRuleItemResponse toRaceToRuleItem(FormatRaceToRule rule) {
		return FormatRaceToRulesFormResponse.FormatRaceToRuleItemResponse.builder()
				.id(rule.getId())
				.roundKey(rule.getRoundKey())
				.label(rule.getLabel())
				.bracketPhase(rule.getBracketPhase())
				.raceTo(rule.getRaceTo())
				.build();
	}

	private GameTypeDetailResponse toGameTypeDetail(GameTypeDefinition gameType) {
		return GameTypeDetailResponse.builder()
				.code(gameType.getCode())
				.name(gameType.getName())
				.description(gameType.getDescription())
				.defaultRaceTo(gameType.getDefaultRaceTo())
				.compatibleTableTypes(JsonParseUtil.parseStringList(gameType.getCompatibleTableTypes()))
				.isActive(gameType.getIsActive())
				.build();
	}
}
