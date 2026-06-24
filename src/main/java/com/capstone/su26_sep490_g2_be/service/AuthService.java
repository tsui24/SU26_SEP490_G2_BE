package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.LoginResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegisterResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;

public interface AuthService {

	LoginResponse login(LoginRequest request);

	UserResponse getMe(Long userId);

	RegisterResponse register(RegisterRequest request);

	void forgotPassword(ForgotPasswordRequest request);

	void verifyOtp(VerifyOtpRequest request);

	void resetPassword(ResetPasswordRequest request);

	void changePassword(String email, ChangePasswordRequest request);
}
