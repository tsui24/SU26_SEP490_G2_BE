package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateEmployeeAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.BranchManager;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.BranchManagerRepository;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.RoleRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
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
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link AccountServiceImpl}.
 *
 * <p>Mirrors the <b>AccountService</b> sheet in Report 5.1_UnitTests_L1.xlsx one row per test.
 * Spec source: UCS Report 3.1 — UC-07 (7.1/7.2/7.3), UC-08 (8.1/8.2/8.3), UC-09 (9.1/9.2/9.3).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · AccountService — UC-07, UC-08, UC-09")
class AccountServiceImplTest {

	@Mock UserRepository userRepository;
	@Mock RoleRepository roleRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock MinioStorageService minioStorageService;
	@Mock MinioProperties minioProperties;
	@Mock BranchRepository branchRepository;
	@Mock BranchManagerRepository branchManagerRepository;
	@Mock ApplicationEventPublisher eventPublisher;
	@Mock MailContextBuilder mailContextBuilder;

	@InjectMocks AccountServiceImpl accountService;

	private static final Long OWNER_ID = 100L;
	private static final Long OTHER_OWNER_ID = 200L;
	private static final String EMAIL = "nhanvien@example.com";
	private static final String PHONE = "0901234567";
	private static final String RAW_PASSWORD = "Secret@123";
	private static final String HASHED = "$2a$10$hashed";

	private User owner;

	@BeforeEach
	void setUp() {
		owner = user(OWNER_ID, "owner@example.com", "OWNER", UserStatus.ACTIVE);
	}

	// ─────────────────────────── tiện ích dựng dữ liệu ───────────────────────────

	private static Role role(String code) {
		return Role.builder().id(1L).code(code).name(code).build();
	}

	private static User user(Long id, String email, String roleCode, UserStatus status) {
		return User.builder()
				.id(id).email(email).phone(PHONE)
				.passwordHash(HASHED)
				.role(role(roleCode))
				.status(status)
				.build();
	}

	private User employeeOf(Long id, String roleCode, User theOwner) {
		User u = user(id, EMAIL, roleCode, UserStatus.ACTIVE);
		u.setOwner(theOwner);
		u.setProfile(UserProfile.builder().userId(id).user(u).fullName("Nguyễn Văn A").build());
		return u;
	}

	private static Branch branch(Long id, String name) {
		return Branch.builder().id(id).name(name).build();
	}

