package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.LoginResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegisterResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Login, Register, Password management")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@Operation(summary = "Login", description = "Đăng nhập bằng email/password, trả về JWT token")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Sai email hoặc password")
	})
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(summary = "Thông tin tài khoản hiện tại", description = "Lấy thông tin user đang đăng nhập từ JWT")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy thông tin thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token không hợp lệ")
	})
	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserResponse>> me(Authentication authentication) {
		Long userId = extractUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(authService.getMe(userId)));
	}

	@Operation(summary = "Register Player account",
			description = "Bước 1: chỉ tạo tài khoản (email, phone, password). Trả token để chuyển sang màn tạo profile.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đăng ký thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email đã tồn tại")
	})
	@PostMapping("/register")
	public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
		RegisterResponse response = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Đã tạo tài khoản. Vui lòng hoàn thiện hồ sơ.", response));
	}

	@Operation(summary = "Forgot Password", description = "Gửi OTP đến email để reset password")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP đã gửi"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Email không tồn tại")
	})
	@PostMapping("/forgot-password")
	public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		authService.forgotPassword(request);
		return ResponseEntity.ok(ApiResponse.success("Đã gửi OTP tới email của bạn", null));
	}

	@Operation(summary = "Verify OTP", description = "Xác thực OTP đã gửi qua email")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP hợp lệ"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "OTP sai hoặc hết hạn")
	})
	@PostMapping("/verify-otp")
	public ResponseEntity<ApiResponse<Void>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
		authService.verifyOtp(request);
		return ResponseEntity.ok(ApiResponse.success("Xác thực OTP thành công", null));
	}

	@Operation(summary = "Reset Password", description = "Đặt lại mật khẩu sau khi verify OTP")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đặt lại mật khẩu thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "OTP sai hoặc hết hạn")
	})
	@PostMapping("/reset-password")
	public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request);
		return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công", null));
	}

	@Operation(summary = "Change Password", description = "Đổi mật khẩu (cần đăng nhập)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đổi mật khẩu thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Mật khẩu cũ không đúng"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
	})
	@PostMapping("/change-password")
	public ResponseEntity<ApiResponse<Void>> changePassword(
			Authentication authentication,
			@Valid @RequestBody ChangePasswordRequest request) {
		String email = authentication.getName();
		authService.changePassword(email, request);
		return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công", null));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
