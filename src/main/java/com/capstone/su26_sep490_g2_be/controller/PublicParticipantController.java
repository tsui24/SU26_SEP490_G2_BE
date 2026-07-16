package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerPublicProfileResponse;
import com.capstone.su26_sep490_g2_be.service.TournamentResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public — Participants", description = "Hồ sơ cơ thủ công khai — không cần đăng nhập")
@RestController
@RequestMapping("/api/v1/participants")
@RequiredArgsConstructor
public class PublicParticipantController {

    private final TournamentResultService tournamentResultService;

    @Operation(summary = "Hồ sơ cơ thủ kèm lịch sử thành tích (theo participantId)")
    @GetMapping("/{participantId}/profile")
    public ResponseEntity<ApiResponse<PlayerPublicProfileResponse>> getProfile(
            @PathVariable Long participantId) {
        return ResponseEntity.ok(ApiResponse.success(
                tournamentResultService.getParticipantProfile(participantId)));
    }

    @Operation(summary = "Hồ sơ cơ thủ kèm lịch sử thành tích (theo userId — gom tất cả giải)")
    @GetMapping("/user/{userId}/profile")
    public ResponseEntity<ApiResponse<PlayerPublicProfileResponse>> getProfileByUserId(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                tournamentResultService.getPlayerProfileByUserId(userId)));
    }
}
