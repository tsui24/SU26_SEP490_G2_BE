package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.RoleRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;

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
	public EmployeeAccountResponse createManagerAccount(CreateManagerAccountRequest request) {
		return createEmployeeWithProfile(
				request.getEmail(),
				request.getPhone(),
				request.getPassword(),
				request.getFullName(),
				request.getDisplayName(),
				request.getAvatarUrl(),
				request.getDateOfBirth(),
				request.getGender(),
				request.getBio(),
				RoleCode.MANAGER,
				RoleCode.OWNER);
	}

	@Override
	@Transactional
	public EmployeeAccountResponse createStaffAccount(CreateStaffAccountRequest request) {
		return createEmployeeWithProfile(
				request.getEmail(),
				request.getPhone(),
				request.getPassword(),
				request.getFullName(),
				request.getDisplayName(),
				request.getAvatarUrl(),
				request.getDateOfBirth(),
				request.getGender(),
				request.getBio(),
				RoleCode.STAFF,
				RoleCode.OWNER);
	}

	private EmployeeAccountResponse createEmployeeWithProfile(
			String email,
			String phone,
			String password,
			String fullName,
			String displayName,
			String avatarUrl,
			java.time.LocalDate dateOfBirth,
			String gender,
			String bio,
			RoleCode targetRole,
			RoleCode callerRole) {

		validateRoleAssignment(callerRole, targetRole);

		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}

		Role role = roleRepository.findByCode(targetRole.getCode())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ROLE_NOT_FOUND));

		User user = User.builder()
				.email(email)
				.phone(phone)
				.passwordHash(passwordEncoder.encode(password))
				.role(role)
				.status(UserStatus.ACTIVE)
				.build();

		UserProfile profile = UserProfile.builder()
				.user(user)
				.fullName(fullName)
				.displayName(displayName)
				.avatarUrl(avatarUrl)
				.dateOfBirth(dateOfBirth)
				.gender(gender)
				.bio(bio)
				.build();

		user.setProfile(profile);
		user = userRepository.save(user);

		return toEmployeeResponse(user, profile);
	}

	private void validateRoleAssignment(RoleCode callerRole, RoleCode targetRole) {
		boolean allowed = switch (callerRole) {
			case ADMIN -> targetRole == RoleCode.OWNER;
			case OWNER -> targetRole == RoleCode.MANAGER || targetRole == RoleCode.STAFF;
			default -> false;
		};

		if (!allowed) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_ROLE_ASSIGNMENT);
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
		return EmployeeAccountResponse.builder()
				.id(user.getId())
				.email(user.getEmail())
				.phone(user.getPhone())
				.role(user.getRole().getCode())
				.status(user.getStatus().name())
				.fullName(profile.getFullName())
				.displayName(profile.getDisplayName())
				.avatarUrl(profile.getAvatarUrl())
				.dateOfBirth(profile.getDateOfBirth())
				.gender(profile.getGender())
				.bio(profile.getBio())
				.build();
	}
}