	/**
	 * Infrastructure collaborators touched while building a response or publishing the email event.
	 * Marked lenient because not every test reaches the response-building stage.
	 */
	private void stubInfra() {
		lenient().when(minioProperties.getBucket()).thenReturn("btms");
		lenient().when(mailContextBuilder.systemContext()).thenReturn(new HashMap<>());
		lenient().when(branchManagerRepository.findByManagerId(anyLong())).thenReturn(List.of());
		lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> {
			User u = inv.getArgument(0);
			if (u.getId() == null) u.setId(999L);
			return u;
		});
	}

	private CreateAccountRequest createAccountRequest() {
		CreateAccountRequest r = new CreateAccountRequest();
		r.setEmail(EMAIL);
		r.setPhone(PHONE);
		r.setPassword(RAW_PASSWORD);
		return r;
	}

	private CreateManagerAccountRequest managerRequest(Boolean manageAll, List<Long> branchIds) {
		CreateManagerAccountRequest r = new CreateManagerAccountRequest();
		r.setEmail(EMAIL);
		r.setPhone(PHONE);
		r.setPassword(RAW_PASSWORD);
		r.setFullName("Trần Thị B");
		r.setDisplayName("Bé B");
		r.setGender("FEMALE");
		r.setBio("Quản lý cơ sở");
		r.setManageAllBranches(manageAll);
		r.setBranchIds(branchIds);
		return r;
	}

	private CreateStaffAccountRequest staffRequest(Long branchId) {
		CreateStaffAccountRequest r = new CreateStaffAccountRequest();
		r.setEmail(EMAIL);
		r.setPhone(PHONE);
		r.setPassword(RAW_PASSWORD);
		r.setFullName("Lê Văn C");
		r.setBranchId(branchId);
		return r;
	}

	/** Stubs one successful employee creation for the given role. */
	private void stubEmployeeCreation(String roleCode) {
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(userRepository.existsByPhone(PHONE)).thenReturn(false);
		when(roleRepository.findByCode(roleCode)).thenReturn(Optional.of(role(roleCode)));
		when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED);
	}

	// ════════════════ createAccount — UC-07.2 + ma trận phân quyền gán vai trò ════════════════

	@Test
	@DisplayName("TC-001 · Admin creates an Owner account")
	void TC001_createAccount_adminCreatesOwner() {
		stubInfra();
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(roleRepository.findByCode("OWNER")).thenReturn(Optional.of(role("OWNER")));
		when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED);

		UserResponse response = accountService.createAccount(createAccountRequest(), RoleCode.OWNER, RoleCode.ADMIN);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository, times(1)).save(saved.capture());
		assertEquals("OWNER", saved.getValue().getRole().getCode());
		assertEquals(UserStatus.ACTIVE, saved.getValue().getStatus());
		assertEquals(HASHED, saved.getValue().getPasswordHash());
		assertNotEquals(RAW_PASSWORD, saved.getValue().getPasswordHash());
		assertEquals("OWNER", response.getRole());
	}

	@Test
	@DisplayName("TC-002 · Admin creates another Admin account")
	void TC002_createAccount_adminCreatesAdmin() {
		stubInfra();
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(role("ADMIN")));
		when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED);

		UserResponse response = accountService.createAccount(createAccountRequest(), RoleCode.ADMIN, RoleCode.ADMIN);

		assertEquals("ADMIN", response.getRole());
	}

	@Test
	@DisplayName("TC-003 · Admin cannot create a Manager directly")
	void TC003_createAccount_adminCannotCreateManager() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createAccount(createAccountRequest(), RoleCode.MANAGER, RoleCode.ADMIN));

		assertEquals(ErrorCode.AUTH_INVALID_ROLE_ASSIGNMENT, ex.getErrorCode());
		verify(userRepository, never()).existsByEmail(anyString());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-004 · Admin cannot create a Player account")
	void TC004_createAccount_adminCannotCreatePlayer() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createAccount(createAccountRequest(), RoleCode.PLAYER, RoleCode.ADMIN));

		assertEquals(ErrorCode.AUTH_INVALID_ROLE_ASSIGNMENT, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-005 · Owner may create a Manager")
	void TC005_createAccount_ownerCreatesManager() {
		stubInfra();
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(roleRepository.findByCode("MANAGER")).thenReturn(Optional.of(role("MANAGER")));
		when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED);

		UserResponse response = accountService.createAccount(createAccountRequest(), RoleCode.MANAGER, RoleCode.OWNER);

		assertEquals("MANAGER", response.getRole());
	}

	@Test
	@DisplayName("TC-006 · Owner cannot create another Owner")
	void TC006_createAccount_ownerCannotCreateOwner() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createAccount(createAccountRequest(), RoleCode.OWNER, RoleCode.OWNER));

		assertEquals(ErrorCode.AUTH_INVALID_ROLE_ASSIGNMENT, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-007 · Manager cannot create any account through this path")
	void TC007_createAccount_managerCallerRejected() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createAccount(createAccountRequest(), RoleCode.STAFF, RoleCode.MANAGER));

		assertEquals(ErrorCode.AUTH_INVALID_ROLE_ASSIGNMENT, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-008 · Email already registered is rejected")
	void TC008_createAccount_duplicateEmail() {
		when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createAccount(createAccountRequest(), RoleCode.OWNER, RoleCode.ADMIN));

		assertEquals(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-009 · Target role missing from the system")
	void TC009_createAccount_roleNotFound() {
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(roleRepository.findByCode("OWNER")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createAccount(createAccountRequest(), RoleCode.OWNER, RoleCode.ADMIN));

		assertEquals(ErrorCode.AUTH_ROLE_NOT_FOUND, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-010 · Creating an Owner does not create a profile")
	void TC010_createAccount_doesNotCreateProfile() {
		stubInfra();
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(roleRepository.findByCode("OWNER")).thenReturn(Optional.of(role("OWNER")));
		when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED);

		UserResponse response = accountService.createAccount(createAccountRequest(), RoleCode.OWNER, RoleCode.ADMIN);

		assertFalse(response.isProfileCompleted());
		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertNull(saved.getValue().getProfile());
	}

	// ════════════════════════ createManagerAccount — UC-08.1 ════════════════════════

	@Test
	@DisplayName("TC-011 · Owner creates a chain-wide Manager")
	void TC011_createManager_manageAllBranches() {
		stubInfra();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		stubEmployeeCreation("MANAGER");

		EmployeeAccountResponse response =
				accountService.createManagerAccount(managerRequest(true, null), OWNER_ID);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertEquals("MANAGER", saved.getValue().getRole().getCode());
		assertEquals(UserStatus.ACTIVE, saved.getValue().getStatus());
		assertTrue(saved.getValue().getManageAllBranches());
		assertNotNull(saved.getValue().getProfile());
		verify(branchManagerRepository, never()).save(any());
		assertEquals("MANAGER", response.getRole());
	}

	@Test
	@DisplayName("TC-012 · Branch-scoped Manager gets the requested branches assigned")
	void TC012_createManager_assignsBranches() {
		stubInfra();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		stubEmployeeCreation("MANAGER");
		when(branchRepository.findByIdAndOwnerId(1L, OWNER_ID)).thenReturn(Optional.of(branch(1L, "CN1")));
		when(branchRepository.findByIdAndOwnerId(2L, OWNER_ID)).thenReturn(Optional.of(branch(2L, "CN2")));

		accountService.createManagerAccount(managerRequest(false, List.of(1L, 2L)), OWNER_ID);

		verify(branchManagerRepository, times(2)).save(any(BranchManager.class));
	}

	@Test
	@DisplayName("TC-013 · Neither chain-wide access nor any branch selected")
	void TC013_createManager_noBranchNoAllAccess_rejected() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createManagerAccount(managerRequest(false, List.of()), OWNER_ID));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-014 · Branch list sent as null")
	void TC014_createManager_nullBranchIds_rejected() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createManagerAccount(managerRequest(false, null), OWNER_ID));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-015 · Null manageAllBranches is read as false")
	void TC015_createManager_nullManageAllBranches() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createManagerAccount(managerRequest(null, List.of()), OWNER_ID));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-016 · Assigning a branch the Owner does not own")
	void TC016_createManager_foreignBranch_rejected() {
		stubInfra();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		stubEmployeeCreation("MANAGER");
		when(branchRepository.findByIdAndOwnerId(99L, OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createManagerAccount(managerRequest(false, List.of(99L)), OWNER_ID));

		assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
		verify(branchManagerRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-017 · Calling Owner does not exist")
	void TC017_createManager_ownerNotFound() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createManagerAccount(managerRequest(true, null), OWNER_ID));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-018 · Email already registered is rejected")
	void TC018_createManager_duplicateEmail() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createManagerAccount(managerRequest(true, null), OWNER_ID));

		assertEquals(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-019 · Creating a Manager also creates the linked profile")
	void TC019_createManager_createsLinkedProfile() {
		stubInfra();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		stubEmployeeCreation("MANAGER");

		accountService.createManagerAccount(managerRequest(true, null), OWNER_ID);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		UserProfile profile = saved.getValue().getProfile();
		assertNotNull(profile);
		assertEquals("Trần Thị B", profile.getFullName());
		assertEquals("Bé B", profile.getDisplayName());
		assertEquals("FEMALE", profile.getGender());
		assertEquals("Quản lý cơ sở", profile.getBio());
	}

	@Test
	@DisplayName("TC-020 · Manager creation raises the account-created email event")
	void TC020_createManager_publishesManagerCreatedEvent() {
		stubInfra();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		stubEmployeeCreation("MANAGER");

		accountService.createManagerAccount(managerRequest(true, null), OWNER_ID);

		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher, times(1)).publishEvent(event.capture());
		MailDomainEvent published = (MailDomainEvent) event.getValue();
		assertEquals("MANAGER_ACCOUNT_CREATED", published.eventType().name());
		assertEquals(EMAIL, published.explicitRecipients().get(0).email());
	}

	// ═══════════════ createStaffAccount(request, ownerId) — UC-08.1 ═══════════════

	@Test
	@DisplayName("TC-021 · Owner creates a Staff member attached to a branch")
	void TC021_createStaff_withBranch() {
		stubInfra();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		stubEmployeeCreation("STAFF");
		when(branchRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.of(branch(5L, "CN5")));

		EmployeeAccountResponse response = accountService.createStaffAccount(staffRequest(5L), OWNER_ID);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertEquals("STAFF", saved.getValue().getRole().getCode());
		assertEquals(UserStatus.ACTIVE, saved.getValue().getStatus());
		assertEquals(5L, saved.getValue().getBranch().getId());
		assertEquals("STAFF", response.getRole());
	}

	@Test
	@DisplayName("TC-022 · Attaching Staff to a branch the Owner does not own")
	void TC022_createStaff_foreignBranch_rejected() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		// Not using stubEmployeeCreation: the flow stops at the branch lookup, before any password hashing
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(userRepository.existsByPhone(PHONE)).thenReturn(false);
		when(roleRepository.findByCode("STAFF")).thenReturn(Optional.of(role("STAFF")));
		when(branchRepository.findByIdAndOwnerId(99L, OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createStaffAccount(staffRequest(99L), OWNER_ID));

		assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-023 · Creating Staff without naming a branch")
	void TC023_createStaff_nullBranch() {
		stubInfra();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		stubEmployeeCreation("STAFF");

		accountService.createStaffAccount(staffRequest(null), OWNER_ID);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertNull(saved.getValue().getBranch());
		verifyNoInteractions(branchRepository);
	}

	@Test
	@DisplayName("TC-024 · Phone number already in use")
	void TC024_createStaff_duplicatePhone() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(userRepository.existsByPhone(PHONE)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createStaffAccount(staffRequest(5L), OWNER_ID));

		assertEquals(ErrorCode.AUTH_PHONE_ALREADY_EXISTS, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-025 · Staff creation raises the account-created email event")
	void TC025_createStaff_publishesStaffCreatedEvent() {
		stubInfra();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		stubEmployeeCreation("STAFF");

		accountService.createStaffAccount(staffRequest(null), OWNER_ID);

		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher, times(1)).publishEvent(event.capture());
		MailDomainEvent published = (MailDomainEvent) event.getValue();
		assertEquals("STAFF_ACCOUNT_CREATED", published.eventType().name());
	}

	// ═════════ createStaffAccount(request, ownerId, allowedBranchIds) — UC-09.1 ═════════

	@Test
	@DisplayName("TC-026 · Manager creates Staff inside an assigned branch")
	void TC026_managerCreateStaff_inScope() {
		stubInfra();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		stubEmployeeCreation("STAFF");
		when(branchRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.of(branch(5L, "CN5")));

		EmployeeAccountResponse response =
				accountService.createStaffAccount(staffRequest(5L), OWNER_ID, List.of(5L, 6L));

		assertEquals("STAFF", response.getRole());
		verify(userRepository).save(any(User.class));
	}

	@Test
	@DisplayName("TC-027 · Manager creates Staff for a branch outside their scope")
	void TC027_managerCreateStaff_outOfScope_rejected() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createStaffAccount(staffRequest(9L), OWNER_ID, List.of(5L, 6L)));

		assertEquals(ErrorCode.BRANCH_ACCESS_DENIED, ex.getErrorCode());
		verifyNoInteractions(userRepository);
	}

	@Test
	@DisplayName("TC-028 · Manager omits the branch entirely")
	void TC028_managerCreateStaff_nullBranch_rejected() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.createStaffAccount(staffRequest(null), OWNER_ID, List.of(5L)));

		assertEquals(ErrorCode.BRANCH_ACCESS_DENIED, ex.getErrorCode());
		verifyNoInteractions(userRepository);
	}

	@Test
	@DisplayName("TC-029 · Owner calling the same method with a null scope skips the branch check")
	void TC029_ownerCreateStaff_nullScope_bypassesBranchCheck() {
		stubInfra();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		stubEmployeeCreation("STAFF");
		when(branchRepository.findByIdAndOwnerId(9L, OWNER_ID)).thenReturn(Optional.of(branch(9L, "CN9")));

		EmployeeAccountResponse response =
				accountService.createStaffAccount(staffRequest(9L), OWNER_ID, null);

		assertEquals("STAFF", response.getRole());
	}

	// ═══════════════════════════ updateEmployee — UC-08.2 ═══════════════════════════

	private UpdateEmployeeAccountRequest updateRequest() {
		return new UpdateEmployeeAccountRequest();
	}

	@Test
	@DisplayName("TC-030 · Owner updates one of their own employees")
	void TC030_updateEmployee_happyPath() {
		stubInfra();
		User staff = employeeOf(50L, "STAFF", owner);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));

		UpdateEmployeeAccountRequest request = updateRequest();
		request.setFullName("Tên Mới");
		request.setBio("Tiểu sử mới");

		accountService.updateEmployee(OWNER_ID, 50L, request);

		assertEquals("Tên Mới", staff.getProfile().getFullName());
		assertEquals("Tiểu sử mới", staff.getProfile().getBio());
		verify(userRepository).save(staff);
	}

	@Test
	@DisplayName("TC-031 · Editing an employee belonging to another Owner")
	void TC031_updateEmployee_foreignEmployee_rejected() {
		User otherOwner = user(OTHER_OWNER_ID, "other@example.com", "OWNER", UserStatus.ACTIVE);
		User staff = employeeOf(50L, "STAFF", otherOwner);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.updateEmployee(OWNER_ID, 50L, updateRequest()));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-032 · Editing an account that is neither Staff nor Manager")
	void TC032_updateEmployee_invalidRole_rejected() {
		User player = employeeOf(50L, "PLAYER", owner);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(50L)).thenReturn(Optional.of(player));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.updateEmployee(OWNER_ID, 50L, updateRequest()));

		assertEquals(ErrorCode.INVALID_EMPLOYEE_ROLE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-033 · Switching to a phone number someone else already uses")
	void TC033_updateEmployee_duplicatePhone_rejected() {
		User staff = employeeOf(50L, "STAFF", owner);
		staff.setPhone("0901111111");
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));
		when(userRepository.existsByPhone("0902222222")).thenReturn(true);

		UpdateEmployeeAccountRequest request = updateRequest();
		request.setPhone("0902222222");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.updateEmployee(OWNER_ID, 50L, request));

		assertEquals(ErrorCode.AUTH_PHONE_ALREADY_EXISTS, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-034 · Resubmitting the current phone number raises no conflict")
	void TC034_updateEmployee_unchangedPhone_skipsDuplicateCheck() {
		stubInfra();
		User staff = employeeOf(50L, "STAFF", owner);
		staff.setPhone("0901111111");
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));

		UpdateEmployeeAccountRequest request = updateRequest();
		request.setPhone("0901111111");

		accountService.updateEmployee(OWNER_ID, 50L, request);

		verify(userRepository, never()).existsByPhone(anyString());
		verify(userRepository).save(staff);
	}

	@Test
	@DisplayName("TC-035 · Revoking chain-wide access without naming replacement branches")
	void TC035_updateEmployee_removeAllAccessWithoutBranches_rejected() {
		User manager = employeeOf(50L, "MANAGER", owner);
		manager.setManageAllBranches(true);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(50L)).thenReturn(Optional.of(manager));

		UpdateEmployeeAccountRequest request = updateRequest();
		request.setManageAllBranches(false);
		request.setBranchIds(List.of());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.updateEmployee(OWNER_ID, 50L, request));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-036 · Promoting a Manager to chain-wide access")
	void TC036_updateEmployee_switchToManageAll() {
		stubInfra();
		User manager = employeeOf(50L, "MANAGER", owner);
		manager.setManageAllBranches(false);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(50L)).thenReturn(Optional.of(manager));

		UpdateEmployeeAccountRequest request = updateRequest();
		request.setManageAllBranches(true);

		accountService.updateEmployee(OWNER_ID, 50L, request);

		assertTrue(manager.getManageAllBranches());
		verify(branchManagerRepository).deleteByManagerId(50L);
		verify(branchManagerRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-037 · Replacing a Manager branch list wholesale")
	void TC037_updateEmployee_replacesBranchAssignments() {
		stubInfra();
		User manager = employeeOf(50L, "MANAGER", owner);
		manager.setManageAllBranches(true);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(50L)).thenReturn(Optional.of(manager));
		when(branchRepository.findByIdAndOwnerId(3L, OWNER_ID)).thenReturn(Optional.of(branch(3L, "CN3")));

		UpdateEmployeeAccountRequest request = updateRequest();
		request.setManageAllBranches(false);
		request.setBranchIds(List.of(3L));

		accountService.updateEmployee(OWNER_ID, 50L, request);

		verify(branchManagerRepository).deleteByManagerId(50L);
		verify(branchManagerRepository, times(1)).save(any(BranchManager.class));
		assertFalse(manager.getManageAllBranches());
	}

	@Test
	@DisplayName("TC-038 · Moving a Staff member to a different branch")
	void TC038_updateEmployee_movesStaffBranch() {
		stubInfra();
		User staff = employeeOf(50L, "STAFF", owner);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));
		when(branchRepository.findByIdAndOwnerId(7L, OWNER_ID)).thenReturn(Optional.of(branch(7L, "CN7")));

		UpdateEmployeeAccountRequest request = updateRequest();
		request.setBranchId(7L);

		accountService.updateEmployee(OWNER_ID, 50L, request);

		assertEquals(7L, staff.getBranch().getId());
	}

	@Test
	@DisplayName("TC-039 · Employee without a profile gets one created")
	void TC039_updateEmployee_createsMissingProfile() {
		stubInfra();
		User staff = user(50L, EMAIL, "STAFF", UserStatus.ACTIVE);
		staff.setOwner(owner);
		staff.setProfile(null);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));

		UpdateEmployeeAccountRequest request = updateRequest();
		request.setFullName("Tên Mới");

		accountService.updateEmployee(OWNER_ID, 50L, request);

		assertNotNull(staff.getProfile());
		assertEquals("Tên Mới", staff.getProfile().getFullName());
	}

	@Test
	@DisplayName("TC-040 · Employee does not exist")
	void TC040_updateEmployee_notFound() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRepository.findById(9999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.updateEmployee(OWNER_ID, 9999L, updateRequest()));

		assertEquals(ErrorCode.EMPLOYEE_NOT_FOUND, ex.getErrorCode());
	}

	// ═════════════════════════════ getUsers — UC-07.1 ═════════════════════════════

	private Page<User> pageOf(User... users) {
		return new PageImpl<>(new ArrayList<>(List.of(users)), PageRequest.of(0, 10), users.length);
	}

	@Test
	@DisplayName("TC-041 · Admin filters the account list by role")
	void TC041_getUsers_filterByRole() {
		stubInfra();
		when(userRepository.searchUsers(eq("OWNER"), eq("Nam"), any(Pageable.class)))
				.thenReturn(pageOf(user(1L, "a@x.com", "OWNER", UserStatus.ACTIVE),
						user(2L, "b@x.com", "OWNER", UserStatus.ACTIVE)));

		PageResponse<EmployeeAccountResponse> response = accountService.getUsers("OWNER", "Nam", 0, 10);

		assertEquals(2, response.getContent().size());
		verify(userRepository).searchUsers(eq("OWNER"), eq("Nam"), any(Pageable.class));
	}

	@Test
	@DisplayName("TC-042 · Invalid role filter")
	void TC042_getUsers_invalidRoleFilter() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.getUsers("SUPERUSER", null, 0, 10));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		verify(userRepository, never()).searchUsers(anyString(), anyString(), any());
	}

	@Test
	@DisplayName("TC-043 · Blank filters are ignored")
	void TC043_getUsers_blankFiltersIgnored() {
		when(userRepository.searchUsers(eq(null), eq(null), any(Pageable.class)))
				.thenReturn(Page.empty(PageRequest.of(0, 10)));

		accountService.getUsers("   ", "   ", 0, 10);

		verify(userRepository).searchUsers(eq(null), eq(null), any(Pageable.class));
	}

	@Test
	@DisplayName("TC-044 · Lower-case role filter is normalised")
	void TC044_getUsers_lowercaseRoleNormalized() {
		when(userRepository.searchUsers(eq("OWNER"), eq(null), any(Pageable.class)))
				.thenReturn(Page.empty(PageRequest.of(0, 10)));

		accountService.getUsers("owner", null, 0, 10);

		verify(userRepository).searchUsers(eq("OWNER"), eq(null), any(Pageable.class));
	}

	@Test
	@DisplayName("TC-045 · List is ordered most recent first")
	void TC045_getUsers_sortedByIdDesc() {
		when(userRepository.searchUsers(any(), any(), any(Pageable.class)))
				.thenReturn(Page.empty(PageRequest.of(0, 10)));

		accountService.getUsers(null, null, 0, 10);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(userRepository).searchUsers(any(), any(), pageable.capture());
		Sort.Order order = pageable.getValue().getSort().getOrderFor("id");
		assertNotNull(order);
		assertTrue(order.isDescending());
		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(10, pageable.getValue().getPageSize());
	}

	@Test
	@DisplayName("TC-046 · Nothing matches the query")
	void TC046_getUsers_emptyResult() {
		when(userRepository.searchUsers(eq("ADMIN"), eq("khongcoai"), any(Pageable.class)))
				.thenReturn(Page.empty(PageRequest.of(0, 10)));

		PageResponse<EmployeeAccountResponse> response =
				accountService.getUsers("ADMIN", "khongcoai", 0, 10);

		assertTrue(response.getContent().isEmpty());
	}

	// ═══════════════════════════ getEmployees — UC-08.2 ═══════════════════════════

	@Test
	@DisplayName("TC-047 · Owner lists employees filtered by role")
	void TC047_getEmployees_filterByRole() {
		stubInfra();
		when(userRepository.searchEmployees(eq(OWNER_ID), eq("MANAGER"), eq(null), any(Pageable.class)))
				.thenReturn(pageOf(user(1L, "m@x.com", "MANAGER", UserStatus.ACTIVE)));

		PageResponse<EmployeeAccountResponse> response =
				accountService.getEmployees(OWNER_ID, "MANAGER", null, 0, 10);

		assertEquals(1, response.getContent().size());
		verify(userRepository).searchEmployees(eq(OWNER_ID), eq("MANAGER"), eq(null), any(Pageable.class));
	}

	@Test
	@DisplayName("TC-048 · Role filter outside the employee range")
	void TC048_getEmployees_invalidRoleFilter() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.getEmployees(OWNER_ID, "ADMIN", null, 0, 10));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		verify(userRepository, never()).searchEmployees(anyLong(), anyString(), anyString(), any());
	}

	// ══════════════════ getStaffsByManagerBranches — UC-09.2 ══════════════════

	@Test
	@DisplayName("TC-049 · Manager lists Staff across their assigned branches")
	void TC049_getStaffsByManagerBranches_inScope() {
		stubInfra();
		when(userRepository.searchStaffsByManagerBranches(eq(List.of(1L, 2L)), eq(null), any(Pageable.class)))
				.thenReturn(pageOf(user(1L, "s@x.com", "STAFF", UserStatus.ACTIVE)));

		PageResponse<EmployeeAccountResponse> response =
				accountService.getStaffsByManagerBranches(List.of(1L, 2L), null, 0, 10);

		assertEquals(1, response.getContent().size());
	}

	@Test
	@DisplayName("TC-050 · Manager holds no branch assignment yet")
	void TC050_getStaffsByManagerBranches_emptyScope() {
		PageResponse<EmployeeAccountResponse> response =
				accountService.getStaffsByManagerBranches(List.of(), null, 0, 10);

		assertTrue(response.getContent().isEmpty());
		verify(userRepository, never()).searchStaffsByManagerBranches(any(), any(), any());
	}

	@Test
	@DisplayName("TC-051 · Branch list sent as null")
	void TC051_getStaffsByManagerBranches_nullScope() {
		PageResponse<EmployeeAccountResponse> response =
				accountService.getStaffsByManagerBranches(null, null, 0, 10);

		assertTrue(response.getContent().isEmpty());
		verify(userRepository, never()).searchStaffsByManagerBranches(any(), any(), any());
	}

	// ══════════════════ getStaffDetailForManager — UC-09.2 ══════════════════

	private User staffInBranch(Long id, Long branchId) {
		User staff = user(id, EMAIL, "STAFF", UserStatus.ACTIVE);
		staff.setBranch(branch(branchId, "CN" + branchId));
		staff.setProfile(UserProfile.builder().userId(id).user(staff).fullName("Nhân viên").build());
		return staff;
	}

	@Test
	@DisplayName("TC-052 · Manager opens a Staff record inside their scope")
	void TC052_getStaffDetail_inScope() {
		stubInfra();
		when(userRepository.findByIdAndStatus(50L, UserStatus.ACTIVE))
				.thenReturn(Optional.of(staffInBranch(50L, 1L)));

		EmployeeAccountResponse response = accountService.getStaffDetailForManager(List.of(1L, 2L), 50L);

		assertEquals("STAFF", response.getRole());
		assertEquals(1L, response.getBranchId());
	}

	@Test
	@DisplayName("TC-053 · Account is not in the active state")
	void TC053_getStaffDetail_notActive() {
		when(userRepository.findByIdAndStatus(50L, UserStatus.ACTIVE)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.getStaffDetailForManager(List.of(1L), 50L));

		assertEquals(ErrorCode.EMPLOYEE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-054 · Account is not a Staff member")
	void TC054_getStaffDetail_notStaff() {
		User manager = user(50L, EMAIL, "MANAGER", UserStatus.ACTIVE);
		when(userRepository.findByIdAndStatus(50L, UserStatus.ACTIVE)).thenReturn(Optional.of(manager));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.getStaffDetailForManager(List.of(1L), 50L));

		assertEquals(ErrorCode.INVALID_EMPLOYEE_ROLE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-055 · Staff member sits in a branch outside the scope")
	void TC055_getStaffDetail_outOfScope() {
		when(userRepository.findByIdAndStatus(50L, UserStatus.ACTIVE))
				.thenReturn(Optional.of(staffInBranch(50L, 9L)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.getStaffDetailForManager(List.of(1L, 2L), 50L));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-056 · Staff member has no branch attached")
	void TC056_getStaffDetail_staffWithoutBranch() {
		User staff = user(50L, EMAIL, "STAFF", UserStatus.ACTIVE);
		staff.setBranch(null);
		when(userRepository.findByIdAndStatus(50L, UserStatus.ACTIVE)).thenReturn(Optional.of(staff));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.getStaffDetailForManager(List.of(1L), 50L));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}

	// ═══════════ deactivateStaffForManager / reactivateStaffForManager — UC-09.3 ═══════════

	@Test
	@DisplayName("TC-057 · Manager locks a Staff account")
	void TC057_deactivateStaffForManager_happyPath() {
		stubInfra();
		User staff = staffInBranch(50L, 1L);
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));

		accountService.deactivateStaffForManager(List.of(1L), 50L);

		assertEquals(UserStatus.LOCKED, staff.getStatus());
		verify(userRepository, times(1)).save(staff);
	}

	/**
	 * DEF-W0-04 — SPEC vs CODE CONFLICT.
	 *
	 * <p>UC-09.3 BR-01 reads "Only STAFF or MANAGER accounts can be deactivated" but the code
	 * accepts STAFF alone. This Then follows the CODE rather than the spec, because implementing
	 * the spec literally would open a real privilege hole: Managers locking one another out.
	 */
	@Test
	@DisplayName("TC-058 · Manager cannot lock another Manager")
	void TC058_deactivateStaffForManager_managerTarget_rejected() {
		User manager = user(50L, EMAIL, "MANAGER", UserStatus.ACTIVE);
		when(userRepository.findById(50L)).thenReturn(Optional.of(manager));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.deactivateStaffForManager(List.of(1L), 50L));

		assertEquals(ErrorCode.INVALID_EMPLOYEE_ROLE, ex.getErrorCode());
		assertEquals(UserStatus.ACTIVE, manager.getStatus());
	}

	@Test
	@DisplayName("TC-059 · Locking a Staff member outside the branch scope")
	void TC059_deactivateStaffForManager_outOfScope() {
		User staff = staffInBranch(50L, 9L);
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.deactivateStaffForManager(List.of(1L, 2L), 50L));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
		assertEquals(UserStatus.ACTIVE, staff.getStatus());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-060 · Manager unlocks a Staff account")
	void TC060_reactivateStaffForManager_happyPath() {
		stubInfra();
		User staff = staffInBranch(50L, 1L);
		staff.setStatus(UserStatus.LOCKED);
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));

		accountService.reactivateStaffForManager(List.of(1L), 50L);

		assertEquals(UserStatus.ACTIVE, staff.getStatus());
		verify(userRepository, times(1)).save(staff);
	}

	@Test
	@DisplayName("TC-061 · Locking an account that does not exist")
	void TC061_deactivateStaffForManager_notFound() {
		when(userRepository.findById(9999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.deactivateStaffForManager(List.of(1L), 9999L));

		assertEquals(ErrorCode.EMPLOYEE_NOT_FOUND, ex.getErrorCode());
	}

	// ═══════ deactivateEmployee / reactivateEmployee / getEmployeeDetail — UC-07.3 + UC-08.3 ═══════

	@Test
	@DisplayName("TC-062 · Owner locks one of their own employees")
	void TC062_deactivateEmployee_byOwner() {
		stubInfra();
		User staff = employeeOf(50L, "STAFF", owner);
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));

		accountService.deactivateEmployee(OWNER_ID, 50L);

		assertEquals(UserStatus.LOCKED, staff.getStatus());
		verify(userRepository, times(1)).save(staff);
		verify(userRepository, never()).delete(any());
	}

	@Test
	@DisplayName("TC-063 · Admin locks an account, bypassing the ownership check")
	void TC063_deactivateEmployee_byAdmin_skipsOwnershipCheck() {
		stubInfra();
		User otherOwner = user(OTHER_OWNER_ID, "other@example.com", "OWNER", UserStatus.ACTIVE);
		User staff = employeeOf(50L, "STAFF", otherOwner);
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));

		// An Admin calls with ownerId = null and holds system-wide authority
		accountService.deactivateEmployee(null, 50L);

		assertEquals(UserStatus.LOCKED, staff.getStatus());
		verify(userRepository).save(staff);
	}

	@Test
	@DisplayName("TC-064 · Owner locks an employee belonging to another Owner")
	void TC064_deactivateEmployee_foreignEmployee_rejected() {
		User otherOwner = user(OTHER_OWNER_ID, "other@example.com", "OWNER", UserStatus.ACTIVE);
		User staff = employeeOf(50L, "STAFF", otherOwner);
		when(userRepository.findById(50L)).thenReturn(Optional.of(staff));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.deactivateEmployee(OWNER_ID, 50L));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
		assertEquals(UserStatus.ACTIVE, staff.getStatus());
	}

	@Test
	@DisplayName("TC-065 · Locking an account whose role is out of range")
	void TC065_deactivateEmployee_invalidRole_rejected() {
		User targetOwner = user(50L, EMAIL, "OWNER", UserStatus.ACTIVE);
		when(userRepository.findById(50L)).thenReturn(Optional.of(targetOwner));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.deactivateEmployee(null, 50L));

		assertEquals(ErrorCode.INVALID_EMPLOYEE_ROLE, ex.getErrorCode());
		assertEquals(UserStatus.ACTIVE, targetOwner.getStatus());
	}

	@Test
	@DisplayName("TC-066 · Locking an account that does not exist")
	void TC066_deactivateEmployee_notFound() {
		when(userRepository.findById(9999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.deactivateEmployee(null, 9999L));

		assertEquals(ErrorCode.EMPLOYEE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-067 · Owner unlocks an employee")
	void TC067_reactivateEmployee_happyPath() {
		stubInfra();
		User manager = employeeOf(50L, "MANAGER", owner);
		manager.setStatus(UserStatus.LOCKED);
		when(userRepository.findById(50L)).thenReturn(Optional.of(manager));

		accountService.reactivateEmployee(OWNER_ID, 50L);

		assertEquals(UserStatus.ACTIVE, manager.getStatus());
		verify(userRepository, times(1)).save(manager);
	}

	@Test
	@DisplayName("TC-068 · Owner opens an employee record")
	void TC068_getEmployeeDetail_happyPath() {
		stubInfra();
		User manager = employeeOf(50L, "MANAGER", owner);
		when(userRepository.findByIdAndStatus(50L, UserStatus.ACTIVE)).thenReturn(Optional.of(manager));

		EmployeeAccountResponse response = accountService.getEmployeeDetail(OWNER_ID, 50L);

		assertEquals(EMAIL, response.getEmail());
		assertEquals("MANAGER", response.getRole());
		assertEquals("ACTIVE", response.getStatus());
		assertEquals("Nguyễn Văn A", response.getFullName());
	}

	@Test
	@DisplayName("TC-069 · Opening a locked employee record")
	void TC069_getEmployeeDetail_lockedEmployee() {
		when(userRepository.findByIdAndStatus(50L, UserStatus.ACTIVE)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.getEmployeeDetail(OWNER_ID, 50L));

		assertEquals(ErrorCode.EMPLOYEE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-070 · Opening an employee record belonging to another Owner")
	void TC070_getEmployeeDetail_foreignEmployee_rejected() {
		User otherOwner = user(OTHER_OWNER_ID, "other@example.com", "OWNER", UserStatus.ACTIVE);
		User staff = employeeOf(50L, "STAFF", otherOwner);
		when(userRepository.findByIdAndStatus(50L, UserStatus.ACTIVE)).thenReturn(Optional.of(staff));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accountService.getEmployeeDetail(OWNER_ID, 50L));

		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
	}
}
