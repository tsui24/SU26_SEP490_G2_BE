package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.FieldSource;
import com.capstone.su26_sep490_g2_be.enums.SeedingMethod;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.exception.ConfigValidationException;
import com.capstone.su26_sep490_g2_be.repository.*;
import com.capstone.su26_sep490_g2_be.service.OwnerTournamentService;
import com.capstone.su26_sep490_g2_be.service.TournamentConfigValueService;
import com.capstone.su26_sep490_g2_be.service.TournamentRaceToRuleService;
import com.capstone.su26_sep490_g2_be.util.JsonParseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerTournamentServiceImpl implements OwnerTournamentService {

	private static final List<String> SEEDING_OPTIONS = List.of("RANDOM", "MANUAL", "ELO");

	private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
			"DRAFT", Set.of("OPEN_FOR_REGISTRATION", "CANCELLED"),
			"OPEN_FOR_REGISTRATION", Set.of("REGISTRATION_CLOSED", "CANCELLED"),
			"REGISTRATION_CLOSED", Set.of("DRAW_DONE", "CANCELLED"),
			"DRAW_DONE", Set.of("IN_PROGRESS", "CANCELLED"),
			"IN_PROGRESS", Set.of("COMPLETED", "CANCELLED")
	);

	private final TournamentRepository tournamentRepository;
	private final TournamentConfigRepository tournamentConfigRepository;
	private final TournamentFormatDefinitionRepository formatRepository;
	private final GameTypeDefinitionRepository gameTypeRepository;
	private final FormatConfigFieldRepository formatConfigFieldRepository;
	private final FormatRaceToRuleRepository formatRaceToRuleRepository;
	private final ConfigFieldDefinitionRepository configFieldRepository;
	private final UserRepository userRepository;
	private final TournamentConfigValueService configValueService;
	private final TournamentRaceToRuleService raceToRuleService;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<TournamentListItemResponse> listTournaments(
			Long userId,
			boolean filterByOwner,
			String status,
			String search,
			int page,
			int size) {
		String statusParam = (status == null || status.isBlank()) ? null : status.trim();
		String searchParam = (search == null || search.isBlank()) ? null : search.trim();
		Long createdById = filterByOwner ? userId : null;

		if (size < 1) {
			size = 10;
		}
		if (page < 0) {
			page = 0;
		}

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<Tournament> tournamentPage = tournamentRepository.searchTournaments(
				createdById, statusParam, searchParam, pageable);

		return PageResponse.of(tournamentPage, this::toListItem);
	}

	@Override
	public OwnerFormatListResponse listFormats() {
		List<OwnerFormatListItemResponse> items = formatRepository.findByIsActiveTrueOrderByCreatedAtAsc().stream()
				.map(this::toOwnerFormatItem)
				.filter(item -> Boolean.TRUE.equals(item.getIsReady()))
				.sorted(Comparator.comparing(OwnerFormatListItemResponse::getSortOrder,
						Comparator.nullsLast(Integer::compareTo)))
				.toList();
		return OwnerFormatListResponse.builder()
				.items(items)
				.total(items.size())
				.build();
	}

	@Override
	public OwnerGameTypeListResponse listGameTypes() {
		List<OwnerGameTypeListItemResponse> items = gameTypeRepository.findByIsActiveTrueOrderByCreatedAtAsc().stream()
				.sorted(Comparator.comparing(GameTypeDefinition::getSortOrder))
				.map(gt -> OwnerGameTypeListItemResponse.builder()
						.code(gt.getCode())
						.name(gt.getName())
						.defaultRaceTo(gt.getDefaultRaceTo())
						.sortOrder(gt.getSortOrder())
						.build())
				.toList();
		return OwnerGameTypeListResponse.builder()
				.items(items)
				.total(items.size())
				.build();
	}

	@Override
	@Transactional
	public CreateTournamentResponse createTournament(Long userId, CreateTournamentRequest request) {
		User creator = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		validateGameType(request.getGameType());
		validateFormatReady(request.getFormat());

		Tournament tournament = Tournament.builder()
				.name(request.getName())
				.description(request.getDescription())
				.gameType(request.getGameType())
				.format(request.getFormat())
				.participantType(request.getParticipantType())
				.status("DRAFT")
				.maxParticipants(request.getMaxParticipants())
				.entryFee(request.getEntryFee() != null ? request.getEntryFee() : BigDecimal.ZERO)
				.prizePool(request.getPrizePool())
				.prizeDescription(request.getPrizeDescription())
				.registrationDeadline(request.getRegistrationDeadline())
				.startAt(request.getStartAt())
				.endAt(request.getEndAt())
				.createdBy(creator)
				.build();
		tournament = tournamentRepository.save(tournament);

		TournamentConfig config = TournamentConfig.builder()
				.tournament(tournament)
				.formatCode(request.getFormat())
				.seedingMethod("RANDOM")
				.build();
		tournamentConfigRepository.save(config);

		return CreateTournamentResponse.builder()
				.id(tournament.getId())
				.name(tournament.getName())
				.gameType(tournament.getGameType())
				.format(tournament.getFormat())
				.participantType(tournament.getParticipantType())
				.status(tournament.getStatus())
				.maxParticipants(tournament.getMaxParticipants())
				.configComplete(false)
				.build();
	}

	@Override
	@Transactional
	public UpdateTournamentResponse updateTournament(Long userId, Long tournamentId,
	                                                 UpdateTournamentRequest request,
	                                                 boolean enforceOwnership) {
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		assertEditableStatus(tournament);

		if (request.getFormat() != null && !request.getFormat().equals(tournament.getFormat())) {
			validateFormatReady(request.getFormat());
			tournament.setFormat(request.getFormat());
			configValueService.deleteByTournament(tournamentId);
			raceToRuleService.deleteByTournament(tournamentId);
			TournamentConfig config = getConfig(tournamentId);
			config.setFormatCode(request.getFormat());
			config.setConfigSnapshotJson(null);
			tournamentConfigRepository.save(config);
		}

		if (request.getName() != null) {
			tournament.setName(request.getName());
		}
		if (request.getDescription() != null) {
			tournament.setDescription(request.getDescription());
		}
		if (request.getMaxParticipants() != null) {
			tournament.setMaxParticipants(request.getMaxParticipants());
		}
		if (request.getEntryFee() != null) {
			tournament.setEntryFee(request.getEntryFee());
		}
		if (request.getPrizePool() != null) {
			tournament.setPrizePool(request.getPrizePool());
		}
		if (request.getPrizeDescription() != null) {
			tournament.setPrizeDescription(request.getPrizeDescription());
		}
		if (request.getRegistrationDeadline() != null) {
			tournament.setRegistrationDeadline(request.getRegistrationDeadline());
		}
		if (request.getStartAt() != null) {
			tournament.setStartAt(request.getStartAt());
		}
		if (request.getEndAt() != null) {
			tournament.setEndAt(request.getEndAt());
		}

		tournamentRepository.save(tournament);
		boolean configComplete = isConfigComplete(tournamentId, tournament.getFormat());

		return UpdateTournamentResponse.builder()
				.id(tournament.getId())
				.status(tournament.getStatus())
				.format(tournament.getFormat())
				.configComplete(configComplete)
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public TournamentDetailResponse getTournament(Long userId, Long tournamentId, boolean enforceOwnership) {
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		TournamentFormatDefinition format = formatRepository.findById(tournament.getFormat()).orElse(null);
		TournamentConfig config = getConfig(tournamentId);
		boolean configComplete = isConfigComplete(tournamentId, tournament.getFormat());

		return TournamentDetailResponse.builder()
				.id(tournament.getId())
				.name(tournament.getName())
				.description(tournament.getDescription())
				.gameType(tournament.getGameType())
				.format(tournament.getFormat())
				.formatName(format != null ? format.getName() : null)
				.participantType(tournament.getParticipantType())
				.status(tournament.getStatus())
				.maxParticipants(tournament.getMaxParticipants())
				.entryFee(tournament.getEntryFee())
				.prizePool(tournament.getPrizePool())
				.prizeDescription(tournament.getPrizeDescription())
				.registrationDeadline(tournament.getRegistrationDeadline())
				.startAt(tournament.getStartAt())
				.endAt(tournament.getEndAt())
				.configComplete(configComplete)
				.configSummary(buildConfigSummary(tournamentId, tournament.getFormat(), config))
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public TournamentConfigFormResponse getConfigForm(Long userId, Long tournamentId, boolean enforceOwnership) {
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		TournamentFormatDefinition format = getFormatDefinition(tournament.getFormat());
		TournamentConfig config = getConfig(tournamentId);
		List<FormatConfigField> formatFields = formatConfigFieldRepository
				.findByFormatCodeAndIsVisibleToOwnerTrueOrderByIdAsc(tournament.getFormat());
		List<FormatRaceToRule> formatRules = formatRaceToRuleRepository
				.findByFormatCodeOrderByIdAsc(tournament.getFormat());

		List<TournamentConfigFormResponse.ConfigFieldItem> fields = formatFields.stream()
				.map(ff -> toConfigFieldItem(tournamentId, ff))
				.toList();

		List<TournamentConfigFormResponse.RaceToRuleItem> raceToRules = formatRules.stream()
				.map(rule -> toRaceToRuleItem(tournamentId, rule))
				.toList();

		return TournamentConfigFormResponse.builder()
				.tournamentId(tournamentId)
				.tournamentName(tournament.getName())
				.formatCode(format.getCode())
				.formatName(format.getName())
				.formatDescription(format.getDescription())
				.gameType(tournament.getGameType())
				.seedingMethod(config.getSeedingMethod())
				.isConfigComplete(isConfigComplete(tournamentId, tournament.getFormat()))
				.fields(fields)
				.raceToRules(raceToRules)
				.seedingOptions(SEEDING_OPTIONS)
				.build();
	}

	@Override
	@Transactional
	public SaveTournamentConfigResponse saveConfig(Long userId, Long tournamentId,
	                                               SaveTournamentConfigRequest request,
	                                               boolean enforceOwnership) {
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		assertEditableStatus(tournament);

		if (!SeedingMethod.isValid(request.getSeedingMethod())) {
			throw new ConfigValidationException(ErrorCode.CONFIG_VALIDATION_FAILED, List.of(
					ConfigValidationDetailResponse.builder()
							.fieldKey("seedingMethod")
							.message("Invalid seeding method")
							.build()));
		}

		List<FormatConfigField> allFormatFields = formatConfigFieldRepository
				.findByFormatCodeOrderByIdAsc(tournament.getFormat());
		Map<String, FormatConfigField> formatFieldMap = allFormatFields.stream()
				.collect(Collectors.toMap(FormatConfigField::getFieldKey, f -> f));

		Map<String, String> requestValues = request.getFields().stream()
				.collect(Collectors.toMap(
						SaveTournamentConfigRequest.ConfigFieldValueItem::getFieldKey,
						SaveTournamentConfigRequest.ConfigFieldValueItem::getValue,
						(a, b) -> b));

		List<ConfigValidationDetailResponse> errors = new ArrayList<>();

		for (String fieldKey : requestValues.keySet()) {
			if (!formatFieldMap.containsKey(fieldKey)) {
				errors.add(ConfigValidationDetailResponse.builder()
						.fieldKey(fieldKey)
						.field(fieldKey)
						.message("Field does not belong to format")
						.build());
			}
		}
		if (!errors.isEmpty()) {
			throw new ConfigValidationException(ErrorCode.INVALID_FIELD_FOR_FORMAT, errors);
		}

		Map<String, String> valuesToSave = new LinkedHashMap<>();
		for (FormatConfigField formatField : allFormatFields) {
			String fieldKey = formatField.getFieldKey();
			String value = requestValues.get(fieldKey);
			if (value == null || value.isBlank()) {
				if (Boolean.TRUE.equals(formatField.getIsRequired())) {
					value = formatField.getDefaultValue();
				}
			}
			if (Boolean.TRUE.equals(formatField.getIsRequired())
					&& (value == null || value.isBlank())) {
				errors.add(ConfigValidationDetailResponse.builder()
						.fieldKey(fieldKey)
						.field(fieldKey)
						.message("Required field is missing")
						.build());
				continue;
			}
			if (value != null && !value.isBlank()) {
				errors.addAll(validateFieldValue(formatField, value));
				valuesToSave.put(fieldKey, value);
			}
		}

		if (!errors.isEmpty()) {
			throw new ConfigValidationException(ErrorCode.CONFIG_VALIDATION_FAILED, errors);
		}

		configValueService.saveAll(tournamentId, valuesToSave);

		if (request.getRaceToOverrides() != null) {
			for (SaveTournamentConfigRequest.RaceToOverrideItem override : request.getRaceToOverrides()) {
				FormatRaceToRule defaultRule = formatRaceToRuleRepository
						.findByFormatCodeAndRoundKey(tournament.getFormat(), override.getRoundKey())
						.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FIELD_FOR_FORMAT));

				if (!override.getRaceTo().equals(defaultRule.getRaceTo())) {
					TournamentRaceToRule rule = TournamentRaceToRule.builder()
							.tournament(tournament)
							.roundKey(override.getRoundKey())
							.bracketPhase(defaultRule.getBracketPhase())
							.raceTo(override.getRaceTo())
							.build();
					raceToRuleService.upsert(rule);
				} else {
					raceToRuleService.deleteByTournamentAndRoundKey(tournamentId, override.getRoundKey());
				}
			}
		}

		TournamentConfig config = getConfig(tournamentId);
		config.setSeedingMethod(request.getSeedingMethod());
		boolean complete = isConfigComplete(tournamentId, tournament.getFormat());
		if (complete) {
			config.setConfigSnapshotJson(buildConfigSnapshot(tournamentId, tournament.getFormat()));
		}
		tournamentConfigRepository.save(config);

		return SaveTournamentConfigResponse.builder()
				.tournamentId(tournamentId)
				.formatCode(tournament.getFormat())
				.seedingMethod(config.getSeedingMethod())
				.isConfigComplete(complete)
				.validationErrors(List.of())
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public TournamentConfigResolvedResponse getResolvedConfig(Long userId, Long tournamentId,
	                                                          boolean enforceOwnership) {
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		TournamentFormatDefinition format = getFormatDefinition(tournament.getFormat());
		TournamentConfig config = getConfig(tournamentId);

		Map<String, Object> fields = buildResolvedFields(tournamentId, tournament.getFormat());
		List<FormatRaceToRule> formatRules = formatRaceToRuleRepository
				.findByFormatCodeOrderByIdAsc(tournament.getFormat());
		Map<String, Integer> raceToMap = new LinkedHashMap<>();
		List<String> overriddenRounds = new ArrayList<>();

		for (FormatRaceToRule rule : formatRules) {
			int resolved = raceToRuleService.resolveRaceTo(tournamentId, tournament.getFormat(), rule.getRoundKey());
			raceToMap.put(rule.getRoundKey(), resolved);
			boolean overridden = raceToRuleService.getByTournament(tournamentId).stream()
					.anyMatch(r -> r.getRoundKey().equals(rule.getRoundKey()));
			if (overridden) {
				overriddenRounds.add(rule.getRoundKey());
			}
		}

		return TournamentConfigResolvedResponse.builder()
				.tournamentId(tournamentId)
				.formatCode(format.getCode())
				.formatName(format.getName())
				.gameType(tournament.getGameType())
				.seedingMethod(config.getSeedingMethod())
				.isConfigComplete(isConfigComplete(tournamentId, tournament.getFormat()))
				.fields(fields)
				.raceToRules(raceToMap)
				.overriddenRounds(overriddenRounds)
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public TournamentConfigValidateResponse validateConfig(Long userId, Long tournamentId,
	                                                       boolean enforceOwnership) {
		loadTournament(userId, tournamentId, enforceOwnership);
		List<ConfigValidationDetailResponse> errors = collectConfigErrors(tournamentId,
				tournamentRepository.findById(tournamentId).orElseThrow().getFormat());
		boolean complete = errors.isEmpty();

		return TournamentConfigValidateResponse.builder()
				.tournamentId(tournamentId)
				.isValid(complete)
				.isConfigComplete(complete)
				.errors(errors)
				.warnings(List.of())
				.build();
	}

	@Override
	@Transactional
	public PatchTournamentStatusResponse patchStatus(Long userId, Long tournamentId,
	                                               PatchTournamentStatusRequest request,
	                                               boolean enforceOwnership) {
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		String previousStatus = tournament.getStatus();
		String newStatus = request.getStatus();

		if (previousStatus.equals(newStatus)) {
			return PatchTournamentStatusResponse.builder()
					.id(tournamentId)
					.status(newStatus)
					.previousStatus(previousStatus)
					.build();
		}

		Set<String> allowed = STATUS_TRANSITIONS.getOrDefault(previousStatus, Set.of());
		if (!allowed.contains(newStatus)) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
		}

		if ("OPEN_FOR_REGISTRATION".equals(newStatus)) {
			List<ConfigValidationDetailResponse> errors = collectConfigErrors(tournamentId, tournament.getFormat());
			if (!errors.isEmpty()) {
				throw new ConfigValidationException(ErrorCode.CONFIG_INCOMPLETE, errors);
			}
		}

		tournament.setStatus(newStatus);
		tournamentRepository.save(tournament);

		return PatchTournamentStatusResponse.builder()
				.id(tournamentId)
				.status(newStatus)
				.previousStatus(previousStatus)
				.build();
	}

	private TournamentListItemResponse toListItem(Tournament tournament) {
		String formatName = formatRepository.findById(tournament.getFormat())
				.map(TournamentFormatDefinition::getName)
				.orElse(null);

		return TournamentListItemResponse.builder()
				.id(tournament.getId())
				.name(tournament.getName())
				.gameType(tournament.getGameType())
				.format(tournament.getFormat())
				.formatName(formatName)
				.status(tournament.getStatus())
				.maxParticipants(tournament.getMaxParticipants())
				.entryFee(tournament.getEntryFee())
				.configComplete(isConfigComplete(tournament.getId(), tournament.getFormat()))
				.startAt(tournament.getStartAt())
				.endAt(tournament.getEndAt())
				.createdAt(tournament.getCreatedAt())
				.build();
	}

	private OwnerFormatListItemResponse toOwnerFormatItem(TournamentFormatDefinition format) {
		boolean ready = isFormatReady(format.getCode());
		return OwnerFormatListItemResponse.builder()
				.code(format.getCode())
				.name(format.getName())
				.description(format.getDescription())
				.sortOrder(format.getSortOrder())
				.isReady(ready)
				.build();
	}

	private boolean isFormatReady(String formatCode) {
		long configCount = formatConfigFieldRepository.countByFormatCode(formatCode);
		long raceCount = formatRaceToRuleRepository.countByFormatCode(formatCode);
		return configCount > 0 && raceCount > 0;
	}

	private void validateGameType(String code) {
		GameTypeDefinition gameType = gameTypeRepository.findById(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.GAME_TYPE_NOT_FOUND));
		if (!Boolean.TRUE.equals(gameType.getIsActive())) {
			throw new BusinessException(ErrorCode.GAME_TYPE_NOT_FOUND);
		}
	}

	private void validateFormatReady(String formatCode) {
		TournamentFormatDefinition format = formatRepository.findById(formatCode)
				.orElseThrow(() -> new BusinessException(ErrorCode.FORMAT_NOT_FOUND));
		if (!Boolean.TRUE.equals(format.getIsActive())) {
			throw new BusinessException(ErrorCode.FORMAT_NOT_FOUND);
		}
		if (!isFormatReady(formatCode)) {
			throw new BusinessException(ErrorCode.FORMAT_NOT_READY);
		}
	}

	private TournamentFormatDefinition getFormatDefinition(String code) {
		return formatRepository.findById(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.FORMAT_NOT_FOUND));
	}

	private Tournament loadTournament(Long userId, Long tournamentId, boolean enforceOwnership) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (enforceOwnership && !tournament.getCreatedBy().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		return tournament;
	}

	private TournamentConfig getConfig(Long tournamentId) {
		return tournamentConfigRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	private void assertEditableStatus(Tournament tournament) {
		if (!"DRAFT".equals(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_OPERATION);
		}
	}

	private boolean isConfigComplete(Long tournamentId, String formatCode) {
		return collectConfigErrors(tournamentId, formatCode).isEmpty();
	}

	private List<ConfigValidationDetailResponse> collectConfigErrors(Long tournamentId, String formatCode) {
		List<ConfigValidationDetailResponse> errors = new ArrayList<>();
		TournamentConfig config = getConfig(tournamentId);

		if (config.getSeedingMethod() == null || config.getSeedingMethod().isBlank()) {
			errors.add(ConfigValidationDetailResponse.builder()
					.fieldKey("seedingMethod")
					.message("Seeding method is required")
					.build());
		}

		List<FormatConfigField> formatFields = formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(formatCode);
		for (FormatConfigField formatField : formatFields) {
			if (!Boolean.TRUE.equals(formatField.getIsRequired())) {
				continue;
			}
			String value = resolveFieldValue(tournamentId, formatField);
			if (value == null || value.isBlank()) {
				errors.add(ConfigValidationDetailResponse.builder()
						.fieldKey(formatField.getFieldKey())
						.field(formatField.getFieldKey())
						.message("Required field is missing")
						.build());
			} else {
				errors.addAll(validateFieldValue(formatField, value));
			}
		}

		long raceCount = formatRaceToRuleRepository.countByFormatCode(formatCode);
		if (raceCount == 0) {
			errors.add(ConfigValidationDetailResponse.builder()
					.fieldKey("raceToRules")
					.message("Format has no race-to rules")
					.build());
		}

		return errors;
	}

	private String resolveFieldValue(Long tournamentId, FormatConfigField formatField) {
		return configValueService.getByTournamentAndField(tournamentId, formatField.getFieldKey())
				.map(TournamentConfigValue::getValue)
				.orElse(formatField.getDefaultValue());
	}

	private TournamentConfigFormResponse.ConfigFieldItem toConfigFieldItem(Long tournamentId,
	                                                                       FormatConfigField formatField) {
		ConfigFieldDefinition def = formatField.getFieldDefinition();
		if (def == null) {
			def = configFieldRepository.findById(formatField.getFieldKey()).orElse(null);
		}
		Optional<TournamentConfigValue> saved = configValueService.getByTournamentAndField(
				tournamentId, formatField.getFieldKey());
		String value = saved.map(TournamentConfigValue::getValue).orElse(formatField.getDefaultValue());
		FieldSource source = saved.isPresent() ? FieldSource.TOURNAMENT : FieldSource.ADMIN_DEFAULT;

		return TournamentConfigFormResponse.ConfigFieldItem.builder()
				.fieldKey(formatField.getFieldKey())
				.label(def != null ? def.getLabel() : formatField.getFieldKey())
				.description(def != null ? def.getDescription() : null)
				.dataType(def != null ? def.getDataType() : null)
				.uiComponent(def != null ? def.getUiComponent() : null)
				.enumOptions(def != null ? JsonParseUtil.parseStringList(def.getEnumOptions()) : null)
				.minValue(def != null ? def.getMinValue() : null)
				.maxValue(def != null ? def.getMaxValue() : null)
				.value(value)
				.defaultValue(formatField.getDefaultValue())
				.isRequired(formatField.getIsRequired())
				.source(source)
				.build();
	}

	private TournamentConfigFormResponse.RaceToRuleItem toRaceToRuleItem(Long tournamentId, FormatRaceToRule rule) {
		Optional<TournamentRaceToRule> override = raceToRuleService.getByTournament(tournamentId).stream()
				.filter(r -> r.getRoundKey().equals(rule.getRoundKey()))
				.findFirst();
		int raceTo = override.map(TournamentRaceToRule::getRaceTo).orElse(rule.getRaceTo());
		boolean isOverridden = override.isPresent();

		return TournamentConfigFormResponse.RaceToRuleItem.builder()
				.roundKey(rule.getRoundKey())
				.label(rule.getLabel())
				.bracketPhase(rule.getBracketPhase())
				.raceTo(raceTo)
				.defaultRaceTo(rule.getRaceTo())
				.isOverridden(isOverridden)
				.source(isOverridden ? FieldSource.TOURNAMENT : FieldSource.ADMIN_DEFAULT)
				.build();
	}

	private List<ConfigValidationDetailResponse> validateFieldValue(FormatConfigField formatField, String value) {
		ConfigFieldDefinition def = formatField.getFieldDefinition();
		if (def == null) {
			def = configFieldRepository.findById(formatField.getFieldKey()).orElse(null);
		}
		if (def == null) {
			return List.of();
		}

		List<ConfigValidationDetailResponse> errors = new ArrayList<>();
		String fieldKey = formatField.getFieldKey();

		switch (def.getDataType()) {
			case "INT" -> {
				try {
					int intVal = Integer.parseInt(value);
					if (def.getMinValue() != null && intVal < def.getMinValue()) {
						errors.add(detail(fieldKey, "Value below minimum"));
					}
					if (def.getMaxValue() != null && intVal > def.getMaxValue()) {
						errors.add(detail(fieldKey, "Value above maximum"));
					}
				} catch (NumberFormatException e) {
					errors.add(detail(fieldKey, "Invalid integer value"));
				}
			}
			case "BOOLEAN" -> {
				if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
					errors.add(detail(fieldKey, "Invalid boolean value"));
				}
			}
			case "ENUM" -> {
				List<String> options = JsonParseUtil.parseStringList(def.getEnumOptions());
				if (options != null && !options.isEmpty() && !options.contains(value)) {
					errors.add(detail(fieldKey, "Value not in allowed options"));
				}
			}
			default -> {
				if (value.isBlank()) {
					errors.add(detail(fieldKey, "Value cannot be blank"));
				}
			}
		}
		return errors;
	}

	private ConfigValidationDetailResponse detail(String fieldKey, String message) {
		return ConfigValidationDetailResponse.builder()
				.fieldKey(fieldKey)
				.field(fieldKey)
				.message(message)
				.build();
	}

	private Map<String, Object> buildResolvedFields(Long tournamentId, String formatCode) {
		Map<String, Object> result = new LinkedHashMap<>();
		List<FormatConfigField> formatFields = formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(formatCode);
		for (FormatConfigField formatField : formatFields) {
			String raw = resolveFieldValue(tournamentId, formatField);
			ConfigFieldDefinition def = formatField.getFieldDefinition();
			if (def == null) {
				def = configFieldRepository.findById(formatField.getFieldKey()).orElse(null);
			}
			result.put(formatField.getFieldKey(), coerceValue(def, raw));
		}
		return result;
	}

	private Object coerceValue(ConfigFieldDefinition def, String raw) {
		if (raw == null || def == null) {
			return raw;
		}
		return switch (def.getDataType()) {
			case "INT" -> {
				try {
					yield Integer.parseInt(raw);
				} catch (NumberFormatException e) {
					yield raw;
				}
			}
			case "BOOLEAN" -> Boolean.parseBoolean(raw);
			default -> raw;
		};
	}

	private String buildConfigSnapshot(Long tournamentId, String formatCode) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("fields", buildResolvedFields(tournamentId, formatCode));
		List<FormatRaceToRule> rules = formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(formatCode);
		Map<String, Integer> raceTo = new LinkedHashMap<>();
		for (FormatRaceToRule rule : rules) {
			raceTo.put(rule.getRoundKey(),
					raceToRuleService.resolveRaceTo(tournamentId, formatCode, rule.getRoundKey()));
		}
		snapshot.put("raceToRules", raceTo);
		return JsonParseUtil.toJson(snapshot);
	}

	private TournamentDetailResponse.ConfigSummary buildConfigSummary(Long tournamentId, String formatCode,
	                                                                  TournamentConfig config) {
		Map<String, Object> fields = buildResolvedFields(tournamentId, formatCode);
		Integer bracketSize = fields.get("bracket_size") instanceof Integer i ? i : null;
		Boolean thirdPlace = fields.get("third_place_match") instanceof Boolean b ? b : null;
		String breakRule = fields.get("break_rule") != null ? fields.get("break_rule").toString() : null;

		Integer finalRaceTo = null;
		try {
			finalRaceTo = raceToRuleService.resolveRaceTo(tournamentId, formatCode, "final");
		} catch (BusinessException ignored) {
			// grand_final for double elim
			try {
				finalRaceTo = raceToRuleService.resolveRaceTo(tournamentId, formatCode, "grand_final");
			} catch (BusinessException ignored2) {
				// no final round
			}
		}

		return TournamentDetailResponse.ConfigSummary.builder()
				.seedingMethod(config.getSeedingMethod())
				.bracketSize(bracketSize)
				.thirdPlaceMatch(thirdPlace)
				.breakRule(breakRule)
				.finalRaceTo(finalRaceTo)
				.build();
	}
}
