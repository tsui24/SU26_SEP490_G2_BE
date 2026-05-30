package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.UserProfileRequest;
import com.capstone.su26_sep490_g2_be.dto.response.UserProfileResponse;

public interface UserProfileService {

	UserProfileResponse getUserProfile(Long userId);
	UserProfileResponse updateUserProfile(Long userId, UserProfileRequest request);
}
