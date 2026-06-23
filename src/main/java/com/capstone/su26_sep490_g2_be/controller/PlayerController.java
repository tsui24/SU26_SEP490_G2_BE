package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CreatePlayerProfileRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerProfileResponse;
import com.capstone.su26_sep490_g2_be.service.PlayerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Player", description = "Player APIs — requires PLAYER role")
@RestController
@RequestMapping("/api/v1/player")
@RequiredArgsConstructor
public class PlayerController {

	private final PlayerProfileService playerProfileService;

	@Operation(summary = "Create Player profile",
			description = "Bước 2 sau đăng ký: tạo profile (tên, rank bi-a, avatar, ...)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo profile thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Profile đã tồn tại")
	})
	@PostMapping("/profile")
	public ResponseEntity<ApiResponse<PlayerProfileResponse>> createProfile(
			Authentication authentication,
			@Valid @RequestBody CreatePlayerProfileRequest request) {
		PlayerProfileResponse response = playerProfileService.createProfile(authentication.getName(), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Đã tạo hồ sơ", response));
	}

	@Operation(summary = "Get my profile", description = "Xem profile của Player đang đăng nhập")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Chưa tạo profile")
	})
	@GetMapping("/profile")
	public ResponseEntity<ApiResponse<PlayerProfileResponse>> getMyProfile(Authentication authentication) {
		PlayerProfileResponse response = playerProfileService.getMyProfile(authentication.getName());
		return ResponseEntity.ok(ApiResponse.success(response));
	}
}
