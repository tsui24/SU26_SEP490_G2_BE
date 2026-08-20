package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.UserProfileRequest;
import com.capstone.su26_sep490_g2_be.dto.response.UserProfileResponse;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.BilliardRank;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.repository.UserProfileRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.service.UserProfileService;
import com.capstone.su26_sep490_g2_be.util.AvatarUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final MinioStorageService minioStorageService;
	private final MinioProperties minioProperties;


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
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Xếp hạng bi-a chỉ áp dụng cho hồ sơ Cơ thủ");
		}
		// Hạng phải nằm trong hệ phân hạng bi-a Việt Nam — trước đây là chuỗi tự do, gõ gì cũng lưu
		if (isPlayer && request.getBilliardRank() != null && !request.getBilliardRank().trim().isEmpty()
				&& !BilliardRank.isValid(request.getBilliardRank().trim())) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST,
					"Hạng cơ thủ không hợp lệ — chỉ nhận CN, A đến L, hoặc UNKNOWN");
		}

		// Cập nhật kiểu PATCH: trường nào client không gửi lên (null) thì giữ nguyên giá trị cũ.
		// Trước đây mọi trường bị ghi thẳng xuống profile/user kể cả khi null, nên 1 request chỉ
		// sửa 1-2 trường (cách gọi API phổ biến nhất — chỉ gửi trường vừa đổi) sẽ vô tình xóa sạch
		// mọi trường còn lại về null/mặc định. fullName là trường bắt buộc (@NotBlank) nên luôn ghi.
		profile.setFullName(request.getFullName());
		if (request.getDisplayName() != null) {
			profile.setDisplayName(request.getDisplayName());
		}
		if (request.getAvatarUrl() != null) {
			profile.setAvatarUrl(AvatarUrlResolver.normalizeForStorage(
					request.getAvatarUrl(), minioProperties.getBucket()));
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

		// Chỉ cập nhật billiardRank nếu là PLAYER và có gửi lên (chuẩn hoá rỗng → UNKNOWN)
		if (isPlayer && request.getBilliardRank() != null) {
			profile.setBilliardRank(BilliardRank.fromNullable(request.getBilliardRank()).name());
		}
		if (request.getPhone() != null) {
			if (!request.getPhone().equals(user.getPhone()) && userRepository.existsByPhone(request.getPhone())) {
				throw new BusinessException(ErrorCode.AUTH_PHONE_ALREADY_EXISTS);
			}
			user.setPhone(request.getPhone());
		}
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
				.avatarUrl(AvatarUrlResolver.resolveForResponse(
						profile.getAvatarUrl(), minioStorageService, minioProperties.getBucket()))
				.dateOfBirth(profile.getDateOfBirth())
				.gender(profile.getGender())
				.billiardRank(profile.getBilliardRank())
				.bio(profile.getBio())
				.build();
	}
}
