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
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.util.AvatarUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
		return userRepository.save(user);
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
			employee.setManageAllBranches(manageAllBranches);
			branchManagerRepository.deleteByManagerId(employeeId);
			if (!manageAllBranches && request.getBranchIds() != null) {
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
		return PageResponse.of(userPage, user -> toEmployeeResponse(user, user.getProfile()));
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
		return PageResponse.of(userPage, user -> toEmployeeResponse(user, user.getProfile()));
	}


	@Override
	@Transactional(readOnly = true)
	public PageResponse<EmployeeAccountResponse> getStaffsByManager(Long ownerId, String search, int page, int size) {
		String searchParam = (search == null || search.trim().isEmpty()) ? null : search.trim();

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

		Page<User> userPage = userRepository.searchStaffsByManager(ownerId, searchParam, pageable);
		return PageResponse.of(userPage, user -> toEmployeeResponse(user, user.getProfile()));
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

	private EmployeeAccountResponse toEmployeeResponse(User user, UserProfile profile) {
		boolean isManager = "MANAGER".equals(user.getRole().getCode());
		boolean isStaff = "STAFF".equals(user.getRole().getCode());
		List<Long> branchIds = isManager
				? branchManagerRepository.findByManagerId(user.getId()).stream()
						.map(bm -> bm.getBranch().getId())
						.toList()
				: null;

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