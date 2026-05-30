package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.UserProfileRequest;
import com.capstone.su26_sep490_g2_be.dto.response.UserProfileResponse;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.UserProfileRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;


	@Override
	@Transactional(readOnly = true)
	public UserProfileResponse getUserProfile(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		UserProfile profile = user.getProfile();
		if (profile == null) {
			throw new BusinessException(ErrorCode.PROFILE_NOT_FOUND);
		}

		return toResponse(user, profile);
	}

	@Override
	@Transactional
	public UserProfileResponse updateUserProfile(Long userId, UserProfileRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		UserProfile profile = user.getProfile();
		if (profile == null) {
			throw new BusinessException(ErrorCode.PROFILE_NOT_FOUND);
		}

		boolean isPlayer = "PLAYER".equals(user.getRole().getCode());
		// Kiểm tra nếu vai trò không phải PLAYER mà cố tình set billiardRank thì báo lỗi
		if (!isPlayer && request.getBilliardRank() != null && !request.getBilliardRank().trim().isEmpty()) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Billiard rank is only available for player profiles");
		}

		profile.setFullName(request.getFullName());
		profile.setDisplayName(request.getDisplayName());
		profile.setAvatarUrl(request.getAvatarUrl());
		profile.setDateOfBirth(request.getDateOfBirth());
		profile.setGender(request.getGender());
		profile.setBio(request.getBio());

		// Chỉ cập nhật billiardRank nếu là PLAYER
		if (isPlayer) {
			profile.setBilliardRank(request.getBilliardRank());
		}
		user.setEmail(user.getEmail());
		user.setPhone(request.getPhone());
		User savedUser = userRepository.save(user);

		profile = userProfileRepository.save(profile);
		return toResponse(savedUser, profile);
	}

	private UserProfileResponse toResponse(User user, UserProfile profile) {
		return UserProfileResponse.builder()
				.email(user.getEmail())
				.phone(user.getPhone())
				.fullName(profile.getFullName())
				.displayName(profile.getDisplayName())
				.avatarUrl(profile.getAvatarUrl())
				.dateOfBirth(profile.getDateOfBirth())
				.gender(profile.getGender())
				.billiardRank(profile.getBilliardRank())
				.bio(profile.getBio())
				.build();
	}
}
