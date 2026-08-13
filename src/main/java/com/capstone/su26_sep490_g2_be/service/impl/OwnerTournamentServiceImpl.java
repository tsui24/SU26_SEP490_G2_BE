package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.BranchStatus;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.FieldSource;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.SeedingMethod;
import com.capstone.su26_sep490_g2_be.enums.TournamentFormat;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.util.ProgressiveSurvivorsUtil;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.exception.ConfigValidationException;
import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.repository.*;
import com.capstone.su26_sep490_g2_be.service.AdminRegistrationFormService;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.service.OwnerTournamentService;
import com.capstone.su26_sep490_g2_be.service.RegistrationFormService;
import com.capstone.su26_sep490_g2_be.service.TournamentAuditService;
import com.capstone.su26_sep490_g2_be.service.TournamentPublishedEvent;
import com.capstone.su26_sep490_g2_be.service.TournamentResultService;
import com.capstone.su26_sep490_g2_be.service.TournamentConfigValueService;
import com.capstone.su26_sep490_g2_be.service.TournamentRaceToRuleService;
import com.capstone.su26_sep490_g2_be.util.AvatarUrlResolver;
import com.capstone.su26_sep490_g2_be.util.JsonParseUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerTournamentServiceImpl implements OwnerTournamentService {

	private static final List<String> SEEDING_OPTIONS = List.of(
			SeedingMethod.RANDOM.name(), SeedingMethod.RANK.name());

	/** Đồng bộ maxParticipants <-> bracket_size chỉ áp dụng cho thể thức Loại trực tiếp (1 lần thua). */
	private static final String SINGLE_ELIMINATION_FORMAT_CODE = "SINGLE_ELIMINATION";
	private static final String DOUBLE_ELIMINATION_FORMAT_CODE = "DOUBLE_ELIMINATION";

	/**
	 * {@code bracket_size} là giá trị dẫn xuất — luôn bằng số người ACTIVE thực tế của giải, không
	 * phải giá trị Owner nhập. Xem {@link #countActiveParticipants(Long)}.
	 */
	private static final String BRACKET_SIZE_FIELD_KEY = "bracket_size";

	/**
	 * DRAW_DONE chỉ được vào qua bracketGenerationService.confirmDraw() (không phải patchStatus trực
	 * tiếp) nên REGISTRATION_CLOSED không có đường đi thẳng tới DRAW_DONE ở đây — tránh bỏ qua bước
	 * sinh bracket. Tương tự DRAW_PREVIEW/FINAL_BRACKET_READY chỉ vào qua generate()/populateFinalBracket().
	 */
	private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
			TournamentStatus.DRAFT.getValue(), Set.of(
					TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), TournamentStatus.CANCELLED.getValue()),
			TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), Set.of(
					TournamentStatus.REGISTRATION_CLOSED.getValue(), TournamentStatus.CANCELLED.getValue()),
			TournamentStatus.REGISTRATION_CLOSED.getValue(), Set.of(
					TournamentStatus.CANCELLED.getValue()),
			TournamentStatus.DRAW_DONE.getValue(), Set.of(
					TournamentStatus.IN_PROGRESS.getValue(), TournamentStatus.CANCELLED.getValue()),
			TournamentStatus.FINAL_BRACKET_READY.getValue(), Set.of(
					TournamentStatus.COMPLETED.getValue(), TournamentStatus.CANCELLED.getValue()),
			TournamentStatus.IN_PROGRESS.getValue(), Set.of(
					TournamentStatus.COMPLETED.getValue(), TournamentStatus.CANCELLED.getValue()));

	private static final List<String> FINISHED_MATCH_STATUSES = List.of(
			MatchStatus.COMPLETED.getValue(), MatchStatus.WALKOVER.getValue(), MatchStatus.BYE.getValue());

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
	private final AdminRegistrationFormService adminRegistrationFormService;
	private final RegistrationFormService registrationFormService;
	private final RegistrationFormTemplateRepository registrationFormTemplateRepository;
	private final RegistrationRepository registrationRepository;
	private final ParticipantRepository participantRepository;
	private final MatchRepository matchRepository;
	private final MinioStorageService minioStorageService;
	private final MinioProperties minioProperties;
	private final BranchRepository branchRepository;
	private final BranchAccessService branchAccessService;
	private final TournamentAuditService tournamentAuditService;
	private final TournamentResultService tournamentResultService;
	private final ApplicationEventPublisher eventPublisher;
	private final MailContextBuilder mailContextBuilder;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<TournamentListItemResponse> listTournaments(
			Long userId,
			boolean filterByOwner,
			String status,
			String search,
			String gameType,
			String participantType,
			Boolean isRegister,
			Long branchId,
			int page,
			int size) {
		String statusParam = (status == null || status.isBlank()) ? null : status.trim();
		String searchParam = (search == null || search.isBlank()) ? null : search.trim();
		String gameTypeParam = (gameType == null || gameType.isBlank()) ? null : gameType.trim();
		String participantTypeParam = (participantType == null || participantType.isBlank()) ? null : participantType.trim();
		List<Long> branchIds = filterByOwner ? resolveAccessibleBranchIds(userId) : null;

		if (size < 1)
			size = 10;
		if (page < 0)
			page = 0;

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		Specification<Tournament> spec = buildSpec(branchIds, statusParam, searchParam, null)
				.and(buildExtraFiltersSpec(gameTypeParam, participantTypeParam, isRegister, branchId));
		Page<Tournament> tournamentPage = tournamentRepository.findAll(spec, pageable);
		return toListResponse(tournamentPage, true);
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
	public OwnerRegistrationFormTemplateListResponse listRegistrationFormTemplates() {
		return adminRegistrationFormService.listActiveTemplatesForOwner();
	}

	@Override
	public RegistrationFormPreviewResponse previewRegistrationFormTemplate(Long templateId) {
		return adminRegistrationFormService.getActiveTemplatePreview(templateId);
	}

	@Override
	@Transactional
	public CreateTournamentResponse createTournament(Long userId, CreateTournamentRequest request) {
		User creator = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		validateGameType(request.getGameType());
		validateFormatReady(request.getFormat());
		validateParticipantType(request.getParticipantType());

		boolean isRegister = Boolean.TRUE.equals(request.getIsRegister());
		registrationFormService.validateRegistrationSettings(isRegister, request.getRegistrationFormTemplateId());

		validateTournamentDates(
				request.getRegistrationDeadline(),
				request.getStartAt(),
				request.getEndAt());

		Tournament tournament = Tournament.builder()
				.name(request.getName())
				.description(request.getDescription())
				.thumbnailUrl(normalizeImageForStorage(request.getThumbnailUrl()))
				.bannerUrl(normalizeImageForStorage(request.getBannerUrl()))
				.gameType(request.getGameType())
				.format(request.getFormat())
				.participantType(request.getParticipantType())
				.status(TournamentStatus.DRAFT.getValue())
				.maxParticipants(request.getMaxParticipants())
				.tableCount(request.getTableCount() != null && request.getTableCount() > 0 ? request.getTableCount() : 1)
				.entryFee(request.getEntryFee() != null ? request.getEntryFee() : BigDecimal.ZERO)
				.prizePool(request.getPrizePool())
				.prizeDescription(request.getPrizeDescription())
				.registrationDeadline(request.getRegistrationDeadline())
				.startAt(request.getStartAt())
				.endAt(request.getEndAt())
				.isRegister(isRegister)
				.isShowTournament(Boolean.TRUE.equals(request.getIsShowTournament()))
				.isPublicRatio(Boolean.TRUE.equals(request.getIsPublicRatio()))
				.registrationFormTemplateId(isRegister ? request.getRegistrationFormTemplateId() : null)
				.createdBy(creator)
				.build();
		validateAndSnapshotBranch(creator, tournament, request.getBranchId());
		tournament = tournamentRepository.save(tournament);

		TournamentConfig config = TournamentConfig.builder()
				.tournament(tournament)
				.formatCode(request.getFormat())
				.seedingMethod(SeedingMethod.RANDOM.name())
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
				.tableCount(tournament.getTableCount())
				.configComplete(false)
				.isRegister(Boolean.TRUE.equals(tournament.getIsRegister()))
				.registrationFormTemplateId(tournament.getRegistrationFormTemplateId())
				.build();
	}

	@Override
	@Transactional
	public UpdateTournamentResponse updateTournament(Long userId, Long tournamentId,
			UpdateTournamentRequest request,
			boolean enforceOwnership) {
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		assertEditableStatus(tournament);

		// Optimistic lock thủ công: FE gửi lại version đã thấy lúc load form. Nếu ai đó khác đã lưu
		// giải này sau thời điểm đó (version DB đã tăng), version FE gửi lên sẽ lệch → chặn ngay thay
		// vì để Hibernate ghi đè âm thầm (request không sửa hết mọi field nên rất dễ mất field của
		// người kia). Bỏ trống version ở request = không kiểm tra (tương thích ngược).
		if (request.getVersion() != null && !request.getVersion().equals(tournament.getVersion())) {
			throw new BusinessException(ErrorCode.CONCURRENT_UPDATE_CONFLICT);
		}

		if (request.getBranchId() != null
				&& (tournament.getBranch() == null || !request.getBranchId().equals(tournament.getBranch().getId()))) {
			User currentUser = userRepository.findById(userId)
					.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
			validateAndSnapshotBranch(currentUser, tournament, request.getBranchId());
		}

		boolean formatChanged = request.getFormat() != null && !request.getFormat().equals(tournament.getFormat());
		if (formatChanged) {
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
		if (request.getThumbnailUrl() != null) {
			tournament.setThumbnailUrl(normalizeImageForStorage(request.getThumbnailUrl()));
		}
		if (request.getBannerUrl() != null) {
			tournament.setBannerUrl(normalizeImageForStorage(request.getBannerUrl()));
		}
		if (request.getMaxParticipants() != null) {
			tournament.setMaxParticipants(request.getMaxParticipants());
		}
		if (request.getTableCount() != null && request.getTableCount() > 0) {
			tournament.setTableCount(request.getTableCount());
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
		if (request.getIsRegister() != null) {
			tournament.setIsRegister(request.getIsRegister());
			if (!request.getIsRegister()) {
				tournament.setRegistrationFormTemplateId(null);
			}
		}
		if (request.getIsShowTournament() != null) {
			tournament.setIsShowTournament(request.getIsShowTournament());
		}
		if (request.getIsPublicRatio() != null) {
			tournament.setIsPublicRatio(request.getIsPublicRatio());
		}
		if (request.getRegistrationFormTemplateId() != null) {
			tournament.setRegistrationFormTemplateId(request.getRegistrationFormTemplateId());
		}
		registrationFormService.validateRegistrationSettings(
				Boolean.TRUE.equals(tournament.getIsRegister()), tournament.getRegistrationFormTemplateId());

		validateTournamentDates(
				tournament.getRegistrationDeadline(),
				tournament.getStartAt(),
				tournament.getEndAt(),
				request.getRegistrationDeadline() != null,
				request.getStartAt() != null,
				request.getEndAt() != null);

		tournamentRepository.saveAndFlush(tournament);
		boolean configComplete = isConfigComplete(tournamentId, tournament.getFormat());

		return UpdateTournamentResponse.builder()
				.id(tournament.getId())
				.version(tournament.getVersion())
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
		RegistrationFormTemplate registrationTemplate = tournament.getRegistrationFormTemplateId() != null
				? registrationFormTemplateRepository.findById(tournament.getRegistrationFormTemplateId()).orElse(null)
				: null;
		long approved = participantRepository.countByTournamentIdAndStatus(tournamentId, ParticipantStatus.ACTIVE.getValue());
		int remaining = Math.max(0, tournament.getMaxParticipants() - (int) approved);

		return TournamentDetailResponse.builder()
				.id(tournament.getId())
				.version(tournament.getVersion())
				.name(tournament.getName())
				.description(tournament.getDescription())
				.gameType(tournament.getGameType())
				.format(tournament.getFormat())
				.formatName(format != null ? format.getName() : null)
				.participantType(tournament.getParticipantType())
				.status(tournament.getStatus())
				.maxParticipants(tournament.getMaxParticipants())
				.tableCount(tournament.getTableCount())
				.entryFee(tournament.getEntryFee())
				.prizePool(tournament.getPrizePool())
				.prizeDescription(tournament.getPrizeDescription())
				.registrationDeadline(tournament.getRegistrationDeadline())
				.startAt(tournament.getStartAt())
				.endAt(tournament.getEndAt())
				.configComplete(configComplete)
				.isRegister(Boolean.TRUE.equals(tournament.getIsRegister()))
				.isPublicRatio(Boolean.TRUE.equals(tournament.getIsPublicRatio()))
				.isShowTournament(Boolean.TRUE.equals(tournament.getIsShowTournament()))
				.approvedCount(approved)
				.remainingSlots(remaining)
				.registrationFormTemplateId(tournament.getRegistrationFormTemplateId())
				.registrationFormTemplateCode(registrationTemplate != null ? registrationTemplate.getCode() : null)
				.registrationFormTemplateName(registrationTemplate != null ? registrationTemplate.getName() : null)
				.thumbnailUrl(resolveImageForResponse(tournament.getThumbnailUrl()))
				.bannerUrl(resolveImageForResponse(tournament.getBannerUrl()))
				.configSummary(buildConfigSummary(tournamentId, tournament.getFormat(), config))
				.venue(buildVenue(tournament))
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public RegistrationFormPreviewResponse getTournamentRegistrationForm(
			Long userId, Long tournamentId, boolean enforceOwnership) {
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		return registrationFormService.resolveTournamentForm(tournament);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<TournamentListItemResponse> listPlayerTournaments(
			String status, String search, int page, int size) {
		String statusParam = (status == null || status.isBlank()) ? null : status.trim();
		String searchParam = (search == null || search.isBlank()) ? null : search.trim();
		if (size < 1)
			size = 10;
		if (page < 0)
			page = 0;

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		Specification<Tournament> spec = buildSpec(null, statusParam, searchParam,
				List.of(TournamentStatus.DRAFT.getValue(), TournamentStatus.CANCELLED.getValue()));
		spec = spec.and((root, query, cb) -> cb.equal(root.get("isShowTournament"), true));
		Page<Tournament> result = tournamentRepository.findAll(spec, pageable);
		return toListResponse(result, false);
	}

	@Override
	@Transactional(readOnly = true)
	public TournamentDetailResponse getPlayerTournamentDetail(Long tournamentId) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (TournamentStatus.DRAFT.getValue().equals(tournament.getStatus())
				|| TournamentStatus.CANCELLED.getValue().equals(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}
		if (!Boolean.TRUE.equals(tournament.getIsShowTournament())) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}
		TournamentFormatDefinition format = formatRepository.findById(tournament.getFormat()).orElse(null);
		TournamentConfig config = getConfig(tournamentId);
		RegistrationFormTemplate registrationTemplate = tournament.getRegistrationFormTemplateId() != null
				? registrationFormTemplateRepository.findById(tournament.getRegistrationFormTemplateId()).orElse(null)
				: null;
		long approved = participantRepository.countByTournamentIdAndStatus(tournamentId, ParticipantStatus.ACTIVE.getValue());
		int remaining = Math.max(0, tournament.getMaxParticipants() - (int) approved);

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
				.tableCount(tournament.getTableCount())
				.entryFee(tournament.getEntryFee())
				.prizePool(tournament.getPrizePool())
				.prizeDescription(tournament.getPrizeDescription())
				.registrationDeadline(tournament.getRegistrationDeadline())
				.startAt(tournament.getStartAt())
				.endAt(tournament.getEndAt())
				.isRegister(Boolean.TRUE.equals(tournament.getIsRegister()))
				.isPublicRatio(Boolean.TRUE.equals(tournament.getIsPublicRatio()))
				.isShowTournament(Boolean.TRUE.equals(tournament.getIsShowTournament()))
				.approvedCount(approved)
				.remainingSlots(remaining)
				.registrationFormTemplateCode(registrationTemplate != null ? registrationTemplate.getCode() : null)
				.registrationFormTemplateName(registrationTemplate != null ? registrationTemplate.getName() : null)
				.bannerUrl(resolveImageForResponse(tournament.getBannerUrl()))
				.thumbnailUrl(resolveImageForResponse(tournament.getThumbnailUrl()))
				.configSummary(buildConfigSummary(tournamentId, tournament.getFormat(), config))
				.venue(buildVenue(tournament))
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
		assertConfigEditableStatus(tournament);

		if (!SeedingMethod.isValid(request.getSeedingMethod())) {
			throw new ConfigValidationException(ErrorCode.CONFIG_VALIDATION_FAILED, List.of(
					ConfigValidationDetailResponse.builder()
							.fieldKey("seedingMethod")
							.message("Phương thức xếp hạt giống không hợp lệ")
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
						.message("Field không thuộc thể thức này")
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
						.message("Thiếu field bắt buộc")
						.build());
				continue;
			}
			if (value != null && !value.isBlank()) {
				errors.addAll(validateFieldValue(formatField, value));
				valuesToSave.put(fieldKey, value);
			}
		}

		if (DOUBLE_ELIMINATION_FORMAT_CODE.equals(tournament.getFormat())) {
			errors.addAll(validateSePhaseSize(valuesToSave.get("se_phase_size"), tournament.getMaxParticipants()));
		}

		if (!errors.isEmpty()) {
			throw new ConfigValidationException(ErrorCode.CONFIG_VALIDATION_FAILED, errors);
		}

		// bracket_size là giá trị dẫn xuất từ số người thực tế — không lưu, và tuyệt đối không ghi
		// ngược vào maxParticipants. Trước đây chiều ghi ngược đó khiến giải 4 người bị đổi thành
		// 8 chỉ vì Owner bấm Lưu ở màn config.
		valuesToSave.remove(BRACKET_SIZE_FIELD_KEY);

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
		// lấy ra các field đã lưu config và các field default từ format, merge lại thành 1 map
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

		if (TournamentStatus.OPEN_FOR_REGISTRATION.getValue().equals(newStatus)) {
			List<ConfigValidationDetailResponse> errors = collectConfigErrors(tournamentId, tournament.getFormat());
			if (!errors.isEmpty()) {
				throw new ConfigValidationException(ErrorCode.CONFIG_INCOMPLETE, errors);
			}
			if (Boolean.TRUE.equals(tournament.getIsRegister())) {
				registrationFormService.validateRegistrationSettings(
						true, tournament.getRegistrationFormTemplateId());
			}
		}

		if (TournamentStatus.REGISTRATION_CLOSED.getValue().equals(newStatus)
				&& TournamentFormat.PROGRESSIVE_ROUND_ROBIN.getValue().equals(tournament.getFormat())) {
			validateProgressiveTurnoutOrThrow(tournament);
		}

		if (TournamentStatus.COMPLETED.getValue().equals(newStatus)) {
			boolean hasUnfinishedMatch = matchRepository.existsByTournamentIdAndStatusNotIn(
					tournamentId, FINISHED_MATCH_STATUSES);
			if (hasUnfinishedMatch) {
				throw new BusinessException(ErrorCode.TOURNAMENT_MATCHES_NOT_FINISHED);
			}
		}

		tournament.setStatus(newStatus);
		tournamentRepository.save(tournament);
		tournamentAuditService.recordChange(tournament, previousStatus, newStatus, userId,
				"Cập nhật trạng thái thủ công");

		if (TournamentStatus.COMPLETED.getValue().equals(newStatus)) {
			// Chốt kết quả chính thức (finalRank) ngay khi giải hoàn tất — trước đây bảng
			// tournament_results không bao giờ được ghi ngoài data seed demo.
			tournamentResultService.finalizeTournamentResults(tournamentId, userId);
		}

		Map<String, Object> variables = new HashMap<>(mailContextBuilder.systemContext());
		mailContextBuilder.putTournament(variables, tournament);
		eventPublisher.publishEvent(MailDomainEvent.builder()
				.eventType(EmailEventType.TOURNAMENT_STATUS_CHANGED)
				.tournamentId(tournament.getId())
				.variables(variables)
				.entityKey("TOURNAMENT-STATUS-" + tournament.getId() + "-" + newStatus)
				.build());

		if (TournamentStatus.OPEN_FOR_REGISTRATION.getValue().equals(newStatus)) {
			eventPublisher.publishEvent(TournamentPublishedEvent.builder()
					.tournamentId(tournament.getId())
					.build());
		}

		return PatchTournamentStatusResponse.builder()
				.id(tournamentId)
				.status(newStatus)
				.previousStatus(previousStatus)
				.build();
	}

	@Override
	@Transactional
	public PatchTournamentVisibilityResponse updateVisibility(Long userId, Long tournamentId,
			PatchTournamentVisibilityRequest request, boolean enforceOwnership) {
		// Cố ý KHÔNG gọi assertEditableStatus() — hiển thị công khai cần bật/tắt được ở mọi trạng thái
		// (không chỉ DRAFT), khác với các field thông tin cơ bản khác. Giải ở trạng thái DRAFT/CANCELLED
		// vẫn luôn bị ẩn khỏi trang công khai bất kể giá trị này (xem listPlayerTournaments/getPlayerTournamentDetail).
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		boolean previous = Boolean.TRUE.equals(tournament.getIsShowTournament());
		boolean next = Boolean.TRUE.equals(request.getIsShowTournament());

		tournament.setIsShowTournament(next);
		tournamentRepository.save(tournament);

		return PatchTournamentVisibilityResponse.builder()
				.id(tournamentId)
				.isShowTournament(next)
				.previousIsShowTournament(previous)
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public List<TournamentStatusHistoryResponse> getStatusHistory(Long tournamentId) {
		if (!tournamentRepository.existsById(tournamentId)) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}
		return tournamentAuditService.getHistory(tournamentId);
	}

	/**
	 * Xây PageResponse cho danh sách giải đấu mà KHÔNG phát sinh N+1: mọi lookup
	 * (format name, số người tham gia đã duyệt, config-complete) được batch 1 lần
	 * cho cả trang thay vì lặp query theo từng dòng như {@code toListItem}/{@code toPublicListItem} cũ.
	 */
	private PageResponse<TournamentListItemResponse> toListResponse(
			Page<Tournament> tournamentPage, boolean includeConfigComplete) {
		List<Tournament> tournaments = tournamentPage.getContent();
		if (tournaments.isEmpty()) {
			return PageResponse.of(tournamentPage, t -> null);
		}

		List<Long> tournamentIds = tournaments.stream().map(Tournament::getId).toList();
		Set<String> formatCodes = tournaments.stream().map(Tournament::getFormat).collect(Collectors.toSet());

		Map<String, String> formatNameByCode = formatRepository.findAllById(formatCodes).stream()
				.collect(Collectors.toMap(TournamentFormatDefinition::getCode, TournamentFormatDefinition::getName));

		Map<Long, Long> approvedCountByTournamentId = participantRepository
				.countGroupedByTournamentIdInAndStatus(tournamentIds, ParticipantStatus.ACTIVE.getValue()).stream()
				.collect(Collectors.toMap(ParticipantRepository.TournamentParticipantCount::getTournamentId,
						ParticipantRepository.TournamentParticipantCount::getTotal));

		Map<Long, Boolean> configCompleteByTournamentId = includeConfigComplete
				? batchConfigComplete(tournaments, formatCodes)
				: Map.of();

		return PageResponse.of(tournamentPage, tournament -> {
			var builder = TournamentListItemResponse.builder()
					.id(tournament.getId())
					.name(tournament.getName())
					.thumbnailUrl(resolveImageForList(tournament.getThumbnailUrl()))
					.gameType(tournament.getGameType())
					.format(tournament.getFormat())
					.formatName(formatNameByCode.get(tournament.getFormat()))
					.participantType(tournament.getParticipantType())
					.status(tournament.getStatus())
					.maxParticipants(tournament.getMaxParticipants())
					.tableCount(tournament.getTableCount())
					.entryFee(tournament.getEntryFee())
					.isRegister(Boolean.TRUE.equals(tournament.getIsRegister()))
					.isShowTournament(Boolean.TRUE.equals(tournament.getIsShowTournament()))
					.approvedCount(approvedCountByTournamentId.getOrDefault(tournament.getId(), 0L))
					.registrationDeadline(tournament.getRegistrationDeadline())
					.startAt(tournament.getStartAt())
					.endAt(tournament.getEndAt())
					.createdAt(tournament.getCreatedAt())
					.branchId(tournament.getBranch() != null ? tournament.getBranch().getId() : null)
					.venueName(tournament.getVenueName())
					.venueAddress(tournament.getVenueAddress());
			if (includeConfigComplete) {
				builder.configComplete(configCompleteByTournamentId.getOrDefault(tournament.getId(), false));
			}
			return builder.build();
		});
	}

	/** Bản batch của {@link #isConfigComplete(Long, String)} — dùng riêng cho list endpoint, 1 query/loại lookup cho cả trang. */
	private Map<Long, Boolean> batchConfigComplete(List<Tournament> tournaments, Set<String> formatCodes) {
		List<Long> tournamentIds = tournaments.stream().map(Tournament::getId).toList();

		Map<Long, TournamentConfig> configByTournamentId = tournamentConfigRepository.findAllById(tournamentIds).stream()
				.collect(Collectors.toMap(TournamentConfig::getTournamentId, c -> c));

		Map<String, List<FormatConfigField>> fieldsByFormat = formatConfigFieldRepository
				.findByFormatCodeInOrderByIdAsc(formatCodes).stream()
				.collect(Collectors.groupingBy(FormatConfigField::getFormatCode));

		Map<String, Long> raceCountByFormat = formatCodes.stream()
				.collect(Collectors.toMap(fc -> fc, formatRaceToRuleRepository::countByFormatCode));

		Map<Long, Map<String, String>> valuesByTournamentId = configValueService.getByTournamentIds(tournamentIds).stream()
				.collect(Collectors.groupingBy(cv -> cv.getId().getTournamentId(),
						Collectors.toMap(cv -> cv.getId().getFieldKey(), TournamentConfigValue::getValue, (a, b) -> a)));

		Map<Long, Boolean> result = new HashMap<>();
		for (Tournament tournament : tournaments) {
			Long tournamentId = tournament.getId();
			String formatCode = tournament.getFormat();
			TournamentConfig config = configByTournamentId.get(tournamentId);
			if (config == null || config.getSeedingMethod() == null || config.getSeedingMethod().isBlank()) {
				result.put(tournamentId, false);
				continue;
			}

			Map<String, String> savedValues = valuesByTournamentId.getOrDefault(tournamentId, Map.of());
			List<FormatConfigField> formatFields = fieldsByFormat.getOrDefault(formatCode, List.of());
			boolean complete = true;
			for (FormatConfigField field : formatFields) {
				if (!Boolean.TRUE.equals(field.getIsRequired())) {
					continue;
				}
				String value = savedValues.getOrDefault(field.getFieldKey(), field.getDefaultValue());
				if (value == null || value.isBlank() || !validateFieldValue(field, value).isEmpty()) {
					complete = false;
					break;
				}
			}
			if (complete && raceCountByFormat.getOrDefault(formatCode, 0L) == 0) {
				complete = false;
			}
			if (complete && TournamentFormat.PROGRESSIVE_ROUND_ROBIN.getValue().equals(formatCode)) {
				String survivorsCsv = resolveCachedField(formatFields, savedValues, "pe_survivors_per_stage");
				String playoffSizeStr = resolveCachedField(formatFields, savedValues, "final_playoff_size");
				if (survivorsCsv == null || survivorsCsv.isBlank() || playoffSizeStr == null || playoffSizeStr.isBlank()) {
					complete = false;
				} else {
					try {
						int playoffSize = Integer.parseInt(playoffSizeStr.trim());
						List<Integer> survivors = ProgressiveSurvivorsUtil.parse(survivorsCsv);
						int max = tournament.getMaxParticipants() != null ? tournament.getMaxParticipants() : 0;
						if (!ProgressiveSurvivorsUtil.validate(survivors, max, playoffSize).isEmpty()) {
							complete = false;
						}
					} catch (Exception ex) {
						complete = false;
					}
				}
			}
			result.put(tournamentId, complete);
		}
		return result;
	}

	private static String resolveCachedField(
			List<FormatConfigField> formatFields, Map<String, String> savedValues, String fieldKey) {
		return formatFields.stream()
				.filter(f -> fieldKey.equals(f.getFieldKey()))
				.findFirst()
				.map(f -> savedValues.containsKey(fieldKey) ? savedValues.get(fieldKey) : f.getDefaultValue())
				.orElse(null);
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

	/** Chỉ SINGLE/DOUBLE được cài đặt xử lý đầy đủ trong pipeline đăng ký — TEAM tạm thời chưa hỗ trợ. */
	private void validateParticipantType(String participantType) {
		if (!ParticipantType.SINGLE.name().equals(participantType)
				&& !ParticipantType.DOUBLE.name().equals(participantType)) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST,
					"Hình thức thi đấu chỉ hỗ trợ Đơn (SINGLE) hoặc Đôi (DOUBLE)");
		}
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
		if (enforceOwnership) {
			assertBranchAccess(userId, tournament);
		}
		return tournament;
	}

	/**
	 * Hệ thống chỉ có 1 chuỗi (nhiều chi nhánh, không phải nhiều chuỗi độc lập) nên Owner được xem
	 * toàn bộ tournament của cả chuỗi — không isolate Owner với nhau. Chỉ Manager mới bị giới hạn
	 * theo chi nhánh họ được cấp quyền qua {@link BranchAccessService} (đây mới là phân quyền thật
	 * cần enforce: "chi nhánh nào được gán thì chỉ nhìn thấy chi nhánh đó").
	 */
	private void assertBranchAccess(Long userId, Tournament tournament) {
		if (userId == null) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		User actor = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		Branch branch = tournament.getBranch();
		Long branchId = branch != null ? branch.getId() : null;
		if (!branchAccessService.canActorAccessBranch(actor, branchId)) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
	}

	/** Danh sách branchId giới hạn tầm nhìn — null nghĩa là "không lọc" (Owner xem cả chuỗi). */
	private List<Long> resolveAccessibleBranchIds(Long userId) {
		User actor = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		String roleCode = actor.getRole().getCode();
		if ("OWNER".equals(roleCode)) {
			return null;
		}
		if ("MANAGER".equals(roleCode)) {
			return branchAccessService.getAccessibleBranchIds(actor);
		}
		throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
	}

	private TournamentConfig getConfig(Long tournamentId) {
		return tournamentConfigRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	private void assertEditableStatus(Tournament tournament) {
		if (!TournamentStatus.DRAFT.getValue().equals(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_OPERATION);
		}
	}

	/**
	 * Cấu hình thể thức (saveConfig) được nới rộng hơn assertEditableStatus: roster chỉ thực sự bị
	 * khóa từ REGISTRATION_CLOSED trở đi (xem "rosterLocked" ở TournamentDetailPage.jsx — đăng ký
	 * chỉ mở ở DRAFT/OPEN_FOR_REGISTRATION), nên owner vẫn cần sửa được config (VD
	 * pe_survivors_per_stage khi số người đăng ký thực tế ít hơn giả định) miễn đăng ký còn mở.
	 * Không dùng chung với assertEditableStatus vì updateTournament (đổi format, tên, chi nhánh...)
	 * vẫn cần khóa cứng ở DRAFT như cũ.
	 */
	private void assertConfigEditableStatus(Tournament tournament) {
		String status = tournament.getStatus();
		if (!TournamentStatus.DRAFT.getValue().equals(status)
				&& !TournamentStatus.OPEN_FOR_REGISTRATION.getValue().equals(status)) {
			throw new BusinessException(ErrorCode.INVALID_OPERATION);
		}
	}

	/**
	 * Kiểm tra quyền của người tạo/sửa đối với chi nhánh, rồi chụp snapshot tên/địa chỉ
	 * vào tournament (branch có thể đổi thông tin sau này mà không ảnh hưởng giải đã tạo).
	 */
	private void validateAndSnapshotBranch(User currentUser, Tournament tournament, Long branchId) {
		Branch branch = branchRepository.findById(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND));
		String roleCode = currentUser.getRole().getCode();
		if ("OWNER".equals(roleCode)) {
			if (branch.getOwner() == null || !branch.getOwner().getId().equals(currentUser.getId())) {
				throw new BusinessException(ErrorCode.BRANCH_ACCESS_DENIED);
			}
			if (branch.getStatus() != BranchStatus.ACTIVE) {
				throw new BusinessException(ErrorCode.BRANCH_INACTIVE);
			}
		} else if ("MANAGER".equals(roleCode)) {
			if (!branchAccessService.canManagerCreateTournamentAt(currentUser, branchId)) {
				throw new BusinessException(ErrorCode.BRANCH_ACCESS_DENIED);
			}
		} else {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		tournament.setBranch(branch);
		tournament.setVenueName(branch.getName());
		tournament.setVenueAddress(branch.getAddress());
	}

	private BranchVenueResponse buildVenue(Tournament tournament) {
		if (tournament.getBranch() == null) {
			return null;
		}
		Branch branch = tournament.getBranch();
		List<String> imageKeys = JsonParseUtil.parseStringList(branch.getImageKeys());
		List<BranchImageResponse> images = imageKeys == null ? List.of() : imageKeys.stream()
				.map(key -> BranchImageResponse.builder()
						.key(key)
						.url(AvatarUrlResolver.resolveForResponse(key, minioStorageService, minioProperties.getBucket()))
						.build())
				.toList();
		return BranchVenueResponse.builder()
				.branchId(branch.getId())
				.name(tournament.getVenueName())
				.address(tournament.getVenueAddress())
				.phone(branch.getPhone())
				.images(images)
				.build();
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
					.message("Phương thức xếp hạt giống là bắt buộc")
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
						.message("Thiếu field bắt buộc")
						.build());
			} else {
				errors.addAll(validateFieldValue(formatField, value));
			}
		}

		long raceCount = formatRaceToRuleRepository.countByFormatCode(formatCode);
		if (raceCount == 0) {
			errors.add(ConfigValidationDetailResponse.builder()
					.fieldKey("raceToRules")
					.message("Thể thức chưa có quy tắc race-to")
					.build());
		}

		if (TournamentFormat.PROGRESSIVE_ROUND_ROBIN.getValue().equals(formatCode)) {
			errors.addAll(validateProgressiveConfig(tournamentId, formatFields));
		}

		return errors;
	}

	/**
	 * Validate cấu hình riêng cho PROGRESSIVE_ROUND_ROBIN: dãy {@code pe_survivors_per_stage}
	 * phải giảm dần nghiêm ngặt, mọi phần tử ≥ 4, phần tử cuối == {@code final_playoff_size},
	 * và {@code maxParticipants} lớn hơn phần tử đầu.
	 */
	private List<ConfigValidationDetailResponse> validateProgressiveConfig(
			Long tournamentId, List<FormatConfigField> formatFields) {
		List<ConfigValidationDetailResponse> errors = new ArrayList<>();

		String survivorsCsv = resolveFieldValueByKey(tournamentId, formatFields, "pe_survivors_per_stage");
		String playoffSizeStr = resolveFieldValueByKey(tournamentId, formatFields, "final_playoff_size");
		if (survivorsCsv == null || survivorsCsv.isBlank() || playoffSizeStr == null || playoffSizeStr.isBlank()) {
			return errors; // thiếu field đã được báo ở vòng lặp trước
		}

		int playoffSize;
		try {
			playoffSize = Integer.parseInt(playoffSizeStr.trim());
		} catch (NumberFormatException e) {
			errors.add(detail("final_playoff_size", "Số người vào Playoff phải là số nguyên"));
			return errors;
		}

		List<Integer> survivors;
		try {
			survivors = ProgressiveSurvivorsUtil.parse(survivorsCsv);
		} catch (IllegalArgumentException e) {
			errors.add(detail("pe_survivors_per_stage", e.getMessage()));
			return errors;
		}

		Integer maxParticipants = tournamentRepository.findById(tournamentId)
				.map(Tournament::getMaxParticipants).orElse(null);
		int max = maxParticipants != null ? maxParticipants : 0;

		for (String msg : ProgressiveSurvivorsUtil.validate(survivors, max, playoffSize)) {
			errors.add(detail("pe_survivors_per_stage", msg));
		}
		return errors;
	}

	/**
	 * Chốt đăng ký (đóng vòng đăng ký) cho PROGRESSIVE_ROUND_ROBIN: cấu hình pe_survivors_per_stage
	 * được validate lúc LƯU CONFIG dựa trên maxParticipants (số slot tối đa của giải), không phải
	 * số người ĐĂNG KÝ THẬT. Nếu tới lúc đóng đăng ký mà số người active vẫn ít hơn giả định của
	 * config (VD chỉ 8 người trong khi config "10,6,4" thiết kế cho 16), việc chốt roster ở đây sẽ
	 * khiến các giai đoạn sau sinh sai số lượng trận đấu. Chặn ngay tại bước "Đóng đăng ký" — sớm
	 * hơn nhiều so với lúc bốc thăm — để owner còn cơ hội sửa lại config cho khớp số người thực tế.
	 */
	private void validateProgressiveTurnoutOrThrow(Tournament tournament) {
		List<FormatConfigField> formatFields =
				formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(tournament.getFormat());
		String survivorsCsv = resolveFieldValueByKey(tournament.getId(), formatFields, "pe_survivors_per_stage");
		if (survivorsCsv == null || survivorsCsv.isBlank()) {
			return; // thiếu field bắt buộc đã được báo khi mở đăng ký (collectConfigErrors)
		}

		List<Integer> survivors;
		try {
			survivors = ProgressiveSurvivorsUtil.parse(survivorsCsv);
		} catch (IllegalArgumentException e) {
			return; // cấu hình sai định dạng đã được báo khi mở đăng ký
		}

		long activeCount = participantRepository.countByTournamentIdAndStatus(
				tournament.getId(), ParticipantStatus.ACTIVE.getValue());

		List<String> turnoutErrors = ProgressiveSurvivorsUtil.validate(
				survivors, (int) activeCount, survivors.get(survivors.size() - 1));
		if (!turnoutErrors.isEmpty()) {
			throw new BusinessException(ErrorCode.PROGRESSIVE_CONFIG_INVALID,
					"Số người tham gia thực tế (" + activeCount
							+ " người) chưa đủ so với cấu hình \"Số người đi tiếp mỗi giai đoạn\" (" + survivorsCsv
							+ "). Điều này sẽ ảnh hưởng tới việc tạo các cặp trận ở những giai đoạn sau."
							+ " Vui lòng sửa lại cấu hình cho khớp số người tham gia thực tế trước khi đóng đăng ký: "
							+ String.join("; ", turnoutErrors));
		}
	}

	private String resolveFieldValueByKey(Long tournamentId, List<FormatConfigField> formatFields, String fieldKey) {
		return formatFields.stream()
				.filter(f -> fieldKey.equals(f.getFieldKey()))
				.findFirst()
				.map(f -> resolveFieldValue(tournamentId, f))
				.orElse(null);
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

		// bracket_size là giá trị DẪN XUẤT, không phải giá trị người dùng nhập: luôn hiển thị số
		// người đang thực sự có mặt trong giải. Trước đây nó được lưu như một field độc lập và bị
		// clamp theo minValue=8, nên giải tạo 4 người lại hiện 8 ở màn config.
		if (BRACKET_SIZE_FIELD_KEY.equals(formatField.getFieldKey())) {
			value = String.valueOf(countActiveParticipants(tournamentId));
			source = FieldSource.TOURNAMENT;
		}

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
						errors.add(detail(fieldKey, "Giá trị nhỏ hơn mức tối thiểu"));
					}
					if (def.getMaxValue() != null && intVal > def.getMaxValue()) {
						errors.add(detail(fieldKey, "Giá trị lớn hơn mức tối đa"));
					}
				} catch (NumberFormatException e) {
					errors.add(detail(fieldKey, "Giá trị số nguyên không hợp lệ"));
				}
			}
			case "BOOLEAN" -> {
				if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
					errors.add(detail(fieldKey, "Giá trị boolean không hợp lệ"));
				}
			}
			case "ENUM" -> {
				List<String> options = JsonParseUtil.parseStringList(def.getEnumOptions());
				if (options != null && !options.isEmpty() && !options.contains(value)) {
					errors.add(detail(fieldKey, "Giá trị không nằm trong danh sách cho phép"));
				}
			}
			default -> {
				if (value.isBlank()) {
					errors.add(detail(fieldKey, "Giá trị không được để trống"));
				}
			}
		}
		return errors;
	}

	/** Số cơ thủ đang thực sự có mặt trong giải — nguồn duy nhất cho {@code bracket_size}. */
	private long countActiveParticipants(Long tournamentId) {
		return participantRepository.countByTournamentIdAndStatus(
				tournamentId, ParticipantStatus.ACTIVE.getValue());
	}

	/**
	 * DOUBLE_ELIMINATION luôn cắt về loại trực tiếp khi còn {@code se_phase_size} người
	 * (không còn "đánh loại kép tới vô địch" làm phương án dự phòng) — nên giá trị này bắt buộc
	 * phải hợp lệ ngay lúc lưu config, không âm thầm làm tròn/kẹp nữa: phải là lũy thừa của 2
	 * (2, 4, 8, 16...) và nhỏ hơn số người tối đa của giải, để luôn còn ít nhất 1 vòng đấu ở
	 * nhánh thắng/thua trước khi gộp lại. (Bốc thăm vẫn giữ lớp kẹp an toàn cho trường hợp số
	 * người đăng ký thực tế thấp hơn số tối đa — xem BracketGenerationServiceImpl.generateCutToSEDE.)
	 */
	private List<ConfigValidationDetailResponse> validateSePhaseSize(String value, Integer maxParticipants) {
		List<ConfigValidationDetailResponse> errors = new ArrayList<>();
		if (value == null || value.isBlank()) {
			return errors; // đã báo "Thiếu field bắt buộc" ở vòng lặp phía trên
		}
		int seSize;
		try {
			seSize = Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return errors; // đã báo "Giá trị số nguyên không hợp lệ" ở validateFieldValue
		}
		if (seSize < 2 || (seSize & (seSize - 1)) != 0) {
			errors.add(detail("se_phase_size", "Phải là lũy thừa của 2 (2, 4, 8, 16...)"));
			return errors;
		}
		if (maxParticipants != null && seSize >= maxParticipants) {
			errors.add(detail("se_phase_size",
					"Phải nhỏ hơn số người tối đa của giải (" + maxParticipants
							+ ") để còn ít nhất 1 vòng đấu nhánh thắng/thua trước khi gộp lại"));
		}
		return errors;
	}

	/**
	 * bracket_size chỉ được đồng bộ với maxParticipants cho thể thức Loại trực tiếp
	 * (SINGLE_ELIMINATION) — DOUBLE_ELIMINATION và các format khác không áp dụng.
	 * Đồng bộ ngay lúc tạo giải để 2 giá trị không lệch nhau ngay từ đầu — clamp theo
	 * min/max của field để không vi phạm validate khi owner mở lại màn config.
	 */
	private void syncBracketSizeFromMaxParticipants(Tournament tournament, String formatCode, Integer maxParticipants) {
		if (maxParticipants == null || !SINGLE_ELIMINATION_FORMAT_CODE.equals(formatCode)) return;
		formatConfigFieldRepository.findByFormatCodeAndFieldKey(formatCode, "bracket_size")
				.ifPresent(field -> {
					int clamped = clampToFieldRange(field, maxParticipants);
					configValueService.saveAll(tournament.getId(), Map.of("bracket_size", String.valueOf(clamped)));
				});
	}

	/** Chiều ngược lại: khi owner sửa bracket_size ở màn config (chỉ SINGLE_ELIMINATION), đồng bộ lại maxParticipants. */
	private void syncMaxParticipantsFromBracketSize(Tournament tournament, String bracketSizeValue) {
		if (bracketSizeValue == null || !SINGLE_ELIMINATION_FORMAT_CODE.equals(tournament.getFormat())) return;
		try {
			int bracketSize = Integer.parseInt(bracketSizeValue);
			if (!Objects.equals(tournament.getMaxParticipants(), bracketSize)) {
				tournament.setMaxParticipants(bracketSize);
				tournamentRepository.save(tournament);
			}
		} catch (NumberFormatException ignored) {
			// bracket_size đã được validateFieldValue kiểm tra là INT hợp lệ trước đó
		}
	}

	private int clampToFieldRange(FormatConfigField formatField, int value) {
		ConfigFieldDefinition def = formatField.getFieldDefinition();
		if (def == null) {
			def = configFieldRepository.findById(formatField.getFieldKey()).orElse(null);
		}
		if (def == null) return value;
		int result = value;
		if (def.getMinValue() != null && result < def.getMinValue()) result = def.getMinValue();
		if (def.getMaxValue() != null && result > def.getMaxValue()) result = def.getMaxValue();
		return result;
	}

	/**
	 * Validate 3 trường ngày của giải đấu khi tạo mới — cả 3 đều là input mới nên luôn check
	 * "không ở quá khứ".
	 */
	private void validateTournamentDates(Instant registrationDeadline, Instant startAt, Instant endAt) {
		validateTournamentDates(registrationDeadline, startAt, endAt, true, true, true);
	}

	/**
	 * Validate 3 trường ngày của giải đấu:
	 * 1. Field nào thực sự được sửa trong request thì mới check "không ở quá khứ" — nếu không, sửa
	 *    một field không liên quan (VD: tên giải) trên 1 tournament DRAFT tạo từ trước sẽ luôn bị
	 *    chặn nhầm vì hạn đăng ký cũ đã trôi qua "now" hiện tại.
	 * 2. startAt và endAt phải sau registrationDeadline — luôn kiểm tra (tính nhất quán nội tại,
	 *    không phụ thuộc field nào vừa đổi).
	 * 3. endAt >= startAt (cho phép cùng ngày) — luôn kiểm tra.
	 */
	private void validateTournamentDates(Instant registrationDeadline, Instant startAt, Instant endAt,
			boolean checkRegistrationDeadlinePast, boolean checkStartAtPast, boolean checkEndAtPast) {
		Instant now = Instant.now();
		List<ConfigValidationDetailResponse> errors = new ArrayList<>();

		if (registrationDeadline != null && checkRegistrationDeadlinePast && !registrationDeadline.isAfter(now)) {
			errors.add(detail("registrationDeadline", "Hạn đăng ký không được là thời điểm trong quá khứ"));
		}

		if (startAt != null) {
			if (checkStartAtPast && !startAt.isAfter(now)) {
				errors.add(detail("startAt", "Ngày bắt đầu thi đấu không được là thời điểm trong quá khứ"));
			} else if (registrationDeadline != null && !startAt.isAfter(registrationDeadline)) {
				errors.add(detail("startAt", "Ngày bắt đầu thi đấu phải sau hạn đăng ký"));
			}
		}

		if (endAt != null) {
			if (checkEndAtPast && !endAt.isAfter(now)) {
				errors.add(detail("endAt", "Ngày kết thúc không được là thời điểm trong quá khứ"));
			} else if (registrationDeadline != null && !endAt.isAfter(registrationDeadline)) {
				errors.add(detail("endAt", "Ngày kết thúc phải sau hạn đăng ký"));
			} else if (startAt != null && endAt.isBefore(startAt)) {
				errors.add(detail("endAt", "Ngày kết thúc phải từ ngày bắt đầu thi đấu trở đi"));
			}
		}

		if (!errors.isEmpty()) {
			throw new ConfigValidationException(ErrorCode.TOURNAMENT_DATE_INVALID, errors);
		}
	}

	private ConfigValidationDetailResponse detail(String fieldKey, String message) {
		return ConfigValidationDetailResponse.builder()
				.fieldKey(fieldKey)
				.field(fieldKey)
				.message(message)
				.build();
	}

	private Specification<Tournament> buildSpec(
			List<Long> branchIds, String status, String search, List<String> excludeStatuses) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (branchIds != null) {
				predicates.add(branchIds.isEmpty()
						? cb.disjunction()
						: root.get("branch").get("id").in(branchIds));
			}
			if (excludeStatuses != null && !excludeStatuses.isEmpty()) {
				predicates.add(root.get("status").in(excludeStatuses).not());
			}
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (search != null) {
				predicates.add(cb.like(
						cb.lower(root.get("name")),
						"%" + search.toLowerCase() + "%"));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	/** Filter bổ sung cho trang danh sách giải đấu của Owner/Manager — tách riêng để không đụng
	 * tới {@link #buildSpec} vốn cũng được dùng cho danh sách công khai (player-facing). */
	private Specification<Tournament> buildExtraFiltersSpec(
			String gameType, String participantType, Boolean isRegister, Long branchId) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (gameType != null) {
				predicates.add(cb.equal(root.get("gameType"), gameType));
			}
			if (participantType != null) {
				predicates.add(cb.equal(root.get("participantType"), participantType));
			}
			if (isRegister != null) {
				predicates.add(cb.equal(root.get("isRegister"), isRegister));
			}
			if (branchId != null) {
				predicates.add(cb.equal(root.get("branch").get("id"), branchId));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
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
		// Số người thực tế đang có trong giải, không đọc từ config value đã lưu.
		Integer bracketSize = (int) countActiveParticipants(tournamentId);
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

	/**
	 * Chuẩn hóa input ảnh từ FE để lưu DB: lưu MinIO object key (không lưu presigned URL có thời hạn).
	 * Chuỗi rỗng => null (xóa ảnh).
	 */
	private String normalizeImageForStorage(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return AvatarUrlResolver.normalizeForStorage(value, minioProperties.getBucket());
	}

	/**
	 * Từ object key đã lưu, tạo presigned URL mới cho client hiển thị ảnh.
	 */
	private String resolveImageForResponse(String storedValue) {
		return AvatarUrlResolver.resolveForResponse(
				storedValue, minioStorageService, minioProperties.getBucket());
	}

	/** Presign nhanh cho list — không StatObject MinIO. */
	private String resolveImageForList(String storedValue) {
		return AvatarUrlResolver.resolveForList(
				storedValue, minioStorageService, minioProperties.getBucket());
	}
}
