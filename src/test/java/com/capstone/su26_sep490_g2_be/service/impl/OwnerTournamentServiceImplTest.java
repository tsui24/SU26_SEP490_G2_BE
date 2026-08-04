package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.dto.request.CreateTournamentRequest;
import com.capstone.su26_sep490_g2_be.dto.request.PatchTournamentStatusRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SaveTournamentConfigRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateTournamentRequest;
import com.capstone.su26_sep490_g2_be.dto.response.CreateTournamentResponse;
import com.capstone.su26_sep490_g2_be.dto.response.OwnerFormatListResponse;
import com.capstone.su26_sep490_g2_be.dto.response.OwnerGameTypeListResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PatchTournamentStatusResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SaveTournamentConfigResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentConfigFormResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentConfigResolvedResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentConfigValidateResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentListItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UpdateTournamentResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.FormatConfigField;
import com.capstone.su26_sep490_g2_be.entity.FormatRaceToRule;
import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfig;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValue;
import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import com.capstone.su26_sep490_g2_be.entity.TournamentRaceToRule;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.BranchStatus;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.FieldSource;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.SeedingMethod;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.exception.ConfigValidationException;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.ConfigFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.FormatConfigFieldRepository;
import com.capstone.su26_sep490_g2_be.repository.FormatRaceToRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFormTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentConfigRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentFormatDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.AdminRegistrationFormService;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.service.RegistrationFormService;
import com.capstone.su26_sep490_g2_be.service.TournamentAuditService;
import com.capstone.su26_sep490_g2_be.service.TournamentConfigValueService;
import com.capstone.su26_sep490_g2_be.service.TournamentPublishedEvent;
import com.capstone.su26_sep490_g2_be.service.TournamentRaceToRuleService;
import com.capstone.su26_sep490_g2_be.service.TournamentResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link OwnerTournamentServiceImpl}.
 *
 * <p>Mirrors the <b>OwnerTournamentService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-16 (create and edit a tournament), UC-17 (format configuration
 * of a tournament) and UC-18 (the status lifecycle).
 *
 * <p>Owner and Manager reach every method here through the same service call and differ only in
 * whether ownership is enforced, so both roles are covered from one sheet.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · OwnerTournamentService — UC-16, UC-17, UC-18")
class OwnerTournamentServiceImplTest {

	@Mock TournamentRepository tournamentRepository;
	@Mock TournamentConfigRepository tournamentConfigRepository;
	@Mock TournamentFormatDefinitionRepository formatRepository;
	@Mock GameTypeDefinitionRepository gameTypeRepository;
	@Mock FormatConfigFieldRepository formatConfigFieldRepository;
	@Mock FormatRaceToRuleRepository formatRaceToRuleRepository;
	@Mock ConfigFieldDefinitionRepository configFieldRepository;
	@Mock UserRepository userRepository;
	@Mock TournamentConfigValueService configValueService;
	@Mock TournamentRaceToRuleService raceToRuleService;
	@Mock AdminRegistrationFormService adminRegistrationFormService;
	@Mock RegistrationFormService registrationFormService;
	@Mock RegistrationFormTemplateRepository registrationFormTemplateRepository;
	@Mock RegistrationRepository registrationRepository;
	@Mock ParticipantRepository participantRepository;
	@Mock MatchRepository matchRepository;
	@Mock MinioStorageService minioStorageService;
	@Mock MinioProperties minioProperties;
	@Mock BranchRepository branchRepository;
	@Mock BranchAccessService branchAccessService;
	@Mock TournamentAuditService tournamentAuditService;
	@Mock TournamentResultService tournamentResultService;
	@Mock ApplicationEventPublisher eventPublisher;
	@Mock MailContextBuilder mailContextBuilder;

	@InjectMocks OwnerTournamentServiceImpl service;

	private static final Long TOURNAMENT_ID = 300L;
	private static final Long OWNER_ID = 7L;
	private static final Long MANAGER_ID = 8L;
	private static final Long BRANCH_ID = 2L;
	private static final String FORMAT = "SINGLE_ELIMINATION";
	private static final String GAME_TYPE = "EIGHT_BALL";

	private static final Instant DEADLINE = Instant.now().plus(10, ChronoUnit.DAYS);
	private static final Instant START_AT = Instant.now().plus(20, ChronoUnit.DAYS);
	private static final Instant END_AT = Instant.now().plus(25, ChronoUnit.DAYS);

	@BeforeEach
	void wireSharedInfrastructure() {
		lenient().when(minioProperties.getBucket()).thenReturn("btms");
	}

	// ══════════════════════════ fixtures ══════════════════════════

	private static User user(Long id, String roleCode) {
		return User.builder().id(id).email("actor@btms.vn")
				.role(Role.builder().id(1L).code(roleCode).name(roleCode).build())
				.build();
	}

	private static Branch branch(Long ownerId, BranchStatus status) {
		return Branch.builder().id(BRANCH_ID).name("Chi nhánh Quận 1")
				.address("12 Nguyễn Huệ").phone("0900000000").status(status)
				.owner(ownerId != null ? User.builder().id(ownerId).build() : null)
				.build();
	}

	private static Tournament tournament(String status) {
		return Tournament.builder()
				.id(TOURNAMENT_ID).name("Summer Open 2026")
				.gameType(GAME_TYPE).format(FORMAT).participantType("SINGLE")
				.status(status).maxParticipants(16).tableCount(4)
				.entryFee(BigDecimal.TEN)
				.registrationDeadline(DEADLINE).startAt(START_AT).endAt(END_AT)
				.isRegister(false).isShowTournament(true)
				.branch(branch(OWNER_ID, BranchStatus.ACTIVE))
				// the venue is snapshotted onto the tournament when the branch is chosen
				.venueName("Chi nhánh Quận 1").venueAddress("12 Nguyễn Huệ")
				.build();
	}

	private static CreateTournamentRequest createRequest() {
		CreateTournamentRequest request = new CreateTournamentRequest();
		request.setName("Summer Open 2026");
		request.setDescription("Giải mùa hè");
		request.setGameType(GAME_TYPE);
		request.setFormat(FORMAT);
		request.setParticipantType("SINGLE");
		request.setMaxParticipants(16);
		request.setTableCount(4);
		request.setEntryFee(new BigDecimal("100000"));
		request.setRegistrationDeadline(DEADLINE);
		request.setStartAt(START_AT);
		request.setEndAt(END_AT);
		request.setIsRegister(false);
		request.setIsShowTournament(true);
		request.setBranchId(BRANCH_ID);
		return request;
	}

	/** The update DTO carries no builder, so tests describe the patch as a mutation instead. */
	private static UpdateTournamentRequest updateWith(java.util.function.Consumer<UpdateTournamentRequest> patch) {
		UpdateTournamentRequest request = new UpdateTournamentRequest();
		patch.accept(request);
		return request;
	}

	private static PatchTournamentStatusRequest statusRequest(String status) {
		PatchTournamentStatusRequest request = new PatchTournamentStatusRequest();
		request.setStatus(status);
		return request;
	}

	private static FormatConfigField configField(String key, String dataType, boolean required,
												 String defaultValue, Integer min, Integer max) {
		return FormatConfigField.builder()
				.id(1L).formatCode(FORMAT).fieldKey(key)
				.defaultValue(defaultValue).isRequired(required).isVisibleToOwner(true)
				.fieldDefinition(ConfigFieldDefinition.builder()
						.fieldKey(key).label(key).dataType(dataType)
						.minValue(min).maxValue(max).uiComponent("INPUT").build())
				.build();
	}

	/** The format exists, is active and carries at least one config field and one race-to rule. */
	private void givenReadyFormat() {
		lenient().when(formatRepository.findById(FORMAT)).thenReturn(Optional.of(
				TournamentFormatDefinition.builder().code(FORMAT).name("Loại trực tiếp")
						.description("Một lần thua").isActive(true).sortOrder(1).build()));
		lenient().when(formatConfigFieldRepository.countByFormatCode(FORMAT)).thenReturn(1L);
		lenient().when(formatRaceToRuleRepository.countByFormatCode(FORMAT)).thenReturn(1L);
	}

