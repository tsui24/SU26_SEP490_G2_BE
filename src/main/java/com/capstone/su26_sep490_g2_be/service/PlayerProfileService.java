package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.CreatePlayerProfileRequest;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerProfileResponse;

public interface PlayerProfileService {

	PlayerProfileResponse createProfile(String email, CreatePlayerProfileRequest request);

	PlayerProfileResponse getMyProfile(String email);
}
