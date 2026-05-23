package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.CreatePlayerProfileRequest;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerProfileResponse;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.UserProfileRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.PlayerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerProfileServiceImpl implements PlayerProfileService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;

	@Override
	@Transactional
	public PlayerProfileResponse createProfile(String email, CreatePlayerProfileRequest request) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		if (!RoleCode.PLAYER.getCode().equals(user.getRole().getCode())) {
			throw new BusinessException(ErrorCode.PROFILE_ONLY_FOR_PLAYER);
		}

		if (userProfileRepository.existsByUserId(user.getId())) {
			throw new BusinessException(ErrorCode.PROFILE_ALREADY_EXISTS);
		}

		String rank = request.getBilliardRank() != null && !request.getBilliardRank().isBlank()
				? request.getBilliardRank().trim()
				: "UNRANKED";

		UserProfile profile = UserProfile.builder()
				.user(user)
				.fullName(request.getFullName())
				.displayName(request.getDisplayName())
				.avatarUrl(request.getAvatarUrl())
				.dateOfBirth(request.getDateOfBirth())
				.gender(request.getGender())
				.billiardRank(rank)
				.bio(request.getBio())
				.build();

		profile = userProfileRepository.save(profile);
		return toResponse(profile);
	}

	@Override
	@Transactional(readOnly = true)
	public PlayerProfileResponse getMyProfile(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		if (user.getProfile() == null) {
			throw new BusinessException(ErrorCode.PROFILE_NOT_FOUND);
		}

		return toResponse(user.getProfile());
	}

	private PlayerProfileResponse toResponse(UserProfile profile) {
		return PlayerProfileResponse.builder()
				.userId(profile.getUserId())
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
