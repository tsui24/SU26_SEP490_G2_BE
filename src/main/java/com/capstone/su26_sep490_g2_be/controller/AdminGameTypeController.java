package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.UpdateGameTypeRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.GameTypeDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.GameTypeListResponse;
import com.capstone.su26_sep490_g2_be.service.AdminTournamentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Game Types", description = "Admin quản lý loại bi — requires ADMIN role")
@RestController
@RequestMapping("/api/v1/admin/game-types")
@RequiredArgsConstructor
public class AdminGameTypeController {

	private final AdminTournamentConfigService adminTournamentConfigService;

	@Operation(summary = "Danh sách loại bi")
	@GetMapping
	public ResponseEntity<ApiResponse<GameTypeListResponse>> listGameTypes() {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.listGameTypes()));
	}

	@Operation(summary = "Cập nhật loại bi")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy loại bi")
	})
	@PutMapping("/{code}")
	public ResponseEntity<ApiResponse<GameTypeDetailResponse>> updateGameType(
			@PathVariable String code,
			@Valid @RequestBody UpdateGameTypeRequest request) {
		return ResponseEntity.ok(ApiResponse.success(adminTournamentConfigService.updateGameType(code, request)));
	}
}
