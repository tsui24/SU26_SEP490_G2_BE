package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentListItemResponse;
import com.capstone.su26_sep490_g2_be.service.OwnerTournamentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Public — Tournaments", description = "Danh sách và chi tiết giải đấu công khai — không cần đăng nhập")
@RestController
@RequestMapping("/api/v1/tournaments")
@RequiredArgsConstructor
public class PublicTournamentController {

    private final OwnerTournamentService ownerTournamentService;

    @Operation(summary = "Danh sách giải đấu công khai")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TournamentListItemResponse>>> listTournaments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerTournamentService.listPlayerTournaments(status, search, page, size)));
    }

    @Operation(summary = "Chi tiết giải đấu công khai")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TournamentDetailResponse>> getTournament(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerTournamentService.getPlayerTournamentDetail(id)));
    }
}