	private void givenActiveGameType() {
		lenient().when(gameTypeRepository.findById(GAME_TYPE)).thenReturn(Optional.of(
				GameTypeDefinition.builder().code(GAME_TYPE).name("8 bi").isActive(true)
						.defaultRaceTo(7).sortOrder(1).build()));
	}

	/** A config row whose required fields all resolve, so isConfigComplete() reports true. */
	private void givenCompleteConfig() {
		lenient().when(tournamentConfigRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				TournamentConfig.builder().tournamentId(TOURNAMENT_ID).formatCode(FORMAT)
						.seedingMethod(SeedingMethod.RANDOM.name()).build()));
		lenient().when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("bracket_size", "INT", true, "16", 4, 128)));
		lenient().when(configValueService.getByTournamentAndField(eq(TOURNAMENT_ID), anyString()))
				.thenReturn(Optional.empty());
		lenient().when(formatRaceToRuleRepository.countByFormatCode(FORMAT)).thenReturn(1L);
		lenient().when(raceToRuleService.resolveRaceTo(eq(TOURNAMENT_ID), eq(FORMAT), anyString())).thenReturn(9);
	}

	private void givenOwnerCanUseBranch() {
		lenient().when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		lenient().when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(OWNER_ID, BranchStatus.ACTIVE)));
		lenient().when(tournamentRepository.save(any(Tournament.class))).thenAnswer(inv -> {
			Tournament t = inv.getArgument(0);
			if (t.getId() == null) t.setId(TOURNAMENT_ID);
			return t;
		});
	}

	// ══════════════════════════ createTournament ══════════════════════════

	@Test
	@DisplayName("TC-001 · A new tournament starts as a draft with a random-seeding config")
	void TC001_createTournament_happyPath() {
		givenActiveGameType();
		givenReadyFormat();
		givenOwnerCanUseBranch();

		CreateTournamentResponse response = service.createTournament(OWNER_ID, createRequest());

		assertEquals(TournamentStatus.DRAFT.getValue(), response.getStatus());
		assertEquals(FORMAT, response.getFormat());
		assertFalse(response.getConfigComplete(), "a fresh tournament has not been configured yet");
		ArgumentCaptor<TournamentConfig> config = ArgumentCaptor.forClass(TournamentConfig.class);
		verify(tournamentConfigRepository).save(config.capture());
		assertEquals(SeedingMethod.RANDOM.name(), config.getValue().getSeedingMethod());
		assertEquals(FORMAT, config.getValue().getFormatCode());
	}

	@Test
	@DisplayName("TC-002 · Creating a tournament under an account that no longer exists")
	void TC002_createTournament_creatorNotFound() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(OWNER_ID, createRequest()));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-003 · Creating a tournament on a game type that is not in the catalogue")
	void TC003_createTournament_unknownGameType() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		when(gameTypeRepository.findById(GAME_TYPE)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(OWNER_ID, createRequest()));

		assertEquals(ErrorCode.GAME_TYPE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-004 · A game type the admin has retired cannot be chosen")
	void TC004_createTournament_inactiveGameType() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		when(gameTypeRepository.findById(GAME_TYPE)).thenReturn(Optional.of(
				GameTypeDefinition.builder().code(GAME_TYPE).name("8 bi").isActive(false).build()));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(OWNER_ID, createRequest()));

		assertEquals(ErrorCode.GAME_TYPE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-005 · Creating a tournament on a format that does not exist")
	void TC005_createTournament_unknownFormat() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenActiveGameType();
		when(formatRepository.findById(FORMAT)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(OWNER_ID, createRequest()));

		assertEquals(ErrorCode.FORMAT_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-006 · A format the admin has retired cannot be chosen")
	void TC006_createTournament_inactiveFormat() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenActiveGameType();
		when(formatRepository.findById(FORMAT)).thenReturn(Optional.of(
				TournamentFormatDefinition.builder().code(FORMAT).name("Loại trực tiếp").isActive(false).build()));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(OWNER_ID, createRequest()));

		assertEquals(ErrorCode.FORMAT_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-007 · A format the admin has not finished setting up cannot be chosen")
	void TC007_createTournament_formatNotReady() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenActiveGameType();
		when(formatRepository.findById(FORMAT)).thenReturn(Optional.of(
				TournamentFormatDefinition.builder().code(FORMAT).name("Loại trực tiếp").isActive(true).build()));
		when(formatConfigFieldRepository.countByFormatCode(FORMAT)).thenReturn(0L);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(OWNER_ID, createRequest()));

		assertEquals(ErrorCode.FORMAT_NOT_READY, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-008 · Team events are not supported yet")
	void TC008_createTournament_teamParticipantTypeRejected() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenActiveGameType();
		givenReadyFormat();
		CreateTournamentRequest request = createRequest();
		request.setParticipantType("TEAM");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(OWNER_ID, request));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-009 · A registration deadline in the past is refused")
	void TC009_createTournament_deadlineInThePast() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenActiveGameType();
		givenReadyFormat();
		CreateTournamentRequest request = createRequest();
		request.setRegistrationDeadline(Instant.now().minus(1, ChronoUnit.DAYS));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.createTournament(OWNER_ID, request));

		assertEquals(ErrorCode.TOURNAMENT_DATE_INVALID, ex.getErrorCode());
		assertTrue(ex.getDetails().stream().anyMatch(e -> "registrationDeadline".equals(e.getFieldKey())));
	}

	@Test
	@DisplayName("TC-010 · Play cannot start before registration closes")
	void TC010_createTournament_startBeforeDeadline() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenActiveGameType();
		givenReadyFormat();
		CreateTournamentRequest request = createRequest();
		request.setStartAt(DEADLINE.minus(1, ChronoUnit.DAYS));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.createTournament(OWNER_ID, request));

		assertEquals(ErrorCode.TOURNAMENT_DATE_INVALID, ex.getErrorCode());
		assertTrue(ex.getDetails().stream().anyMatch(e -> "startAt".equals(e.getFieldKey())));
	}

	@Test
	@DisplayName("TC-011 · A tournament cannot end before it starts")
	void TC011_createTournament_endBeforeStart() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenActiveGameType();
		givenReadyFormat();
		CreateTournamentRequest request = createRequest();
		request.setEndAt(START_AT.minus(1, ChronoUnit.DAYS));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.createTournament(OWNER_ID, request));

		assertEquals(ErrorCode.TOURNAMENT_DATE_INVALID, ex.getErrorCode());
		assertTrue(ex.getDetails().stream().anyMatch(e -> "endAt".equals(e.getFieldKey())));
	}

	@Test
	@DisplayName("TC-012 · Creating a tournament at a branch that does not exist")
	void TC012_createTournament_branchNotFound() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenActiveGameType();
		givenReadyFormat();
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(OWNER_ID, createRequest()));

		assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-013 · An owner may only use branches they own")
	void TC013_createTournament_branchOfAnotherOwner() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenActiveGameType();
		givenReadyFormat();
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(999L, BranchStatus.ACTIVE)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(OWNER_ID, createRequest()));

		assertEquals(ErrorCode.BRANCH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-014 · A closed branch cannot host a new tournament")
	void TC014_createTournament_inactiveBranch() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenActiveGameType();
		givenReadyFormat();
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(OWNER_ID, BranchStatus.INACTIVE)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(OWNER_ID, createRequest()));

		assertEquals(ErrorCode.BRANCH_INACTIVE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-015 · A manager may only create at the branches granted to them")
	void TC015_createTournament_managerWithoutBranchGrant() {
		when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(user(MANAGER_ID, "MANAGER")));
		givenActiveGameType();
		givenReadyFormat();
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(OWNER_ID, BranchStatus.ACTIVE)));
		when(branchAccessService.canManagerCreateTournamentAt(any(User.class), eq(BRANCH_ID))).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(MANAGER_ID, createRequest()));

		assertEquals(ErrorCode.BRANCH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-016 · Roles other than owner and manager cannot create tournaments")
	void TC016_createTournament_staffRefused() {
		when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(user(MANAGER_ID, "STAFF")));
		givenActiveGameType();
		givenReadyFormat();
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(OWNER_ID, BranchStatus.ACTIVE)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTournament(MANAGER_ID, createRequest()));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-017 · A tournament always has at least one table and a fee")
	void TC017_createTournament_defaultsForTablesAndFee() {
		givenActiveGameType();
		givenReadyFormat();
		givenOwnerCanUseBranch();
		CreateTournamentRequest request = createRequest();
		request.setTableCount(0);
		request.setEntryFee(null);

		CreateTournamentResponse response = service.createTournament(OWNER_ID, request);

		assertEquals(1, response.getTableCount(), "zero tables would make scheduling impossible");
		ArgumentCaptor<Tournament> saved = ArgumentCaptor.forClass(Tournament.class);
		verify(tournamentRepository).save(saved.capture());
		assertEquals(BigDecimal.ZERO, saved.getValue().getEntryFee());
	}

	@Test
	@DisplayName("TC-018 · A tournament that collects no registrations keeps no form template")
	void TC018_createTournament_noRegistrationDropsTemplate() {
		givenActiveGameType();
		givenReadyFormat();
		givenOwnerCanUseBranch();
		CreateTournamentRequest request = createRequest();
		request.setIsRegister(false);
		request.setRegistrationFormTemplateId(55L);

		CreateTournamentResponse response = service.createTournament(OWNER_ID, request);

		assertNull(response.getRegistrationFormTemplateId());
		verify(registrationFormService).validateRegistrationSettings(false, 55L);
	}

	@Test
	@DisplayName("TC-019 · Bracket size follows the maximum entry count for a knockout tournament")
	void TC019_createTournament_syncsBracketSize() {
		givenActiveGameType();
		givenReadyFormat();
		givenOwnerCanUseBranch();
		when(formatConfigFieldRepository.findByFormatCodeAndFieldKey(FORMAT, "bracket_size"))
				.thenReturn(Optional.of(configField("bracket_size", "INT", true, "16", 4, 128)));

		service.createTournament(OWNER_ID, createRequest());

		verify(configValueService).saveAll(TOURNAMENT_ID, Map.of("bracket_size", "16"));
	}

	@Test
	@DisplayName("TC-020 · A maximum entry count outside the allowed bracket range is clamped")
	void TC020_createTournament_clampsBracketSize() {
		givenActiveGameType();
		givenReadyFormat();
		givenOwnerCanUseBranch();
		when(formatConfigFieldRepository.findByFormatCodeAndFieldKey(FORMAT, "bracket_size"))
				.thenReturn(Optional.of(configField("bracket_size", "INT", true, "16", 4, 128)));
		CreateTournamentRequest request = createRequest();
		request.setMaxParticipants(256);

		service.createTournament(OWNER_ID, request);

		verify(configValueService).saveAll(TOURNAMENT_ID, Map.of("bracket_size", "128"));
	}

	// ══════════════════════════ updateTournament ══════════════════════════

	private void givenLoadableTournament(Tournament t) {
		lenient().when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		lenient().when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		lenient().when(branchAccessService.canActorAccessBranch(any(User.class), eq(BRANCH_ID))).thenReturn(true);
	}

	@Test
	@DisplayName("TC-021 · Only a draft tournament may be edited")
	void TC021_updateTournament_lockedOnceOpen() {
		givenLoadableTournament(tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue()));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.updateTournament(
				OWNER_ID, TOURNAMENT_ID, updateWith(r -> r.setName("Đổi tên")), true));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-022 · Editing one field leaves the others alone")
	void TC022_updateTournament_partialUpdate() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();

		UpdateTournamentResponse response = service.updateTournament(
				OWNER_ID, TOURNAMENT_ID, updateWith(r -> r.setName("Tên mới")), true);

		assertEquals("Tên mới", t.getName());
		assertEquals(16, t.getMaxParticipants(), "a field absent from the request must not be reset");
		assertEquals(TournamentStatus.DRAFT.getValue(), response.getStatus());
	}

	@Test
	@DisplayName("TC-023 · Changing the format discards the configuration built for the old one")
	void TC023_updateTournament_formatChangeClearsConfig() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		lenient().when(formatRepository.findById("DOUBLE_ELIMINATION")).thenReturn(Optional.of(
				TournamentFormatDefinition.builder().code("DOUBLE_ELIMINATION").name("Hai lần thua")
						.isActive(true).build()));
		when(formatConfigFieldRepository.countByFormatCode("DOUBLE_ELIMINATION")).thenReturn(1L);
		when(formatRaceToRuleRepository.countByFormatCode("DOUBLE_ELIMINATION")).thenReturn(1L);

		service.updateTournament(OWNER_ID, TOURNAMENT_ID,
				updateWith(r -> r.setFormat("DOUBLE_ELIMINATION")), true);

		assertEquals("DOUBLE_ELIMINATION", t.getFormat());
		verify(configValueService).deleteByTournament(TOURNAMENT_ID);
		verify(raceToRuleService).deleteByTournament(TOURNAMENT_ID);
		ArgumentCaptor<TournamentConfig> config = ArgumentCaptor.forClass(TournamentConfig.class);
		verify(tournamentConfigRepository).save(config.capture());
		assertNull(config.getValue().getConfigSnapshotJson(), "the old snapshot describes a format no longer in use");
	}

	@Test
	@DisplayName("TC-024 · Turning registration off clears the chosen form template")
	void TC024_updateTournament_registrationOffClearsTemplate() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		t.setIsRegister(true);
		t.setRegistrationFormTemplateId(55L);
		givenLoadableTournament(t);
		givenCompleteConfig();

		service.updateTournament(OWNER_ID, TOURNAMENT_ID,
				updateWith(r -> r.setIsRegister(false)), true);

		assertNull(t.getRegistrationFormTemplateId());
		verify(registrationFormService).validateRegistrationSettings(false, null);
	}

	@Test
	@DisplayName("TC-025 · A stale deadline does not block an edit that leaves the dates alone")
	void TC025_updateTournament_pastDateNotReCheckedWhenUntouched() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		t.setRegistrationDeadline(Instant.now().minus(5, ChronoUnit.DAYS));
		t.setStartAt(Instant.now().minus(3, ChronoUnit.DAYS));
		t.setEndAt(Instant.now().minus(1, ChronoUnit.DAYS));
		givenLoadableTournament(t);
		givenCompleteConfig();

		UpdateTournamentResponse response = service.updateTournament(
				OWNER_ID, TOURNAMENT_ID, updateWith(r -> r.setName("Tên mới")), true);

		assertEquals("Tên mới", t.getName());
		assertNotNull(response);
	}

	@Test
	@DisplayName("TC-026 · Moving a tournament to another branch is re-checked against the actor")
	void TC026_updateTournament_branchChangeRevalidated() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		when(branchRepository.findById(9L)).thenReturn(Optional.of(
				Branch.builder().id(9L).name("Chi nhánh Quận 7").address("5 Nguyễn Văn Linh")
						.status(BranchStatus.ACTIVE).owner(User.builder().id(OWNER_ID).build()).build()));

		service.updateTournament(OWNER_ID, TOURNAMENT_ID,
				updateWith(r -> r.setBranchId(9L)), true);

		assertEquals(9L, t.getBranch().getId());
		assertEquals("Chi nhánh Quận 7", t.getVenueName(), "the venue is snapshotted onto the tournament");
		assertEquals("5 Nguyễn Văn Linh", t.getVenueAddress());
	}

	// ══════════════════════════ read models ══════════════════════════

	@Test
	@DisplayName("TC-027 · Tournament detail reports how many places are left")
	void TC027_getTournament_remainingSlots() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		givenReadyFormat();
		when(participantRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(6L);

		TournamentDetailResponse response = service.getTournament(OWNER_ID, TOURNAMENT_ID, true);

		assertEquals(6L, response.getApprovedCount());
		assertEquals(10, response.getRemainingSlots());
		assertEquals("Loại trực tiếp", response.getFormatName());
		assertEquals("Chi nhánh Quận 1", response.getVenue().getName());
	}

	@Test
	@DisplayName("TC-028 · An over-subscribed tournament reports no places left rather than a negative number")
	void TC028_getTournament_remainingSlotsNeverNegative() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		when(participantRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(20L);

		TournamentDetailResponse response = service.getTournament(OWNER_ID, TOURNAMENT_ID, true);

		assertEquals(0, response.getRemainingSlots());
	}

	@Test
	@DisplayName("TC-029 · Reading a tournament that does not exist")
	void TC029_getTournament_notFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getTournament(OWNER_ID, TOURNAMENT_ID, true));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-030 · A manager may not read a tournament outside their branches")
	void TC030_getTournament_branchAccessDenied() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament(TournamentStatus.DRAFT.getValue())));
		when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(user(MANAGER_ID, "MANAGER")));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(BRANCH_ID))).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getTournament(MANAGER_ID, TOURNAMENT_ID, true));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-031 · An anonymous caller cannot reach an ownership-enforced read")
	void TC031_getTournament_nullActorDenied() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament(TournamentStatus.DRAFT.getValue())));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getTournament(null, TOURNAMENT_ID, true));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
		verify(userRepository, never()).findById(any());
	}

	@Test
	@DisplayName("TC-032 · A draft tournament is invisible to players")
	void TC032_getPlayerTournamentDetail_draftHidden() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament(TournamentStatus.DRAFT.getValue())));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getPlayerTournamentDetail(TOURNAMENT_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-033 · A cancelled tournament is invisible to players")
	void TC033_getPlayerTournamentDetail_cancelledHidden() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(tournament(TournamentStatus.CANCELLED.getValue())));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getPlayerTournamentDetail(TOURNAMENT_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-034 · A tournament the organiser has unpublished is invisible to players")
	void TC034_getPlayerTournamentDetail_unpublishedHidden() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue());
		t.setIsShowTournament(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getPlayerTournamentDetail(TOURNAMENT_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-035 · A published tournament is readable without signing in")
	void TC035_getPlayerTournamentDetail_visible() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		when(participantRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(4L);

		TournamentDetailResponse response = service.getPlayerTournamentDetail(TOURNAMENT_ID);

		assertEquals(TOURNAMENT_ID, response.getId());
		assertEquals(12, response.getRemainingSlots());
		verify(userRepository, never()).findById(any());
	}

	// ══════════════════════════ listing ══════════════════════════

	@SuppressWarnings("unchecked")
	private void givenTournamentPage(List<Tournament> content) {
		lenient().when(tournamentRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenAnswer(inv -> new PageImpl<>(content, inv.getArgument(1), content.size()));
	}

	@Test
	@DisplayName("TC-036 · An owner sees the tournaments of the whole chain")
	void TC036_listTournaments_ownerSeesEverything() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "OWNER")));
		givenTournamentPage(List.of(tournament(TournamentStatus.DRAFT.getValue())));
		when(formatRepository.findAllById(any())).thenReturn(List.of(
				TournamentFormatDefinition.builder().code(FORMAT).name("Loại trực tiếp").build()));
		when(participantRepository.countGroupedByTournamentIdInAndStatus(any(), anyString())).thenReturn(List.of());
		when(tournamentConfigRepository.findAllById(any())).thenReturn(List.of(
				TournamentConfig.builder().tournamentId(TOURNAMENT_ID).formatCode(FORMAT)
						.seedingMethod(SeedingMethod.RANDOM.name()).build()));
		when(formatConfigFieldRepository.findByFormatCodeInOrderByIdAsc(any())).thenReturn(List.of());
		when(formatRaceToRuleRepository.countByFormatCode(FORMAT)).thenReturn(1L);
		when(configValueService.getByTournamentIds(any())).thenReturn(List.of());

		PageResponse<TournamentListItemResponse> page = service.listTournaments(OWNER_ID, true, null, null, 0, 10);

		assertEquals(1, page.getContent().size());
		assertEquals("Loại trực tiếp", page.getContent().get(0).getFormatName());
		assertTrue(page.getContent().get(0).getConfigComplete());
		verify(branchAccessService, never()).getAccessibleBranchIds(any());
	}

	@Test
	@DisplayName("TC-037 · A manager only sees the branches granted to them")
	void TC037_listTournaments_managerFiltered() {
		when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(user(MANAGER_ID, "MANAGER")));
		when(branchAccessService.getAccessibleBranchIds(any(User.class))).thenReturn(List.of(BRANCH_ID));
		givenTournamentPage(List.of());

		PageResponse<TournamentListItemResponse> page = service.listTournaments(MANAGER_ID, true, null, null, 0, 10);

		assertTrue(page.getContent().isEmpty());
		verify(branchAccessService).getAccessibleBranchIds(any(User.class));
	}

	@Test
	@DisplayName("TC-038 · Roles other than owner and manager cannot list tournaments")
	void TC038_listTournaments_staffRefused() {
		when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(user(MANAGER_ID, "STAFF")));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.listTournaments(MANAGER_ID, true, null, null, 0, 10));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-039 · Nonsense paging values fall back to the first page of ten")
	void TC039_listTournaments_pagingDefaults() {
		givenTournamentPage(List.of());

		service.listTournaments(OWNER_ID, false, "  ", "  ", -5, 0);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(tournamentRepository).findAll(any(Specification.class), pageable.capture());
		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(10, pageable.getValue().getPageSize());
	}

	@Test
	@DisplayName("TC-040 · The player-facing list never reports configuration state")
	void TC040_listPlayerTournaments_noConfigFlag() {
		givenTournamentPage(List.of(tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue())));
		when(formatRepository.findAllById(any())).thenReturn(List.of(
				TournamentFormatDefinition.builder().code(FORMAT).name("Loại trực tiếp").build()));
		when(participantRepository.countGroupedByTournamentIdInAndStatus(any(), anyString())).thenReturn(List.of());

		PageResponse<TournamentListItemResponse> page = service.listPlayerTournaments(null, null, 0, 10);

		assertEquals(1, page.getContent().size());
		assertNull(page.getContent().get(0).getConfigComplete());
		verify(tournamentConfigRepository, never()).findAllById(any());
	}

	@Test
	@DisplayName("TC-041 · Only formats the admin has finished setting up are offered")
	void TC041_listFormats_onlyReadyOnes() {
		when(formatRepository.findByIsActiveTrueOrderByCreatedAtAsc()).thenReturn(List.of(
				TournamentFormatDefinition.builder().code(FORMAT).name("Loại trực tiếp").sortOrder(1).build(),
				TournamentFormatDefinition.builder().code("HALF_BAKED").name("Chưa xong").sortOrder(2).build()));
		when(formatConfigFieldRepository.countByFormatCode(FORMAT)).thenReturn(1L);
		when(formatRaceToRuleRepository.countByFormatCode(FORMAT)).thenReturn(1L);
		when(formatConfigFieldRepository.countByFormatCode("HALF_BAKED")).thenReturn(0L);

		OwnerFormatListResponse response = service.listFormats();

		assertEquals(1, response.getTotal());
		assertEquals(FORMAT, response.getItems().get(0).getCode());
	}

	@Test
	@DisplayName("TC-042 · Game types are offered in the order the admin arranged them")
	void TC042_listGameTypes_sortedByAdminOrder() {
		when(gameTypeRepository.findByIsActiveTrueOrderByCreatedAtAsc()).thenReturn(List.of(
				GameTypeDefinition.builder().code("NINE_BALL").name("9 bi").sortOrder(2).defaultRaceTo(9).build(),
				GameTypeDefinition.builder().code(GAME_TYPE).name("8 bi").sortOrder(1).defaultRaceTo(7).build()));

		OwnerGameTypeListResponse response = service.listGameTypes();

		assertEquals(2, response.getTotal());
		assertEquals(GAME_TYPE, response.getItems().get(0).getCode());
		assertEquals("NINE_BALL", response.getItems().get(1).getCode());
	}

	// ══════════════════════════ saveConfig ══════════════════════════

	private static SaveTournamentConfigRequest configRequest(String seedingMethod, Integer seedCount,
															 Map<String, String> fields) {
		SaveTournamentConfigRequest request = new SaveTournamentConfigRequest();
		request.setSeedingMethod(seedingMethod);
		request.setSeedCount(seedCount);
		List<SaveTournamentConfigRequest.ConfigFieldValueItem> items = new ArrayList<>();
		fields.forEach((key, value) -> {
			SaveTournamentConfigRequest.ConfigFieldValueItem item =
					new SaveTournamentConfigRequest.ConfigFieldValueItem();
			item.setFieldKey(key);
			item.setValue(value);
			items.add(item);
		});
		request.setFields(items);
		return request;
	}

	private static SaveTournamentConfigRequest.RaceToOverrideItem raceToOverride(String roundKey, int raceTo) {
		SaveTournamentConfigRequest.RaceToOverrideItem item = new SaveTournamentConfigRequest.RaceToOverrideItem();
		item.setRoundKey(roundKey);
		item.setRaceTo(raceTo);
		return item;
	}

	@Test
	@DisplayName("TC-043 · A seeding method outside the three supported ones is refused")
	void TC043_saveConfig_invalidSeedingMethod() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID,
						configRequest("BY_COIN_TOSS", null, Map.of()), true));

		assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED, ex.getErrorCode());
		assertEquals("seedingMethod", ex.getDetails().get(0).getFieldKey());
	}

	@Test
	@DisplayName("TC-044 · Choosing manual seeding without saying how many seeds")
	void TC044_saveConfig_seedCountMissing() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID,
						configRequest(SeedingMethod.MANUAL.name(), null, Map.of()), true));

		assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED, ex.getErrorCode());
		assertEquals("seedCount", ex.getDetails().get(0).getFieldKey());
	}

	@Test
	@DisplayName("TC-045 · There cannot be more seeds than places in the tournament")
	void TC045_saveConfig_seedCountAboveCapacity() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID,
						configRequest(SeedingMethod.MANUAL.name(), 32, Map.of()), true));

		assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED, ex.getErrorCode());
		assertEquals("seedCount", ex.getDetails().get(0).getFieldKey());
	}

	@Test
	@DisplayName("TC-046 · A field that belongs to another format is refused")
	void TC046_saveConfig_fieldNotInFormat() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("bracket_size", "INT", true, "16", 4, 128)));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID,
						configRequest(SeedingMethod.RANDOM.name(), null, Map.of("group_count", "4")), true));

		assertEquals(ErrorCode.INVALID_FIELD_FOR_FORMAT, ex.getErrorCode());
		assertEquals("group_count", ex.getDetails().get(0).getFieldKey());
	}

	@Test
	@DisplayName("TC-047 · A required field with no value and no default is reported as missing")
	void TC047_saveConfig_requiredFieldMissing() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("bracket_size", "INT", true, null, 4, 128)));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID,
						configRequest(SeedingMethod.RANDOM.name(), null, Map.of()), true));

		assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED, ex.getErrorCode());
		assertEquals("Thiếu field bắt buộc", ex.getDetails().get(0).getMessage());
	}

	@Test
	@DisplayName("TC-048 · A required field left blank falls back to the admin default")
	void TC048_saveConfig_blankRequiredFieldUsesDefault() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("bracket_size", "INT", true, "16", 4, 128)));

		service.saveConfig(OWNER_ID, TOURNAMENT_ID,
				configRequest(SeedingMethod.RANDOM.name(), null, Map.of("bracket_size", "   ")), true);

		verify(configValueService).saveAll(TOURNAMENT_ID, Map.of("bracket_size", "16"));
	}

	@Test
	@DisplayName("TC-049 · A whole number outside the admin range is refused")
	void TC049_saveConfig_intOutOfRange() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("bracket_size", "INT", true, "16", 4, 128)));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID,
						configRequest(SeedingMethod.RANDOM.name(), null, Map.of("bracket_size", "256")), true));

		assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED, ex.getErrorCode());
		assertEquals("Giá trị lớn hơn mức tối đa", ex.getDetails().get(0).getMessage());
	}

	@Test
	@DisplayName("TC-050 · A value that is not a whole number at all is refused")
	void TC050_saveConfig_intNotANumber() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("bracket_size", "INT", true, "16", 4, 128)));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID,
						configRequest(SeedingMethod.RANDOM.name(), null, Map.of("bracket_size", "mười sáu")), true));

		assertEquals("Giá trị số nguyên không hợp lệ", ex.getDetails().get(0).getMessage());
	}

	@Test
	@DisplayName("TC-051 · A yes/no field only accepts true or false")
	void TC051_saveConfig_invalidBoolean() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("third_place_match", "BOOLEAN", true, "true", null, null)));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID,
						configRequest(SeedingMethod.RANDOM.name(), null, Map.of("third_place_match", "có")), true));

		assertEquals("Giá trị boolean không hợp lệ", ex.getDetails().get(0).getMessage());
	}

	@Test
	@DisplayName("TC-052 · A choice field only accepts one of its listed options")
	void TC052_saveConfig_valueOutsideEnum() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		FormatConfigField field = configField("break_rule", "ENUM", true, "ALTERNATE", null, null);
		field.getFieldDefinition().setEnumOptions("[\"ALTERNATE\",\"WINNER_BREAKS\"]");
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of(field));

		ConfigValidationException ex = assertThrows(ConfigValidationException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID,
						configRequest(SeedingMethod.RANDOM.name(), null, Map.of("break_rule", "LOSER_BREAKS")), true));

		assertEquals("Giá trị không nằm trong danh sách cho phép", ex.getDetails().get(0).getMessage());
	}

	@Test
	@DisplayName("TC-053 · Saving a complete configuration snapshots it")
	void TC053_saveConfig_happyPathSnapshots() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("bracket_size", "INT", true, "16", 4, 128)));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of(
				FormatRaceToRule.builder().id(1L).formatCode(FORMAT).roundKey("final")
						.label("Chung kết").bracketPhase("KNOCKOUT").raceTo(9).build()));

		SaveTournamentConfigResponse response = service.saveConfig(OWNER_ID, TOURNAMENT_ID,
				configRequest(SeedingMethod.RANDOM.name(), null, Map.of("bracket_size", "16")), true);

		assertTrue(response.getIsConfigComplete());
		ArgumentCaptor<TournamentConfig> config = ArgumentCaptor.forClass(TournamentConfig.class);
		verify(tournamentConfigRepository).save(config.capture());
		assertNotNull(config.getValue().getConfigSnapshotJson(),
				"a complete configuration is frozen so a later admin edit cannot change a running tournament");
	}

	@Test
	@DisplayName("TC-054 · Random seeding stores no seed count")
	void TC054_saveConfig_randomClearsSeedCount() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of());

		SaveTournamentConfigResponse response = service.saveConfig(OWNER_ID, TOURNAMENT_ID,
				configRequest(SeedingMethod.RANDOM.name(), 8, Map.of()), true);

		assertNull(response.getSeedCount(), "a seed count is meaningless when the draw is random");
	}

	@Test
	@DisplayName("TC-055 · The configuration is locked once registration has closed")
	void TC055_saveConfig_lockedAfterRegistrationCloses() {
		givenLoadableTournament(tournament(TournamentStatus.REGISTRATION_CLOSED.getValue()));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID,
						configRequest(SeedingMethod.RANDOM.name(), null, Map.of()), true));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-056 · The configuration stays editable while registration is open")
	void TC056_saveConfig_editableWhileRegistrationOpen() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of());

		SaveTournamentConfigResponse response = service.saveConfig(OWNER_ID, TOURNAMENT_ID,
				configRequest(SeedingMethod.RANDOM.name(), null, Map.of()), true);

		assertEquals(TOURNAMENT_ID, response.getTournamentId());
	}

	@Test
	@DisplayName("TC-057 · A race-to that differs from the format default is stored as an override")
	void TC057_saveConfig_raceToOverrideStored() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of());
		when(formatRaceToRuleRepository.findByFormatCodeAndRoundKey(FORMAT, "final")).thenReturn(Optional.of(
				FormatRaceToRule.builder().id(1L).formatCode(FORMAT).roundKey("final")
						.bracketPhase("KNOCKOUT").raceTo(9).build()));
		SaveTournamentConfigRequest request = configRequest(SeedingMethod.RANDOM.name(), null, Map.of());
		request.setRaceToOverrides(List.of(raceToOverride("final", 11)));

		service.saveConfig(OWNER_ID, TOURNAMENT_ID, request, true);

		ArgumentCaptor<TournamentRaceToRule> rule = ArgumentCaptor.forClass(TournamentRaceToRule.class);
		verify(raceToRuleService).upsert(rule.capture());
		assertEquals(11, rule.getValue().getRaceTo());
		assertEquals("KNOCKOUT", rule.getValue().getBracketPhase(), "the phase is copied from the format rule");
	}

	@Test
	@DisplayName("TC-058 · Setting a race-to back to the format default removes the override")
	void TC058_saveConfig_raceToBackToDefaultRemovesOverride() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of());
		when(formatRaceToRuleRepository.findByFormatCodeAndRoundKey(FORMAT, "final")).thenReturn(Optional.of(
				FormatRaceToRule.builder().id(1L).formatCode(FORMAT).roundKey("final")
						.bracketPhase("KNOCKOUT").raceTo(9).build()));
		SaveTournamentConfigRequest request = configRequest(SeedingMethod.RANDOM.name(), null, Map.of());
		request.setRaceToOverrides(List.of(raceToOverride("final", 9)));

		service.saveConfig(OWNER_ID, TOURNAMENT_ID, request, true);

		verify(raceToRuleService).deleteByTournamentAndRoundKey(TOURNAMENT_ID, "final");
		verify(raceToRuleService, never()).upsert(any());
	}

	@Test
	@DisplayName("TC-059 · Overriding a round the format does not have")
	void TC059_saveConfig_raceToUnknownRound() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		givenCompleteConfig();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of());
		when(formatRaceToRuleRepository.findByFormatCodeAndRoundKey(FORMAT, "quarter_final"))
				.thenReturn(Optional.empty());
		SaveTournamentConfigRequest request = configRequest(SeedingMethod.RANDOM.name(), null, Map.of());
		request.setRaceToOverrides(List.of(raceToOverride("quarter_final", 7)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.saveConfig(OWNER_ID, TOURNAMENT_ID, request, true));

		assertEquals(ErrorCode.INVALID_FIELD_FOR_FORMAT, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-060 · Editing the bracket size moves the maximum entry count with it")
	void TC060_saveConfig_bracketSizeSyncsCapacity() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("bracket_size", "INT", true, "16", 4, 128)));

		service.saveConfig(OWNER_ID, TOURNAMENT_ID,
				configRequest(SeedingMethod.RANDOM.name(), null, Map.of("bracket_size", "32")), true);

		assertEquals(32, t.getMaxParticipants(), "the two numbers describe the same thing and must not drift");
		verify(tournamentRepository).save(t);
	}

	// ══════════════════════════ config read models ══════════════════════════

	@Test
	@DisplayName("TC-061 · The configuration form reports where each value came from")
	void TC061_getConfigForm_marksFieldSource() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		givenCompleteConfig();
		givenReadyFormat();
		when(formatConfigFieldRepository.findByFormatCodeAndIsVisibleToOwnerTrueOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("bracket_size", "INT", true, "16", 4, 128)));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of(
				FormatRaceToRule.builder().id(1L).formatCode(FORMAT).roundKey("final")
						.label("Chung kết").bracketPhase("KNOCKOUT").raceTo(9).build()));
		when(raceToRuleService.getByTournament(TOURNAMENT_ID)).thenReturn(List.of());

		TournamentConfigFormResponse response = service.getConfigForm(OWNER_ID, TOURNAMENT_ID, true);

		assertEquals(1, response.getFields().size());
		assertEquals("16", response.getFields().get(0).getValue());
		assertEquals(FieldSource.ADMIN_DEFAULT, response.getFields().get(0).getSource());
		assertEquals(9, response.getRaceToRules().get(0).getRaceTo());
		assertFalse(response.getRaceToRules().get(0).getIsOverridden());
		assertEquals(3, response.getSeedingOptions().size());
	}

	@Test
	@DisplayName("TC-062 · A value the organiser saved outranks the admin default")
	void TC062_getConfigForm_tournamentValueWins() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		givenCompleteConfig();
		givenReadyFormat();
		when(formatConfigFieldRepository.findByFormatCodeAndIsVisibleToOwnerTrueOrderByIdAsc(FORMAT))
				.thenReturn(List.of(configField("bracket_size", "INT", true, "16", 4, 128)));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of());
		when(configValueService.getByTournamentAndField(TOURNAMENT_ID, "bracket_size"))
				.thenReturn(Optional.of(TournamentConfigValue.builder().value("32").build()));

		TournamentConfigFormResponse response = service.getConfigForm(OWNER_ID, TOURNAMENT_ID, true);

		assertEquals("32", response.getFields().get(0).getValue());
		assertEquals(FieldSource.TOURNAMENT, response.getFields().get(0).getSource());
		assertEquals("16", response.getFields().get(0).getDefaultValue());
	}

	@Test
	@DisplayName("TC-063 · The resolved configuration returns typed values, not strings")
	void TC063_getResolvedConfig_coercesTypes() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		givenCompleteConfig();
		givenReadyFormat();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of(
				configField("bracket_size", "INT", true, "16", 4, 128),
				configField("third_place_match", "BOOLEAN", true, "true", null, null)));
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of(
				FormatRaceToRule.builder().id(1L).formatCode(FORMAT).roundKey("final").raceTo(9).build()));
		when(raceToRuleService.getByTournament(TOURNAMENT_ID)).thenReturn(List.of());

		TournamentConfigResolvedResponse response = service.getResolvedConfig(OWNER_ID, TOURNAMENT_ID, true);

		assertEquals(16, response.getFields().get("bracket_size"));
		assertEquals(true, response.getFields().get("third_place_match"));
		assertEquals(9, response.getRaceToRules().get("final"));
		assertTrue(response.getOverriddenRounds().isEmpty());
	}

	@Test
	@DisplayName("TC-064 · A round the organiser overrode is listed as overridden")
	void TC064_getResolvedConfig_listsOverriddenRounds() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		givenCompleteConfig();
		givenReadyFormat();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of());
		when(formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc(FORMAT)).thenReturn(List.of(
				FormatRaceToRule.builder().id(1L).formatCode(FORMAT).roundKey("final").raceTo(9).build()));
		when(raceToRuleService.getByTournament(TOURNAMENT_ID)).thenReturn(List.of(
				TournamentRaceToRule.builder().roundKey("final").raceTo(11).build()));

		TournamentConfigResolvedResponse response = service.getResolvedConfig(OWNER_ID, TOURNAMENT_ID, true);

		assertEquals(List.of("final"), response.getOverriddenRounds());
	}

	@Test
	@DisplayName("TC-065 · Validating a configuration that is ready to go")
	void TC065_validateConfig_valid() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		givenCompleteConfig();

		TournamentConfigValidateResponse response = service.validateConfig(OWNER_ID, TOURNAMENT_ID, true);

		assertTrue(response.getIsValid());
		assertTrue(response.getIsConfigComplete());
		assertTrue(response.getErrors().isEmpty());
	}

	@Test
	@DisplayName("TC-066 · Validating a format the admin has left without race-to rules")
	void TC066_validateConfig_reportsMissingRaceToRules() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		givenCompleteConfig();
		when(formatRaceToRuleRepository.countByFormatCode(FORMAT)).thenReturn(0L);

		TournamentConfigValidateResponse response = service.validateConfig(OWNER_ID, TOURNAMENT_ID, true);

		assertFalse(response.getIsValid());
		assertTrue(response.getErrors().stream().anyMatch(e -> "raceToRules".equals(e.getFieldKey())));
	}

	@Test
	@DisplayName("TC-067 · A tournament with no seeding method recorded is not configured")
	void TC067_validateConfig_missingSeedingMethod() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		givenCompleteConfig();
		when(tournamentConfigRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				TournamentConfig.builder().tournamentId(TOURNAMENT_ID).formatCode(FORMAT)
						.seedingMethod("  ").build()));

		TournamentConfigValidateResponse response = service.validateConfig(OWNER_ID, TOURNAMENT_ID, true);

		assertFalse(response.getIsValid());
		assertTrue(response.getErrors().stream().anyMatch(e -> "seedingMethod".equals(e.getFieldKey())));
	}

	@Test
	@DisplayName("TC-068 · A tournament whose configuration row is missing entirely")
	void TC068_validateConfig_configRowMissing() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		when(tournamentConfigRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.validateConfig(OWNER_ID, TOURNAMENT_ID, true));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ patchStatus ══════════════════════════

	private void givenMailContext() {
		lenient().when(mailContextBuilder.systemContext()).thenReturn(new java.util.HashMap<>());
	}

	@Test
	@DisplayName("TC-069 · Setting the status it already has changes nothing")
	void TC069_patchStatus_noop() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);

		PatchTournamentStatusResponse response = service.patchStatus(OWNER_ID, TOURNAMENT_ID,
				statusRequest(TournamentStatus.DRAFT.getValue()), true);

		assertEquals(TournamentStatus.DRAFT.getValue(), response.getStatus());
		verify(tournamentRepository, never()).save(any(Tournament.class));
		verify(eventPublisher, never()).publishEvent(any(MailDomainEvent.class));
	}

	@Test
	@DisplayName("TC-070 · A transition the lifecycle does not allow is refused")
	void TC070_patchStatus_illegalTransition() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.patchStatus(
				OWNER_ID, TOURNAMENT_ID, statusRequest(TournamentStatus.IN_PROGRESS.getValue()), true));

		assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-071 · Registration cannot be opened while the configuration is incomplete")
	void TC071_patchStatus_openBlockedByIncompleteConfig() {
		givenLoadableTournament(tournament(TournamentStatus.DRAFT.getValue()));
		givenCompleteConfig();
		when(formatRaceToRuleRepository.countByFormatCode(FORMAT)).thenReturn(0L);

		ConfigValidationException ex = assertThrows(ConfigValidationException.class, () -> service.patchStatus(
				OWNER_ID, TOURNAMENT_ID, statusRequest(TournamentStatus.OPEN_FOR_REGISTRATION.getValue()), true));

		assertEquals(ErrorCode.CONFIG_INCOMPLETE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-072 · Opening registration publishes the tournament and announces it")
	void TC072_patchStatus_openPublishes() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		givenMailContext();

		PatchTournamentStatusResponse response = service.patchStatus(OWNER_ID, TOURNAMENT_ID,
				statusRequest(TournamentStatus.OPEN_FOR_REGISTRATION.getValue()), true);

		assertEquals(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), response.getStatus());
		assertEquals(TournamentStatus.DRAFT.getValue(), response.getPreviousStatus());
		verify(tournamentAuditService).recordChange(t, TournamentStatus.DRAFT.getValue(),
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), OWNER_ID, "Cập nhật trạng thái thủ công");
		verify(eventPublisher).publishEvent(any(MailDomainEvent.class));
		verify(eventPublisher).publishEvent(any(TournamentPublishedEvent.class));
	}

	@Test
	@DisplayName("TC-073 · Opening registration re-checks that a usable form template is attached")
	void TC073_patchStatus_openChecksRegistrationForm() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		t.setIsRegister(true);
		t.setRegistrationFormTemplateId(55L);
		givenLoadableTournament(t);
		givenCompleteConfig();
		givenMailContext();

		service.patchStatus(OWNER_ID, TOURNAMENT_ID,
				statusRequest(TournamentStatus.OPEN_FOR_REGISTRATION.getValue()), true);

		verify(registrationFormService).validateRegistrationSettings(true, 55L);
	}

	@Test
	@DisplayName("TC-074 · A tournament cannot be completed while matches are still to be played")
	void TC074_patchStatus_completeBlockedByOpenMatches() {
		givenLoadableTournament(tournament(TournamentStatus.IN_PROGRESS.getValue()));
		when(matchRepository.existsByTournamentIdAndStatusNotIn(eq(TOURNAMENT_ID), any())).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.patchStatus(
				OWNER_ID, TOURNAMENT_ID, statusRequest(TournamentStatus.COMPLETED.getValue()), true));

		assertEquals(ErrorCode.TOURNAMENT_MATCHES_NOT_FINISHED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-075 · Completing a tournament settles the official rankings")
	void TC075_patchStatus_completeFinalisesResults() {
		Tournament t = tournament(TournamentStatus.IN_PROGRESS.getValue());
		givenLoadableTournament(t);
		givenMailContext();
		when(matchRepository.existsByTournamentIdAndStatusNotIn(eq(TOURNAMENT_ID), any())).thenReturn(false);

		service.patchStatus(OWNER_ID, TOURNAMENT_ID,
				statusRequest(TournamentStatus.COMPLETED.getValue()), true);

		assertEquals(TournamentStatus.COMPLETED.getValue(), t.getStatus());
		verify(tournamentResultService).finalizeTournamentResults(TOURNAMENT_ID, OWNER_ID);
	}

	@Test
	@DisplayName("TC-076 · Closing registration checks the turnout against the progressive configuration")
	void TC076_patchStatus_progressiveTurnoutChecked() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue());
		t.setFormat("PROGRESSIVE_ROUND_ROBIN");
		givenLoadableTournament(t);
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc("PROGRESSIVE_ROUND_ROBIN")).thenReturn(List.of(
				FormatConfigField.builder().id(1L).formatCode("PROGRESSIVE_ROUND_ROBIN")
						.fieldKey("pe_survivors_per_stage").defaultValue("10,6,4").isRequired(true).build()));
		when(configValueService.getByTournamentAndField(TOURNAMENT_ID, "pe_survivors_per_stage"))
				.thenReturn(Optional.empty());
		when(participantRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(8L);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.patchStatus(
				OWNER_ID, TOURNAMENT_ID, statusRequest(TournamentStatus.REGISTRATION_CLOSED.getValue()), true));

		assertEquals(ErrorCode.PROGRESSIVE_CONFIG_INVALID, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-077 · Closing registration succeeds when the turnout fits the configuration")
	void TC077_patchStatus_progressiveTurnoutSufficient() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue());
		t.setFormat("PROGRESSIVE_ROUND_ROBIN");
		givenLoadableTournament(t);
		givenMailContext();
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc("PROGRESSIVE_ROUND_ROBIN")).thenReturn(List.of(
				FormatConfigField.builder().id(1L).formatCode("PROGRESSIVE_ROUND_ROBIN")
						.fieldKey("pe_survivors_per_stage").defaultValue("10,6,4").isRequired(true).build()));
		when(configValueService.getByTournamentAndField(TOURNAMENT_ID, "pe_survivors_per_stage"))
				.thenReturn(Optional.empty());
		when(participantRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(12L);

		PatchTournamentStatusResponse response = service.patchStatus(OWNER_ID, TOURNAMENT_ID,
				statusRequest(TournamentStatus.REGISTRATION_CLOSED.getValue()), true);

		assertEquals(TournamentStatus.REGISTRATION_CLOSED.getValue(), response.getStatus());
	}

	@Test
	@DisplayName("TC-078 · A tournament may be cancelled from a draft")
	void TC078_patchStatus_cancelFromDraft() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenMailContext();

		PatchTournamentStatusResponse response = service.patchStatus(OWNER_ID, TOURNAMENT_ID,
				statusRequest(TournamentStatus.CANCELLED.getValue()), true);

		assertEquals(TournamentStatus.CANCELLED.getValue(), response.getStatus());
		verify(eventPublisher, never()).publishEvent(any(TournamentPublishedEvent.class));
	}

	@Test
	@DisplayName("TC-079 · The draw cannot be skipped by setting the status by hand")
	void TC079_patchStatus_cannotJumpToDrawDone() {
		givenLoadableTournament(tournament(TournamentStatus.REGISTRATION_CLOSED.getValue()));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.patchStatus(
				OWNER_ID, TOURNAMENT_ID, statusRequest(TournamentStatus.DRAW_DONE.getValue()), true));

		assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.getErrorCode());
	}

	// ══════════════════════════ getStatusHistory ══════════════════════════

	@Test
	@DisplayName("TC-080 · Reading the history of a tournament that does not exist")
	void TC080_getStatusHistory_notFound() {
		when(tournamentRepository.existsById(TOURNAMENT_ID)).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getStatusHistory(TOURNAMENT_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(tournamentAuditService, never()).getHistory(anyLong());
	}

	@Test
	@DisplayName("TC-081 · The status history is read straight from the audit trail")
	void TC081_getStatusHistory_delegatesToAudit() {
		when(tournamentRepository.existsById(TOURNAMENT_ID)).thenReturn(true);
		when(tournamentAuditService.getHistory(TOURNAMENT_ID)).thenReturn(List.of());

		assertTrue(service.getStatusHistory(TOURNAMENT_ID).isEmpty());
		verify(tournamentAuditService).getHistory(TOURNAMENT_ID);
	}

	// ══════════════════════════ whole-record edit and format-specific rules ══════════════════════════

	@Test
	@DisplayName("TC-082 · Every editable field can be rewritten in a single save")
	void TC082_updateTournament_allFieldsAtOnce() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		givenLoadableTournament(t);
		givenCompleteConfig();
		Instant newDeadline = Instant.now().plus(30, ChronoUnit.DAYS);
		Instant newStart = Instant.now().plus(40, ChronoUnit.DAYS);
		Instant newEnd = Instant.now().plus(45, ChronoUnit.DAYS);

		service.updateTournament(OWNER_ID, TOURNAMENT_ID, updateWith(r -> {
			r.setName("Autumn Open 2026");
			r.setDescription("Giải mùa thu");
			r.setThumbnailUrl("tournaments/thumb.jpg");
			r.setBannerUrl("tournaments/banner.jpg");
			r.setMaxParticipants(32);
			r.setTableCount(8);
			r.setEntryFee(new BigDecimal("250000"));
			r.setPrizePool(new BigDecimal("5000000"));
			r.setPrizeDescription("Cúp và tiền thưởng");
			r.setRegistrationDeadline(newDeadline);
			r.setStartAt(newStart);
			r.setEndAt(newEnd);
			r.setIsRegister(true);
			r.setIsShowTournament(false);
			r.setIsPublicRatio(true);
			r.setRegistrationFormTemplateId(55L);
		}), true);

		assertEquals("Autumn Open 2026", t.getName());
		assertEquals("Giải mùa thu", t.getDescription());
		assertEquals("tournaments/thumb.jpg", t.getThumbnailUrl());
		assertEquals("tournaments/banner.jpg", t.getBannerUrl());
		assertEquals(32, t.getMaxParticipants());
		assertEquals(8, t.getTableCount());
		assertEquals(new BigDecimal("250000"), t.getEntryFee());
		assertEquals(new BigDecimal("5000000"), t.getPrizePool());
		assertEquals("Cúp và tiền thưởng", t.getPrizeDescription());
		assertEquals(newDeadline, t.getRegistrationDeadline());
		assertEquals(newStart, t.getStartAt());
		assertEquals(newEnd, t.getEndAt());
		assertTrue(t.getIsRegister());
		assertFalse(t.getIsShowTournament());
		assertTrue(t.getIsPublicRatio());
		assertEquals(55L, t.getRegistrationFormTemplateId());
	}

	@Test
	@DisplayName("TC-083 · A group draw whose seats do not add up to the entry count is refused")
	void TC083_validateConfig_groupPlayoffSeatMismatch() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		t.setFormat("GROUP_PLAYOFF");
		givenLoadableTournament(t);
		when(tournamentConfigRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				TournamentConfig.builder().tournamentId(TOURNAMENT_ID).formatCode("GROUP_PLAYOFF")
						.seedingMethod(SeedingMethod.RANDOM.name()).build()));
		when(formatRaceToRuleRepository.countByFormatCode("GROUP_PLAYOFF")).thenReturn(1L);
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc("GROUP_PLAYOFF")).thenReturn(List.of(
				FormatConfigField.builder().id(1L).formatCode("GROUP_PLAYOFF").fieldKey("group_count")
						.defaultValue("4").isRequired(false).build(),
				FormatConfigField.builder().id(2L).formatCode("GROUP_PLAYOFF").fieldKey("players_per_group")
						.defaultValue("3").isRequired(false).build()));
		when(configValueService.getByTournamentAndField(eq(TOURNAMENT_ID), anyString())).thenReturn(Optional.empty());

		TournamentConfigValidateResponse response = service.validateConfig(OWNER_ID, TOURNAMENT_ID, true);

		assertFalse(response.getIsValid());
		assertTrue(response.getErrors().stream().anyMatch(e -> "group_count".equals(e.getFieldKey())),
				"4 groups of 3 seats 12 players, not the 16 the tournament advertises");
	}

	@Test
	@DisplayName("TC-084 · A progressive playoff size that is not a number is reported per field")
	void TC084_validateConfig_progressivePlayoffSizeNotANumber() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		t.setFormat("PROGRESSIVE_ROUND_ROBIN");
		givenLoadableTournament(t);
		when(tournamentConfigRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				TournamentConfig.builder().tournamentId(TOURNAMENT_ID).formatCode("PROGRESSIVE_ROUND_ROBIN")
						.seedingMethod(SeedingMethod.RANDOM.name()).build()));
		when(formatRaceToRuleRepository.countByFormatCode("PROGRESSIVE_ROUND_ROBIN")).thenReturn(1L);
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc("PROGRESSIVE_ROUND_ROBIN")).thenReturn(List.of(
				FormatConfigField.builder().id(1L).formatCode("PROGRESSIVE_ROUND_ROBIN")
						.fieldKey("pe_survivors_per_stage").defaultValue("10,6,4").isRequired(false).build(),
				FormatConfigField.builder().id(2L).formatCode("PROGRESSIVE_ROUND_ROBIN")
						.fieldKey("final_playoff_size").defaultValue("bốn").isRequired(false).build()));
		when(configValueService.getByTournamentAndField(eq(TOURNAMENT_ID), anyString())).thenReturn(Optional.empty());

		TournamentConfigValidateResponse response = service.validateConfig(OWNER_ID, TOURNAMENT_ID, true);

		assertFalse(response.getIsValid());
		assertTrue(response.getErrors().stream().anyMatch(e -> "final_playoff_size".equals(e.getFieldKey())));
	}

	@Test
	@DisplayName("TC-085 · A survivor list the tournament capacity cannot support is reported field by field")
	void TC085_validateConfig_progressiveSurvivorsAgainstCapacity() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		t.setFormat("PROGRESSIVE_ROUND_ROBIN");
		t.setMaxParticipants(8);
		givenLoadableTournament(t);
		when(tournamentConfigRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				TournamentConfig.builder().tournamentId(TOURNAMENT_ID).formatCode("PROGRESSIVE_ROUND_ROBIN")
						.seedingMethod(SeedingMethod.RANDOM.name()).build()));
		when(formatRaceToRuleRepository.countByFormatCode("PROGRESSIVE_ROUND_ROBIN")).thenReturn(1L);
		when(formatConfigFieldRepository.findByFormatCodeOrderByIdAsc("PROGRESSIVE_ROUND_ROBIN")).thenReturn(List.of(
				FormatConfigField.builder().id(1L).formatCode("PROGRESSIVE_ROUND_ROBIN")
						.fieldKey("pe_survivors_per_stage").defaultValue("10,6,4").isRequired(false).build(),
				FormatConfigField.builder().id(2L).formatCode("PROGRESSIVE_ROUND_ROBIN")
						.fieldKey("final_playoff_size").defaultValue("4").isRequired(false).build()));
		when(configValueService.getByTournamentAndField(eq(TOURNAMENT_ID), anyString())).thenReturn(Optional.empty());

		TournamentConfigValidateResponse response = service.validateConfig(OWNER_ID, TOURNAMENT_ID, true);

		assertFalse(response.getIsValid());
		assertTrue(response.getErrors().stream().anyMatch(e -> "pe_survivors_per_stage".equals(e.getFieldKey())),
				"a first stage keeping 10 cannot run in a tournament with only 8 places");
	}
}
