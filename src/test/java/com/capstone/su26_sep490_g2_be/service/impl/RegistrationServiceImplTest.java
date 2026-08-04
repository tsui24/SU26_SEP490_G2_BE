package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.RejectRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SubmitTournamentRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.response.CheckoutResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRegistrationResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFieldValue;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFieldValueId;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantMemberRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldValueRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.PayOSService;
import com.capstone.su26_sep490_g2_be.service.RegistrationFormService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * L1 unit tests for {@link RegistrationServiceImpl}.
 *
 * <p>Mirrors the <b>RegistrationService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-23 (submit a registration), UC-24 (approve or reject),
 * UC-25 (cancel), UC-28 (a approved registration becomes a participant) and UC-54 (payment).
 *
 * <p>The slot check runs behind a pessimistic lock and decides, on its own, whether a paid
 * registration is approved or refunded. Several cases below pin that decision down because it is
 * the one place money and a bracket place meet.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · RegistrationService — UC-23, UC-24, UC-25, UC-28, UC-54")
class RegistrationServiceImplTest {

	@Mock RegistrationRepository registrationRepository;
	@Mock TournamentRepository tournamentRepository;
	@Mock UserRepository userRepository;
	@Mock RegistrationFormService registrationFormService;
	@Mock RegistrationFieldValueRepository fieldValueRepository;
	@Mock RegistrationFieldDefinitionRepository fieldDefinitionRepository;
	@Mock PaymentRepository paymentRepository;
	@Mock PayOSService payOSService;
	@Mock ParticipantRepository participantRepository;
	@Mock ParticipantMemberRepository participantMemberRepository;
	@Mock ApplicationEventPublisher eventPublisher;
	@Mock MailContextBuilder mailContextBuilder;
	@Mock BranchAccessService branchAccessService;

	@InjectMocks RegistrationServiceImpl service;

	private static final Long TOURNAMENT_ID = 500L;
	private static final Long REGISTRATION_ID = 60L;
	private static final Long PLAYER_ID = 11L;
	private static final Long STAFF_ID = 3L;
	private static final Long BRANCH_ID = 2L;
	private static final BigDecimal FEE = new BigDecimal("200000");

	@BeforeEach
	void wireMailContext() {
		lenient().when(mailContextBuilder.systemContext()).thenReturn(new HashMap<>());
	}

	// ══════════════════════════ fixtures ══════════════════════════

	private static User user(Long id, String email) {
		return User.builder().id(id).email(email).build();
	}

	private static Tournament tournament(String status, BigDecimal fee, String participantType) {
		return Tournament.builder()
				.id(TOURNAMENT_ID).name("Summer Open 2026")
				.status(status).entryFee(fee).participantType(participantType)
				.maxParticipants(16).isRegister(true).registrationFormTemplateId(55L)
				.branch(Branch.builder().id(BRANCH_ID).name("Chi nhánh Quận 1").build())
				.build();
	}

	private static Registration registration(Tournament t, String status) {
		return Registration.builder()
				.id(REGISTRATION_ID).tournament(t).user(user(PLAYER_ID, "player@btms.vn"))
				.registrationType("SINGLE").playerFullName("Nguyễn Văn A").playerPhone("0900000001")
				.status(status)
				.build();
	}

	private static SubmitTournamentRegistrationRequest submitRequest() {
		SubmitTournamentRegistrationRequest request = new SubmitTournamentRegistrationRequest();
		request.setRegistrationType("SINGLE");
		request.setNote("Xin chào");
		request.setFieldValues(List.of());
		return request;
	}

	private static Map<String, String> singleFieldValues() {
		return Map.of("player_full_name", "Nguyễn Văn A", "player_phone", "0900000001");
	}

	/** Registration save echoes the entity back with an id, the way JPA would. */
	private void givenRegistrationSaveEchoes() {
		lenient().when(registrationRepository.save(any(Registration.class))).thenAnswer(inv -> {
			Registration r = inv.getArgument(0);
			if (r.getId() == null) r.setId(REGISTRATION_ID);
			return r;
		});
		lenient().when(fieldValueRepository.findByRegistrationIdOrderByIdAsc(anyLong())).thenReturn(List.of());
	}

