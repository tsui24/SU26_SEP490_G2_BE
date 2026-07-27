package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateEmployeeAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.BranchManager;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.repository.BranchManagerRepository;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.RoleRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.AccountService;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.util.AvatarUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final MinioStorageService minioStorageService;
	private final MinioProperties minioProperties;
	private final BranchRepository branchRepository;
	private final BranchManagerRepository branchManagerRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final MailContextBuilder mailContextBuilder;

	@Override
	@Transactional
	public UserResponse createAccount(CreateAccountRequest request, RoleCode targetRole, RoleCode callerRole) {
		validateRoleAssignment(callerRole, targetRole);

		if (userRepository.existsByEmail(request.getEmail())) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}

		Role role = roleRepository.findByCode(targetRole.getCode())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ROLE_NOT_FOUND));

		User user = User.builder()
				.email(request.getEmail())
				.phone(request.getPhone())
				.passwordHash(passwordEncoder.encode(request.getPassword()))
				.role(role)
				.status(UserStatus.ACTIVE)
				.build();

		user = userRepository.save(user);
		return toUserResponse(user, false);
	}

	@Override
	@Transactional
	public EmployeeAccountResponse createManagerAccount(CreateManagerAccountRequest request, Long ownerId) {
		User owner = userRepository.findById(ownerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		boolean manageAllBranches = Boolean.TRUE.equals(request.getManageAllBranches());
		if (!manageAllBranches && (request.getBranchIds() == null || request.getBranchIds().isEmpty())) {
			// Không cho tạo Manager "mồ côi" — không quản lý chi nhánh nào thì không thể thao tác gì.
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST,
					"Phải chọn ít nhất 1 chi nhánh khi không cấp quyền toàn chuỗi");
		}

		User manager = createEmployeeWithProfile(
				request.getEmail(), request.getPhone(), request.getPassword(),
				request.getFullName(), request.getDisplayName(), request.getAvatarUrl(),
				request.getDateOfBirth(), request.getGender(), request.getBio(),
				RoleCode.MANAGER, RoleCode.OWNER, owner, manageAllBranches, null);

		if (!manageAllBranches && request.getBranchIds() != null) {
			assignBranches(owner, manager, request.getBranchIds());
		}
		return toEmployeeResponse(manager, manager.getProfile());
	}

	@Override
	@Transactional
	public EmployeeAccountResponse createStaffAccount(CreateStaffAccountRequest request, Long ownerId) {
		return createStaffAccount(request, ownerId, null);
	}

	@Override
	@Transactional
	public EmployeeAccountResponse createStaffAccount(
			CreateStaffAccountRequest request, Long ownerId, List<Long> managerAllowedBranchIds) {
		if (managerAllowedBranchIds != null
				&& (request.getBranchId() == null || !managerAllowedBranchIds.contains(request.getBranchId()))) {
			throw new BusinessException(ErrorCode.BRANCH_ACCESS_DENIED);
		}
		User owner = ownerId != null ? userRepository.findById(ownerId).orElse(null) : null;
		User staff = createEmployeeWithProfile(
				request.getEmail(), request.getPhone(), request.getPassword(),
				request.getFullName(), request.getDisplayName(), request.getAvatarUrl(),
				request.getDateOfBirth(), request.getGender(), request.getBio(),
				RoleCode.STAFF, RoleCode.OWNER, owner, false, request.getBranchId());
		return toEmployeeResponse(staff, staff.getProfile());
	}

	private User createEmployeeWithProfile(
			String email, String phone, String password,
			String fullName, String displayName, String avatarUrl,
			java.time.LocalDate dateOfBirth, String gender, String bio,
			RoleCode targetRole, RoleCode callerRole, User owner, boolean manageAllBranches, Long branchId) {

		validateRoleAssignment(callerRole, targetRole);

		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}
		if (phone != null && !phone.isBlank() && userRepository.existsByPhone(phone)) {
			throw new BusinessException(ErrorCode.AUTH_PHONE_ALREADY_EXISTS);
		}

		Role role = roleRepository.findByCode(targetRole.getCode())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ROLE_NOT_FOUND));

		Branch branch = null;
		if (targetRole == RoleCode.STAFF && branchId != null) {
			if (owner == null) {
				throw new BusinessException(ErrorCode.BRANCH_NOT_FOUND);
			}
			branch = branchRepository.findByIdAndOwnerId(branchId, owner.getId())
					.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND));
		}

		User user = User.builder()
				.email(email).phone(phone)
				.passwordHash(passwordEncoder.encode(password))
				.role(role).status(UserStatus.ACTIVE)
				.owner(owner)
				.manageAllBranches(manageAllBranches)
				.branch(branch)
				.build();

		UserProfile profile = UserProfile.builder()
				.user(user).fullName(fullName).displayName(displayName)
				.avatarUrl(AvatarUrlResolver.normalizeForStorage(avatarUrl, minioProperties.getBucket()))
				.dateOfBirth(dateOfBirth)
				.gender(gender).bio(bio)
				.build();

		user.setProfile(profile);
		user = userRepository.save(user);
		publishAccountCreatedEvent(targetRole, user);
		return user;
	}

	private void publishAccountCreatedEvent(RoleCode targetRole, User user) {
		EmailEventType eventType = targetRole == RoleCode.MANAGER
				? EmailEventType.MANAGER_ACCOUNT_CREATED
				: EmailEventType.STAFF_ACCOUNT_CREATED;
		Map<String, Object> variables = new java.util.HashMap<>(mailContextBuilder.systemContext());
		mailContextBuilder.putUser(variables, user);
		eventPublisher.publishEvent(MailDomainEvent.builder()
				.eventType(eventType)
				.variables(variables)
				.explicitRecipients(List.of(new MailRecipient(user.getId(), user.getEmail())))
				.entityKey("USER-" + user.getId())
				.build());
	}

	private void assignBranches(User owner, User manager, List<Long> branchIds) {
		for (Long branchId : branchIds) {
			Branch branch = branchRepository.findByIdAndOwnerId(branchId, owner.getId())
					.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND));
			branchManagerRepository.save(BranchManager.builder()
					.branch(branch).manager(manager).assignedBy(owner).build());
		}
	}

	@Override
	@Transactional
	public EmployeeAccountResponse updateEmployee(
			Long ownerId, Long employeeId, UpdateEmployeeAccountRequest request) {
		User owner = userRepository.findById(ownerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		User employee = userRepository.findById(employeeId)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
		validateEmployeeRole(employee);
		if (employee.getOwner() == null || !employee.getOwner().getId().equals(ownerId)) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}

		if (request.getPhone() != null) {
			if (!request.getPhone().equals(employee.getPhone()) && userRepository.existsByPhone(request.getPhone())) {
				throw new BusinessException(ErrorCode.AUTH_PHONE_ALREADY_EXISTS);
			}
			employee.setPhone(request.getPhone());
		}

		UserProfile profile = employee.getProfile();
		if (profile == null) {
			profile = UserProfile.builder().user(employee).build();
			employee.setProfile(profile);
		}
		if (request.getFullName() != null) {
			profile.setFullName(request.getFullName());
		}
		if (request.getDisplayName() != null) {
			profile.setDisplayName(request.getDisplayName());
		}
		if (request.getAvatarUrl() != null) {
			profile.setAvatarUrl(AvatarUrlResolver.normalizeForStorage(request.getAvatarUrl(), minioProperties.getBucket()));
		}
		if (request.getDateOfBirth() != null) {
			profile.setDateOfBirth(request.getDateOfBirth());
		}
		if (request.getGender() != null) {
			profile.setGender(request.getGender());
		}
		if (request.getBio() != null) {
			profile.setBio(request.getBio());
		}

		boolean isManager = "MANAGER".equals(employee.getRole().getCode());
		if (isManager && request.getManageAllBranches() != null) {
			boolean manageAllBranches = request.getManageAllBranches();
			if (!manageAllBranches && (request.getBranchIds() == null || request.getBranchIds().isEmpty())) {
				// Không cho gỡ hết quyền chi nhánh của Manager mà không gán chi nhánh thay thế —
				// tránh Manager bị "mồ côi" (không quản lý gì) do gửi thiếu branchIds.
				throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST,
						"Phải chọn ít nhất 1 chi nhánh khi bỏ quyền quản lý toàn chuỗi");
			}
			employee.setManageAllBranches(manageAllBranches);
			branchManagerRepository.deleteByManagerId(employeeId);
			if (!manageAllBranches) {
				assignBranches(owner, employee, request.getBranchIds());
			}
		}

		boolean isStaff = "STAFF".equals(employee.getRole().getCode());
		if (isStaff && request.getBranchId() != null) {
			Branch branch = branchRepository.findByIdAndOwnerId(request.getBranchId(), owner.getId())
					.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND));
			employee.setBranch(branch);
		}

		employee = userRepository.save(employee);
		return toEmployeeResponse(employee, employee.getProfile());
	}


	@Override
	@Transactional(readOnly = true)
	public PageResponse<EmployeeAccountResponse> getUsers(String role, String search, int page, int size) {
		String searchParam = (search == null || search.trim().isEmpty()) ? null : search.trim();
		String roleParam = (role == null || role.trim().isEmpty()) ? null : role.trim().toUpperCase();

		if (roleParam != null && !roleParam.equals("ADMIN") && !roleParam.equals("STAFF")
				&& !roleParam.equals("MANAGER") && !roleParam.equals("PLAYER") && !roleParam.equals("OWNER")) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST,
					"Bộ lọc role phải là ADMIN, STAFF, MANAGER, PLAYER hoặc OWNER");
		}

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

		Page<User> userPage = userRepository.searchUsers(roleParam, searchParam, pageable);
		return toEmployeeResponsePage(userPage);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<EmployeeAccountResponse>getEmployees(Long ownerId, String role, String search, int page, int size) {
		String searchParam = (search == null || search.trim().isEmpty()) ? null : search.trim();
		String roleParam = (role == null || role.trim().isEmpty()) ? null : role.trim().toUpperCase();

		if (roleParam != null && !roleParam.equals("STAFF") && !roleParam.equals("MANAGER")) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Bộ lọc role phải là STAFF hoặc MANAGER");
		}

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

		Page<User> userPage = userRepository.searchEmployees(ownerId, roleParam, searchParam, pageable);
		return toEmployeeResponsePage(userPage);
	}


	@Override
	@Transactional(readOnly = true)
	public PageResponse<EmployeeAccountResponse> getStaffsByManager(Long ownerId, String search, int page, int size) {
		String searchParam = (search == null || search.trim().isEmpty()) ? null : search.trim();

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

		Page<User> userPage = userRepository.searchStaffsByManager(ownerId, searchParam, pageable);
		return toEmployeeResponsePage(userPage);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<EmployeeAccountResponse> getStaffsByManagerBranches(
			List<Long> branchIds, String search, int page, int size) {
		String searchParam = (search == null || search.trim().isEmpty()) ? null : search.trim();
		Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
		if (branchIds == null || branchIds.isEmpty()) {
			return PageResponse.of(Page.empty(pageable), u -> null);
		}
		Page<User> userPage = userRepository.searchStaffsByManagerBranches(branchIds, searchParam, pageable);
		return toEmployeeResponsePage(userPage);
	}

	@Override
	@Transactional(readOnly = true)
	public EmployeeAccountResponse getStaffDetailForManager(List<Long> branchIds, Long id) {
		User user = userRepository.findByIdAndStatus(id, UserStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
		if (!"STAFF".equals(user.getRole().getCode())) {
			throw new BusinessException(ErrorCode.INVALID_EMPLOYEE_ROLE);
		}
		if (user.getBranch() == null || branchIds == null || !branchIds.contains(user.getBranch().getId())) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		return toEmployeeResponse(user, user.getProfile());
	}

	@Override
	@Transactional
	public EmployeeAccountResponse deactivateStaffForManager(List<Long> branchIds, Long id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
		if (!"STAFF".equals(user.getRole().getCode())) {
			throw new BusinessException(ErrorCode.INVALID_EMPLOYEE_ROLE);
		}
		if (user.getBranch() == null || branchIds == null || !branchIds.contains(user.getBranch().getId())) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		user.setStatus(UserStatus.LOCKED);
		user = userRepository.save(user);
		return toEmployeeResponse(user, user.getProfile());
	}

	@Override
	@Transactional
	public EmployeeAccountResponse reactivateStaffForManager(List<Long> branchIds, Long id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
		if (!"STAFF".equals(user.getRole().getCode())) {
			throw new BusinessException(ErrorCode.INVALID_EMPLOYEE_ROLE);
		}
		if (user.getBranch() == null || branchIds == null || !branchIds.contains(user.getBranch().getId())) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		user.setStatus(UserStatus.ACTIVE);
		user = userRepository.save(user);
		return toEmployeeResponse(user, user.getProfile());
	}

	@Override
	@Transactional(readOnly = true)
	public EmployeeAccountResponse getEmployeeDetail(Long ownerId, Long id) {
		User user = userRepository.findByIdAndStatus(id, UserStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
		validateEmployeeRole(user);
		if (ownerId != null && (user.getOwner() == null || !ownerId.equals(user.getOwner().getId()))) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		return toEmployeeResponse(user, user.getProfile());
	}

	@Override
	@Transactional
	public EmployeeAccountResponse deactivateEmployee(Long ownerId, Long id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
		validateEmployeeRole(user);
		if (ownerId != null && (user.getOwner() == null || !ownerId.equals(user.getOwner().getId()))) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		user.setStatus(UserStatus.LOCKED);
		user = userRepository.save(user);
		return toEmployeeResponse(user, user.getProfile());
	}

	@Override
	@Transactional
	public EmployeeAccountResponse reactivateEmployee(Long ownerId, Long id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
		validateEmployeeRole(user);
		if (ownerId != null && (user.getOwner() == null || !ownerId.equals(user.getOwner().getId()))) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		user.setStatus(UserStatus.ACTIVE);
		user = userRepository.save(user);
		return toEmployeeResponse(user, user.getProfile());
	}

	private void validateRoleAssignment(RoleCode callerRole, RoleCode targetRole) {
		boolean allowed = switch (callerRole) {
			case ADMIN -> targetRole == RoleCode.OWNER || targetRole == RoleCode.ADMIN;
			case OWNER -> targetRole == RoleCode.MANAGER || targetRole == RoleCode.STAFF;
			default -> false;
		};
		if (!allowed) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_ROLE_ASSIGNMENT);
		}
	}

	private void validateEmployeeRole(User user) {
		String roleCode = user.getRole().getCode();
		if (!"STAFF".equals(roleCode) && !"MANAGER".equals(roleCode)) {
			throw new BusinessException(ErrorCode.INVALID_EMPLOYEE_ROLE);
		}
	}

	private UserResponse toUserResponse(User user, boolean profileCompleted) {
		return UserResponse.builder()
				.id(user.getId())
				.email(user.getEmail())
				.phone(user.getPhone())
				.role(user.getRole().getCode())
				.status(user.getStatus().name())
				.profileCompleted(profileCompleted)
				.build();
	}

	/**
	 * Batch tương đương {@link #toEmployeeResponse(User, UserProfile)} cho cả 1 trang — batch 1 query
	 * lấy chi nhánh quản lý cho mọi MANAGER trong trang, thay vì lặp lại theo từng dòng.
	 */
	private PageResponse<EmployeeAccountResponse> toEmployeeResponsePage(Page<User> userPage) {
		List<User> users = userPage.getContent();
		if (users.isEmpty()) {
			return PageResponse.of(userPage, u -> null);
		}
		List<Long> managerIds = users.stream()
				.filter(u -> "MANAGER".equals(u.getRole().getCode()))
				.map(User::getId)
				.toList();
		Map<Long, List<Long>> branchIdsByManagerId = managerIds.isEmpty()
				? Map.of()
				: branchManagerRepository.findByManagerIdIn(managerIds).stream()
						.collect(Collectors.groupingBy(bm -> bm.getManager().getId(),
								Collectors.mapping(bm -> bm.getBranch().getId(), Collectors.toList())));
		return PageResponse.of(userPage, user -> toEmployeeResponse(
				user, user.getProfile(),
				"MANAGER".equals(user.getRole().getCode())
						? branchIdsByManagerId.getOrDefault(user.getId(), List.of())
						: null));
	}

	private EmployeeAccountResponse toEmployeeResponse(User user, UserProfile profile) {
		boolean isManager = "MANAGER".equals(user.getRole().getCode());
		List<Long> branchIds = isManager
				? branchManagerRepository.findByManagerId(user.getId()).stream()
						.map(bm -> bm.getBranch().getId())
						.toList()
				: null;
		return toEmployeeResponse(user, profile, branchIds);
	}

	private EmployeeAccountResponse toEmployeeResponse(User user, UserProfile profile, List<Long> branchIds) {
		boolean isManager = "MANAGER".equals(user.getRole().getCode());
		boolean isStaff = "STAFF".equals(user.getRole().getCode());

		return EmployeeAccountResponse.builder()
				.id(user.getId())
				.email(user.getEmail())
				.phone(user.getPhone())
				.role(user.getRole().getCode())
				.status(user.getStatus().name())
				.fullName(profile != null ? profile.getFullName() : null)
				.displayName(profile != null ? profile.getDisplayName() : null)
				.avatarUrl(profile != null
						? AvatarUrlResolver.resolveForResponse(
								profile.getAvatarUrl(), minioStorageService, minioProperties.getBucket())
						: null)
				.dateOfBirth(profile != null ? profile.getDateOfBirth() : null)
				.gender(profile != null ? profile.getGender() : null)
				.bio(profile != null ? profile.getBio() : null)
				.manageAllBranches(isManager ? user.getManageAllBranches() : null)
				.branchIds(branchIds)
				.branchId(isStaff && user.getBranch() != null ? user.getBranch().getId() : null)
				.branchName(isStaff && user.getBranch() != null ? user.getBranch().getName() : null)
				.build();
	}
}