package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.UserProfileRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserProfileResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.UserProfileService;
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

@Tag(name = "Profile", description = "API quản lý hồ sơ người dùng")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

	private final UserProfileService userProfileService;

	@Operation(
			summary = "Lấy hồ sơ người dùng",
			description = "Lấy thông tin hồ sơ cá nhân của tài khoản hiện tại đang đăng nhập." +
					" Lưu ý chỉ hiển thị field billiardRank nếu role là PLAYER"
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy profile thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy profile của tài khoản này")
	})
	@GetMapping
	public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(Authentication authentication) {
		Long userId = extractUserId(authentication);
		UserProfileResponse response = userProfileService.getUserProfile(userId);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(
			summary = "Cập nhật hồ sơ người dùng",
			description = "Cập nhật thông tin hồ sơ cá nhân của tài khoản hiện tại, không update email" +
					" LƯU Ý: Trường 'billiardRank' chỉ được phép thay đổi nếu người dùng có vai trò là PLAYER. " +
					"Đối với các vai trò khác, nếu gửi kèm 'billiardRank' khác rỗng hệ thống sẽ báo lỗi."
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật profile thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Yêu cầu không hợp lệ hoặc cố gắng cập nhật billiardRank cho vai trò không phải PLAYER"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy profile")
	})
	@PutMapping
	public ResponseEntity<ApiResponse<UserProfileResponse>> editProfile(
			Authentication authentication,
			@Valid @RequestBody UserProfileRequest request) {
		Long userId = extractUserId(authentication);
		UserProfileResponse response = userProfileService.updateUserProfile(userId, request);
		return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công", response));
	}

	private Long extractUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return (Long) authentication.getCredentials();
	}
}