	private void givenStaffCanAccessBranch(boolean allowed) {
		lenient().when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(user(STAFF_ID, "staff@btms.vn")));
		lenient().when(branchAccessService.canActorAccessBranch(any(User.class), eq(BRANCH_ID))).thenReturn(allowed);
	}

	private MailDomainEvent capturePublishedEvent() {
		ArgumentCaptor<MailDomainEvent> captor = ArgumentCaptor.forClass(MailDomainEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		return captor.getValue();
	}

	// ══════════════════════════ submitRegistration ══════════════════════════

	@Test
	@DisplayName("TC-001 · A paid tournament leaves the entry waiting for payment")
	void TC001_submitRegistration_paidTournamentAwaitsPayment() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(4L);
		when(registrationFormService.validateAndNormalizeFieldValues(eq(55L), any()))
				.thenReturn(singleFieldValues());
		when(userRepository.findById(PLAYER_ID)).thenReturn(Optional.of(user(PLAYER_ID, "player@btms.vn")));
		givenRegistrationSaveEchoes();

		TournamentRegistrationResponse response = service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest());

		assertEquals(RegistrationStatus.PENDING_PAYMENT.getValue(), response.getStatus());
		assertEquals("Nguyễn Văn A", response.getPlayerFullName());
		assertEquals("0900000001", response.getPlayerPhone());
		verify(registrationFormService).saveFieldValues(any(Registration.class), eq(55L), eq(singleFieldValues()));
		assertEquals(EmailEventType.REGISTRATION_SUBMITTED, capturePublishedEvent().eventType());
	}

	@Test
	@DisplayName("TC-002 · Registering twice for the same tournament is refused")
	void TC002_submitRegistration_duplicate() {
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest()));

		assertEquals(ErrorCode.REGISTRATION_ALREADY_EXISTS, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-003 · Registering for a tournament that does not exist")
	void TC003_submitRegistration_tournamentNotFound() {
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest()));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-004 · A tournament that collects no registrations cannot be entered")
	void TC004_submitRegistration_registrationDisabled() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		t.setIsRegister(false);
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest()));

		assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-005 · Registration closes with the tournament status")
	void TC005_submitRegistration_notOpen() {
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.REGISTRATION_CLOSED.getValue(), FEE, "SINGLE")));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest()));

		assertEquals(ErrorCode.REGISTRATION_NOT_OPEN, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-006 · A tournament with no form template cannot take entries")
	void TC006_submitRegistration_noTemplate() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		t.setRegistrationFormTemplateId(null);
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest()));

		assertEquals(ErrorCode.REG_TEMPLATE_REQUIRED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-007 · A tournament already at capacity turns entries away")
	void TC007_submitRegistration_tournamentFull() {
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE")));
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(16L);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest()));

		assertEquals(ErrorCode.TOURNAMENT_FULL, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-008 · A doubles tournament will not accept a single name")
	void TC008_submitRegistration_doublesNeedsPartner() {
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "DOUBLE")));
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(0L);
		when(registrationFormService.validateAndNormalizeFieldValues(eq(55L), any()))
				.thenReturn(singleFieldValues());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest()));

		assertEquals(ErrorCode.PARTICIPANT_PARTNER_REQUIRED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-009 · Registering under an account that no longer exists")
	void TC009_submitRegistration_userNotFound() {
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE")));
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(0L);
		when(registrationFormService.validateAndNormalizeFieldValues(eq(55L), any()))
				.thenReturn(singleFieldValues());
		when(userRepository.findById(PLAYER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest()));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-010 · A free tournament approves the entry on the spot")
	void TC010_submitRegistration_freeTournamentAutoApproves() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), BigDecimal.ZERO, "SINGLE");
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(tournamentRepository.findByIdWithLock(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(4L);
		when(registrationFormService.validateAndNormalizeFieldValues(eq(55L), any()))
				.thenReturn(singleFieldValues());
		when(userRepository.findById(PLAYER_ID)).thenReturn(Optional.of(user(PLAYER_ID, "player@btms.vn")));
		when(participantRepository.existsByRegistrationId(REGISTRATION_ID)).thenReturn(false);
		givenRegistrationSaveEchoes();

		service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest());

		ArgumentCaptor<Registration> saved = ArgumentCaptor.forClass(Registration.class);
		verify(registrationRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
		assertEquals(RegistrationStatus.APPROVED.getValue(), saved.getValue().getStatus());
		verify(participantRepository).save(any(Participant.class));
	}

	@Test
	@DisplayName("TC-011 · A free tournament that filled up while the form was open rejects with a reason")
	void TC011_submitRegistration_freeTournamentFullRejects() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), BigDecimal.ZERO, "SINGLE");
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(tournamentRepository.findByIdWithLock(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(0L, 16L);
		when(registrationFormService.validateAndNormalizeFieldValues(eq(55L), any()))
				.thenReturn(singleFieldValues());
		when(userRepository.findById(PLAYER_ID)).thenReturn(Optional.of(user(PLAYER_ID, "player@btms.vn")));
		givenRegistrationSaveEchoes();

		service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest());

		ArgumentCaptor<Registration> saved = ArgumentCaptor.forClass(Registration.class);
		verify(registrationRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
		assertEquals(RegistrationStatus.REJECTED.getValue(), saved.getValue().getStatus());
		assertTrue(saved.getValue().getRejectedReason().contains("16"));
		verify(participantRepository, never()).save(any(Participant.class));
	}

	@Test
	@DisplayName("TC-012 · A form with none of the known name fields still produces an entry")
	void TC012_submitRegistration_fallsBackWhenNamesAreMissing() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(0L);
		when(registrationFormService.validateAndNormalizeFieldValues(eq(55L), any()))
				.thenReturn(Map.of("club", "CLB Bi-a FPT"));
		when(userRepository.findById(PLAYER_ID)).thenReturn(Optional.of(user(PLAYER_ID, "player@btms.vn")));
		givenRegistrationSaveEchoes();

		TournamentRegistrationResponse response = service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest());

		assertEquals("N/A", response.getPlayerFullName(),
				"a custom form need not carry a name field, and the entry must still be creatable");
		assertEquals("N/A", response.getPlayerPhone());
	}

	@Test
	@DisplayName("TC-013 · The alternative field keys of a doubles form are all recognised")
	void TC013_submitRegistration_acceptsAlternativeFieldKeys() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "DOUBLE");
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(0L);
		when(registrationFormService.validateAndNormalizeFieldValues(eq(55L), any())).thenReturn(Map.of(
				"player1_full_name", "Nguyễn Văn A", "player1_phone", "0900000001",
				"player2_full_name", "Trần Thị B"));
		when(userRepository.findById(PLAYER_ID)).thenReturn(Optional.of(user(PLAYER_ID, "player@btms.vn")));
		givenRegistrationSaveEchoes();

		TournamentRegistrationResponse response = service.submitRegistration(TOURNAMENT_ID, PLAYER_ID, submitRequest());

		assertEquals("Nguyễn Văn A", response.getPlayerFullName(),
				"player1_full_name is as valid as player_full_name — templates differ between formats");
	}

	// ══════════════════════════ register (legacy entry point) ══════════════════════════

	@Test
	@DisplayName("TC-014 · The plain entry point stores an entry awaiting payment")
	void TC014_register_happyPath() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(userRepository.findById(PLAYER_ID)).thenReturn(Optional.of(user(PLAYER_ID, "player@btms.vn")));
		when(registrationRepository.save(any(Registration.class))).thenAnswer(inv -> inv.getArgument(0));

		Registration saved = service.register(TOURNAMENT_ID, PLAYER_ID, Registration.builder().build());

		assertEquals(RegistrationStatus.PENDING_PAYMENT.getValue(), saved.getStatus());
		assertEquals(TOURNAMENT_ID, saved.getTournament().getId());
		assertEquals(PLAYER_ID, saved.getUser().getId());
	}

	@Test
	@DisplayName("TC-015 · The plain entry point also refuses a second entry")
	void TC015_register_duplicate() {
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.register(TOURNAMENT_ID, PLAYER_ID, Registration.builder().build()));

		assertEquals(ErrorCode.REGISTRATION_ALREADY_EXISTS, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-016 · The plain entry point respects the registration window")
	void TC016_register_notOpen() {
		when(registrationRepository.existsByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(false);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.DRAFT.getValue(), FEE, "SINGLE")));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.register(TOURNAMENT_ID, PLAYER_ID, Registration.builder().build()));

		assertEquals(ErrorCode.REGISTRATION_NOT_OPEN, ex.getErrorCode());
	}

	// ══════════════════════════ approve ══════════════════════════

	@Test
	@DisplayName("TC-017 · Approving an entry records the approver and creates the participant")
	void TC017_approve_happyPath() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PAID.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(tournamentRepository.findByIdWithLock(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		givenStaffCanAccessBranch(true);
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(4L);
		when(participantRepository.existsByRegistrationId(REGISTRATION_ID)).thenReturn(false);
		givenRegistrationSaveEchoes();

		TournamentRegistrationResponse response = service.approve(REGISTRATION_ID, STAFF_ID);

		assertEquals(RegistrationStatus.APPROVED.getValue(), response.getStatus());
		assertEquals(STAFF_ID, reg.getApprovedBy().getId());
		assertNotNull(reg.getApprovedAt());
		verify(participantRepository).save(any(Participant.class));
		assertEquals(EmailEventType.REGISTRATION_APPROVED, capturePublishedEvent().eventType());
	}

	@Test
	@DisplayName("TC-018 · No entry may be approved once the bracket exists")
	void TC018_approve_rosterLocked() {
		Tournament t = tournament(TournamentStatus.DRAW_DONE.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PAID.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(tournamentRepository.findByIdWithLock(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		givenStaffCanAccessBranch(true);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(REGISTRATION_ID, STAFF_ID));

		assertEquals(ErrorCode.TOURNAMENT_ROSTER_LOCKED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-019 · Approving beyond capacity is refused")
	void TC019_approve_tournamentFull() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PAID.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(tournamentRepository.findByIdWithLock(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		givenStaffCanAccessBranch(true);
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(16L);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(REGISTRATION_ID, STAFF_ID));

		assertEquals(ErrorCode.TOURNAMENT_FULL, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-020 · A manager may not approve entries of another branch")
	void TC020_approve_branchAccessDenied() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PAID.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(tournamentRepository.findByIdWithLock(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		givenStaffCanAccessBranch(false);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(REGISTRATION_ID, STAFF_ID));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-021 · Approving under an account that no longer exists")
	void TC021_approve_approverNotFound() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PAID.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(tournamentRepository.findByIdWithLock(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(userRepository.findById(STAFF_ID))
				.thenReturn(Optional.of(user(STAFF_ID, "staff@btms.vn")), Optional.empty());
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(BRANCH_ID))).thenReturn(true);
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(0L);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(REGISTRATION_ID, STAFF_ID));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-022 · Approving an entry that does not exist")
	void TC022_approve_registrationNotFound() {
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(REGISTRATION_ID, STAFF_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ reject ══════════════════════════

	@Test
	@DisplayName("TC-023 · Rejecting an entry records the reason and tells the player")
	void TC023_reject_happyPath() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		givenStaffCanAccessBranch(true);
		givenRegistrationSaveEchoes();
		RejectRegistrationRequest request = new RejectRegistrationRequest();
		request.setReason("Thiếu thông tin liên hệ");

		TournamentRegistrationResponse response = service.reject(REGISTRATION_ID, STAFF_ID, request);

		assertEquals(RegistrationStatus.REJECTED.getValue(), response.getStatus());
		assertEquals("Thiếu thông tin liên hệ", reg.getRejectedReason());
		assertNotNull(reg.getRejectedAt());
		assertEquals(EmailEventType.REGISTRATION_REJECTED, capturePublishedEvent().eventType());
	}

	@Test
	@DisplayName("TC-024 · An anonymous caller cannot reject an entry")
	void TC024_reject_anonymousDenied() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue())));
		RejectRegistrationRequest request = new RejectRegistrationRequest();
		request.setReason("Không hợp lệ");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.reject(REGISTRATION_ID, null, request));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
		verify(userRepository, never()).findById(any());
	}

	// ══════════════════════════ cancel ══════════════════════════

	@Test
	@DisplayName("TC-025 · A player may withdraw an entry that is still awaiting payment")
	void TC025_cancel_pendingPayment() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		givenRegistrationSaveEchoes();

		service.cancel(REGISTRATION_ID, PLAYER_ID);

		assertEquals(RegistrationStatus.CANCELLED.getValue(), reg.getStatus());
		assertEquals(EmailEventType.REGISTRATION_CANCELLED, capturePublishedEvent().eventType());
	}

	@Test
	@DisplayName("TC-026 · One player may not withdraw another player's entry")
	void TC026_cancel_notOwner() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue())));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(REGISTRATION_ID, 999L));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-027 · Withdrawing an entry that is already withdrawn changes nothing")
	void TC027_cancel_alreadyCancelled() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.CANCELLED.getValue())));

		service.cancel(REGISTRATION_ID, PLAYER_ID);

		verify(registrationRepository, never()).save(any(Registration.class));
		verify(eventPublisher, never()).publishEvent(any(MailDomainEvent.class));
	}

	@Test
	@DisplayName("TC-028 · Withdrawing an entry the organiser already rejected changes nothing")
	void TC028_cancel_alreadyRejected() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.REJECTED.getValue())));

		service.cancel(REGISTRATION_ID, PLAYER_ID);

		verify(registrationRepository, never()).save(any(Registration.class));
	}

	@Test
	@DisplayName("TC-029 · Withdrawing an approved entry also retires the participant")
	void TC029_cancel_approvedWithdrawsParticipant() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.APPROVED.getValue());
		Participant participant = Participant.builder().id(9L).displayName("Nguyễn Văn A")
				.status(ParticipantStatus.ACTIVE.getValue()).build();
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(participantRepository.findByRegistrationId(REGISTRATION_ID)).thenReturn(Optional.of(participant));
		givenRegistrationSaveEchoes();

		service.cancel(REGISTRATION_ID, PLAYER_ID);

		assertEquals(RegistrationStatus.CANCELLED.getValue(), reg.getStatus());
		assertEquals(ParticipantStatus.WITHDRAWN.getValue(), participant.getStatus());
		verify(participantRepository).save(participant);
	}

	@Test
	@DisplayName("TC-030 · An approved entry cannot be withdrawn once the bracket exists")
	void TC030_cancel_approvedAfterDrawRefused() {
		Tournament t = tournament(TournamentStatus.DRAW_DONE.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.APPROVED.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(REGISTRATION_ID, PLAYER_ID));

		assertEquals(ErrorCode.TOURNAMENT_ROSTER_LOCKED, ex.getErrorCode());
		verify(registrationRepository, never()).save(any(Registration.class));
	}

	@Test
	@DisplayName("TC-031 · An unpaid entry may still be withdrawn after the draw")
	void TC031_cancel_unpaidAfterDrawAllowed() {
		Tournament t = tournament(TournamentStatus.DRAW_DONE.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		givenRegistrationSaveEchoes();

		service.cancel(REGISTRATION_ID, PLAYER_ID);

		assertEquals(RegistrationStatus.CANCELLED.getValue(), reg.getStatus(),
				"only an approved entry is in the bracket; an unpaid one never made it there");
	}

	// ══════════════════════════ reads ══════════════════════════

	@Test
	@DisplayName("TC-032 · A player may read their own entry")
	void TC032_getRegistrationDetail_ownerReads() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue())));
		when(fieldValueRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(List.of());

		assertEquals(REGISTRATION_ID, service.getRegistrationDetail(REGISTRATION_ID, PLAYER_ID, false).getId());
	}

	@Test
	@DisplayName("TC-033 · A player may not read somebody else's entry")
	void TC033_getRegistrationDetail_otherPlayerDenied() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue())));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getRegistrationDetail(REGISTRATION_ID, 999L, false));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-034 · Staff read entries through the branch check instead of ownership")
	void TC034_getRegistrationDetail_staffReads() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue())));
		givenStaffCanAccessBranch(true);
		when(fieldValueRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(List.of());

		assertEquals(REGISTRATION_ID, service.getRegistrationDetail(REGISTRATION_ID, STAFF_ID, true).getId());
	}

	@Test
	@DisplayName("TC-035 · Field values come back with the label the admin defined")
	void TC035_getRegistrationDetail_resolvesFieldLabels() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue())));
		when(fieldValueRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(List.of(
				RegistrationFieldValue.builder()
						.id(new RegistrationFieldValueId(REGISTRATION_ID, "player_full_name"))
						.fieldDefinition(RegistrationFieldDefinition.builder()
								.fieldKey("player_full_name").label("Họ và tên").build())
						.value("Nguyễn Văn A").build()));

		TournamentRegistrationResponse response = service.getRegistrationDetail(REGISTRATION_ID, PLAYER_ID, false);

		assertEquals("Họ và tên", response.getFieldValues().get(0).getLabel());
		assertEquals("Nguyễn Văn A", response.getFieldValues().get(0).getValue());
	}

	@Test
	@DisplayName("TC-036 · A field whose definition was deleted falls back to its key")
	void TC036_getRegistrationDetail_labelFallsBackToKey() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue())));
		when(fieldValueRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(List.of(
				RegistrationFieldValue.builder()
						.id(new RegistrationFieldValueId(REGISTRATION_ID, "legacy_field"))
						.value("giá trị cũ").build()));
		when(fieldDefinitionRepository.findById("legacy_field")).thenReturn(Optional.empty());

		TournamentRegistrationResponse response = service.getRegistrationDetail(REGISTRATION_ID, PLAYER_ID, false);

		assertEquals("legacy_field", response.getFieldValues().get(0).getLabel(),
				"an old entry must still render after the admin removes a field");
	}

	@Test
	@DisplayName("TC-037 · A player's own list of entries comes back a page at a time")
	void TC037_getMyRegistrations_paged() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findByUserId(eq(PLAYER_ID), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue()))));
		when(fieldValueRepository.findByRegistrationIdInOrderByIdAsc(List.of(REGISTRATION_ID))).thenReturn(List.of());

		PageResponse<TournamentRegistrationResponse> page = service.getMyRegistrations(PLAYER_ID, 0, 10);

		assertEquals(1, page.getContent().size());
		verify(fieldValueRepository, never()).findByRegistrationIdOrderByIdAsc(anyLong());
	}

	@Test
	@DisplayName("TC-038 · A page of entries loads its field values in one query")
	void TC038_getTournamentRegistrations_batchesFieldValues() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		givenStaffCanAccessBranch(true);
		when(registrationRepository.findByTournamentId(eq(TOURNAMENT_ID), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue()))));
		when(fieldValueRepository.findByRegistrationIdInOrderByIdAsc(List.of(REGISTRATION_ID))).thenReturn(List.of(
				RegistrationFieldValue.builder()
						.id(new RegistrationFieldValueId(REGISTRATION_ID, "player_full_name"))
						.fieldDefinition(RegistrationFieldDefinition.builder()
								.fieldKey("player_full_name").label("Họ và tên").build())
						.value("Nguyễn Văn A").build()));

		PageResponse<TournamentRegistrationResponse> page =
				service.getTournamentRegistrations(TOURNAMENT_ID, STAFF_ID, null, 0, 10);

		assertEquals(1, page.getContent().get(0).getFieldValues().size());
		verify(fieldValueRepository).findByRegistrationIdInOrderByIdAsc(List.of(REGISTRATION_ID));
	}

	@Test
	@DisplayName("TC-039 · Entries can be filtered by their status")
	void TC039_getTournamentRegistrations_filtersByStatus() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		givenStaffCanAccessBranch(true);
		when(registrationRepository.findByTournamentIdAndStatus(
				eq(TOURNAMENT_ID), eq(RegistrationStatus.APPROVED.getValue()), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		service.getTournamentRegistrations(TOURNAMENT_ID, STAFF_ID, "  APPROVED  ", 0, 10);

		verify(registrationRepository).findByTournamentIdAndStatus(
				eq(TOURNAMENT_ID), eq(RegistrationStatus.APPROVED.getValue()), any(Pageable.class));
	}

	@Test
	@DisplayName("TC-040 · An empty page of entries queries no field values at all")
	void TC040_getTournamentRegistrations_emptyPage() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		givenStaffCanAccessBranch(true);
		when(registrationRepository.findByTournamentId(eq(TOURNAMENT_ID), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		assertTrue(service.getTournamentRegistrations(TOURNAMENT_ID, STAFF_ID, null, 0, 10).getContent().isEmpty());
		verify(fieldValueRepository, never()).findByRegistrationIdInOrderByIdAsc(any());
	}

	@Test
	@DisplayName("TC-041 · Listing entries of a tournament that does not exist")
	void TC041_getTournamentRegistrations_tournamentNotFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getTournamentRegistrations(TOURNAMENT_ID, STAFF_ID, null, 0, 10));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-042 · A player who never entered a tournament reads back nothing")
	void TC042_getMyRegistrationForTournament_absent() {
		when(registrationRepository.findByTournamentIdAndUserId(TOURNAMENT_ID, PLAYER_ID))
				.thenReturn(Optional.empty());

		assertNull(service.getMyRegistrationForTournament(TOURNAMENT_ID, PLAYER_ID),
				"null is what tells the client to show the registration button");
	}

	// ══════════════════════════ checkout ══════════════════════════

	@Test
	@DisplayName("TC-043 · Checking out creates a payment and a payment link")
	void TC043_checkout_createsPaymentLink() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(paymentRepository.findByRegistrationId(REGISTRATION_ID)).thenReturn(List.of());
		when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
			Payment p = inv.getArgument(0);
			if (p.getId() == null) p.setId(77L);
			return p;
		});
		when(payOSService.createPaymentLink(eq(77L), eq(200000L), anyString()))
				.thenReturn("https://pay.payos.vn/web/77");

		CheckoutResponse response = service.checkout(REGISTRATION_ID, PLAYER_ID);

		assertEquals(77L, response.getPaymentId());
		assertEquals(77L, response.getOrderCode(), "the payment id doubles as the PayOS order code");
		assertEquals(FEE, response.getAmount());
		assertEquals("https://pay.payos.vn/web/77", response.getCheckoutUrl());
	}

	@Test
	@DisplayName("TC-044 · A long tournament name is trimmed for the payment description")
	void TC044_checkout_trimsDescription() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		t.setName("Giải Bi-a Mở Rộng Toàn Quốc Mùa Hè 2026");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(paymentRepository.findByRegistrationId(REGISTRATION_ID)).thenReturn(List.of());
		when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
			Payment p = inv.getArgument(0);
			if (p.getId() == null) p.setId(77L);
			return p;
		});
		ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
		when(payOSService.createPaymentLink(anyLong(), anyLong(), description.capture()))
				.thenReturn("https://pay.payos.vn/web/77");

		CheckoutResponse response = service.checkout(REGISTRATION_ID, PLAYER_ID);

		assertEquals(25, description.getValue().length(), "PayOS caps the description length");
		assertEquals("Giải Bi-a Mở Rộng Toàn Quốc Mùa Hè 2026", response.getDescription(),
				"the response still carries the full name for the client to show");
	}

	@Test
	@DisplayName("TC-045 · Checking out somebody else's entry is refused")
	void TC045_checkout_notOwner() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue())));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.checkout(REGISTRATION_ID, 999L));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-046 · An entry that is already paid cannot be paid again")
	void TC046_checkout_alreadyPaid() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.PAID.getValue())));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.checkout(REGISTRATION_ID, PLAYER_ID));

		assertEquals(ErrorCode.PAYMENT_ALREADY_PAID, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-047 · An approved entry cannot be paid for")
	void TC047_checkout_alreadyApproved() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.APPROVED.getValue())));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.checkout(REGISTRATION_ID, PLAYER_ID));

		assertEquals(ErrorCode.PAYMENT_ALREADY_PAID, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-048 · A free tournament has nothing to check out")
	void TC048_checkout_freeTournament() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), BigDecimal.ZERO, "SINGLE");
		when(registrationRepository.findById(REGISTRATION_ID))
				.thenReturn(Optional.of(registration(t, RegistrationStatus.PENDING_PAYMENT.getValue())));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.checkout(REGISTRATION_ID, PLAYER_ID));

		assertEquals(ErrorCode.PAYMENT_NOT_REQUIRED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-049 · A payment link that is still alive is handed back rather than replaced")
	void TC049_checkout_reusesLiveLink() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		Payment existing = Payment.builder().id(77L).registration(reg).amount(FEE)
				.status(PaymentStatus.PENDING.getValue()).transactionCode("77:abc")
				.checkoutUrl("https://pay.payos.vn/web/77").build();
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(paymentRepository.findByRegistrationId(REGISTRATION_ID)).thenReturn(List.of(existing));
		when(payOSService.getOrderStatus(77L)).thenReturn("PENDING");

		CheckoutResponse response = service.checkout(REGISTRATION_ID, PLAYER_ID);

		assertEquals("https://pay.payos.vn/web/77", response.getCheckoutUrl());
		verify(payOSService, never()).createPaymentLink(anyLong(), anyLong(), anyString());
	}

	@Test
	@DisplayName("TC-050 · A link the player already paid on is settled instead of reissued")
	void TC050_checkout_remotePaidSettlesTheOrder() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		Payment existing = Payment.builder().id(77L).registration(reg).amount(FEE)
				.status(PaymentStatus.PENDING.getValue()).checkoutUrl("https://pay.payos.vn/web/77").build();
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(paymentRepository.findByRegistrationId(REGISTRATION_ID)).thenReturn(List.of(existing));
		when(payOSService.getOrderStatus(77L)).thenReturn("PAID");
		when(paymentRepository.findById(77L)).thenReturn(Optional.of(existing));
		when(tournamentRepository.findByIdWithLock(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(0L);
		when(participantRepository.existsByRegistrationId(REGISTRATION_ID)).thenReturn(false);
		givenRegistrationSaveEchoes();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.checkout(REGISTRATION_ID, PLAYER_ID));

		assertEquals(ErrorCode.PAYMENT_ALREADY_PAID, ex.getErrorCode());
		assertEquals(PaymentStatus.SUCCESS.getValue(), existing.getStatus(),
				"the money arrived even though the webhook did not — the state is repaired here");
	}

	@Test
	@DisplayName("TC-051 · A link the player cancelled at the gateway is replaced with a new one")
	void TC051_checkout_deadLinkIsReplaced() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		Payment dead = Payment.builder().id(77L).registration(reg).amount(FEE)
				.status(PaymentStatus.PENDING.getValue()).checkoutUrl("https://pay.payos.vn/web/77").build();
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		when(paymentRepository.findByRegistrationId(REGISTRATION_ID)).thenReturn(List.of(dead));
		when(payOSService.getOrderStatus(77L)).thenReturn("CANCELLED");
		when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
			Payment p = inv.getArgument(0);
			if (p.getId() == null) p.setId(78L);
			return p;
		});
		when(payOSService.createPaymentLink(eq(78L), eq(200000L), anyString()))
				.thenReturn("https://pay.payos.vn/web/78");

		CheckoutResponse response = service.checkout(REGISTRATION_ID, PLAYER_ID);

		assertEquals(PaymentStatus.CANCELLED.getValue(), dead.getStatus());
		assertEquals("https://pay.payos.vn/web/78", response.getCheckoutUrl(),
				"a dead link must not be handed back — the player would see \"order does not exist\"");
	}

	// ══════════════════════════ markAsPaid ══════════════════════════

	@Test
	@DisplayName("TC-052 · A payment confirmation approves the entry and creates the participant")
	void TC052_markAsPaid_approvesEntry() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		Payment payment = Payment.builder().id(77L).registration(reg).amount(FEE)
				.status(PaymentStatus.PENDING.getValue()).build();
		when(paymentRepository.findById(77L)).thenReturn(Optional.of(payment));
		when(tournamentRepository.findByIdWithLock(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(registrationRepository.countByTournamentIdAndStatus(TOURNAMENT_ID, RegistrationStatus.APPROVED.getValue()))
				.thenReturn(4L);
		when(participantRepository.existsByRegistrationId(REGISTRATION_ID)).thenReturn(false);
		givenRegistrationSaveEchoes();

		service.markAsPaid(77L, "PAYOS-REF-1");

		assertEquals(PaymentStatus.SUCCESS.getValue(), payment.getStatus());
		assertEquals("PAYOS-REF-1", payment.getTransactionCode());
		assertNotNull(payment.getPaidAt());
		assertEquals(RegistrationStatus.APPROVED.getValue(), reg.getStatus());
		verify(participantRepository).save(any(Participant.class));
	}

	@Test
	@DisplayName("TC-053 · A payment for an order that does not exist is ignored")
	void TC053_markAsPaid_unknownOrder() {
		when(paymentRepository.findById(77L)).thenReturn(Optional.empty());

		service.markAsPaid(77L, "PAYOS-REF-1");

		verify(paymentRepository, never()).save(any(Payment.class));
	}

	@Test
	@DisplayName("TC-054 · A repeated payment notification changes nothing")
	void TC054_markAsPaid_idempotent() {
		Payment payment = Payment.builder().id(77L).amount(FEE)
				.status(PaymentStatus.SUCCESS.getValue()).build();
		when(paymentRepository.findById(77L)).thenReturn(Optional.of(payment));

		service.markAsPaid(77L, "PAYOS-REF-1");

		verify(paymentRepository, never()).save(any(Payment.class));
		verify(eventPublisher, never()).publishEvent(any(MailDomainEvent.class));
	}

	@Test
	@DisplayName("TC-055 · A payment with no reference falls back to the order code")
	void TC055_markAsPaid_defaultsTransactionCode() {
		Payment payment = Payment.builder().id(77L).amount(FEE)
				.status(PaymentStatus.PENDING.getValue()).build();
		when(paymentRepository.findById(77L)).thenReturn(Optional.of(payment));

		service.markAsPaid(77L, null);

		assertEquals("77", payment.getTransactionCode());
	}

	@Test
	@DisplayName("TC-056 · Money that arrives after the draw is refused with a refund note")
	void TC056_markAsPaid_afterRosterLockRejects() {
		Tournament t = tournament(TournamentStatus.DRAW_DONE.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		Payment payment = Payment.builder().id(77L).registration(reg).amount(FEE)
				.status(PaymentStatus.PENDING.getValue()).build();
		when(paymentRepository.findById(77L)).thenReturn(Optional.of(payment));
		when(tournamentRepository.findByIdWithLock(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		givenRegistrationSaveEchoes();

		service.markAsPaid(77L, "PAYOS-REF-1");

		assertEquals(RegistrationStatus.REJECTED.getValue(), reg.getStatus());
		assertTrue(reg.getRejectedReason().contains("hoàn tiền"),
				"the player has paid, so the rejection must say how the money comes back");
		verify(participantRepository, never()).save(any(Participant.class));
	}

	@Test
	@DisplayName("TC-057 · A payment on an entry already settled does not re-decide it")
	void TC057_markAsPaid_entryAlreadySettled() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.APPROVED.getValue());
		Payment payment = Payment.builder().id(77L).registration(reg).amount(FEE)
				.status(PaymentStatus.PENDING.getValue()).build();
		when(paymentRepository.findById(77L)).thenReturn(Optional.of(payment));

		service.markAsPaid(77L, "PAYOS-REF-1");

		assertEquals(PaymentStatus.SUCCESS.getValue(), payment.getStatus());
		verify(registrationRepository, never()).save(any(Registration.class));
	}

	// ══════════════════════════ autoCreateParticipant ══════════════════════════

	@Test
	@DisplayName("TC-058 · An entry that already has a participant does not get a second one")
	void TC058_autoCreateParticipant_idempotent() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(participantRepository.existsByRegistrationId(REGISTRATION_ID)).thenReturn(true);

		service.autoCreateParticipant(registration(t, RegistrationStatus.APPROVED.getValue()));

		verify(participantRepository, never()).save(any(Participant.class));
	}

	@Test
	@DisplayName("TC-059 · A singles entry becomes a participant under the player's own name")
	void TC059_autoCreateParticipant_single() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		when(participantRepository.existsByRegistrationId(REGISTRATION_ID)).thenReturn(false);
		when(participantRepository.save(any(Participant.class))).thenAnswer(inv -> inv.getArgument(0));

		service.autoCreateParticipant(registration(t, RegistrationStatus.APPROVED.getValue()));

		ArgumentCaptor<Participant> saved = ArgumentCaptor.forClass(Participant.class);
		verify(participantRepository).save(saved.capture());
		assertEquals("Nguyễn Văn A", saved.getValue().getDisplayName());
		assertEquals(ParticipantStatus.ACTIVE.getValue(), saved.getValue().getStatus());
		verify(participantMemberRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("TC-060 · A doubles entry becomes one participant carrying both names")
	void TC060_autoCreateParticipant_double() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "DOUBLE");
		Registration reg = registration(t, RegistrationStatus.APPROVED.getValue());
		when(participantRepository.existsByRegistrationId(REGISTRATION_ID)).thenReturn(false);
		when(fieldValueRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(List.of(
				RegistrationFieldValue.builder()
						.id(new RegistrationFieldValueId(REGISTRATION_ID, "player2_full_name"))
						.value("Trần Thị B").build(),
				RegistrationFieldValue.builder()
						.id(new RegistrationFieldValueId(REGISTRATION_ID, "player2_phone"))
						.value("0900000002").build()));
		when(participantRepository.save(any(Participant.class))).thenAnswer(inv -> inv.getArgument(0));

		service.autoCreateParticipant(reg);

		ArgumentCaptor<Participant> saved = ArgumentCaptor.forClass(Participant.class);
		verify(participantRepository).save(saved.capture());
		// A three-word name is shortened to first + last so the pair still fits a bracket cell
		assertEquals("Nguyễn A/Trần B", saved.getValue().getDisplayName());
		verify(participantMemberRepository).saveAll(any());
	}

	@Test
	@DisplayName("TC-061 · A doubles entry with no partner recorded still becomes a participant")
	void TC061_autoCreateParticipant_doubleWithoutPartner() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "DOUBLE");
		when(participantRepository.existsByRegistrationId(REGISTRATION_ID)).thenReturn(false);
		when(fieldValueRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(List.of());
		when(participantRepository.save(any(Participant.class))).thenAnswer(inv -> inv.getArgument(0));

		service.autoCreateParticipant(registration(t, RegistrationStatus.APPROVED.getValue()));

		ArgumentCaptor<Participant> saved = ArgumentCaptor.forClass(Participant.class);
		verify(participantRepository).save(saved.capture());
		assertEquals("Nguyễn Văn A", saved.getValue().getDisplayName());
		verify(participantMemberRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("TC-062 · An entry whose player has no email address publishes no mail")
	void TC062_publishEvent_skippedWithoutEmail() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), FEE, "SINGLE");
		Registration reg = registration(t, RegistrationStatus.PENDING_PAYMENT.getValue());
		reg.setUser(User.builder().id(PLAYER_ID).build());
		when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(reg));
		givenRegistrationSaveEchoes();

		service.cancel(REGISTRATION_ID, PLAYER_ID);

		assertEquals(RegistrationStatus.CANCELLED.getValue(), reg.getStatus());
		verify(eventPublisher, never()).publishEvent(any(MailDomainEvent.class));
	}
}
