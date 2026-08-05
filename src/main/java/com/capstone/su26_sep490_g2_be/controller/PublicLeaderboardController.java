package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.LeaderboardEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.enums.LeaderboardPeriod;
import com.capstone.su26_sep490_g2_be.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public — Leaderboard", description = "Bảng xếp hạng điểm tích lũy cơ thủ — không cần đăng nhập")
@RestController
@RequestMapping("/api/v1/leaderboard")
@RequiredArgsConstructor
public class PublicLeaderboardController {

	private final LeaderboardService leaderboardService;

	@Operation(summary = "Bảng xếp hạng điểm tích lũy cơ thủ theo tháng / quý / năm")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<LeaderboardEntryResponse>>> getLeaderboard(
			@RequestParam(required = false, defaultValue = "ALL") String period,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer quarter,
			@RequestParam(required = false) Integer month,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				leaderboardService.getLeaderboard(
						LeaderboardPeriod.from(period), year, quarter, month, page, size)));
	}
}
