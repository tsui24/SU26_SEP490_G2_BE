package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.ManualAddParticipantRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ImportParticipantResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantResponse;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Tag(name = "Participants", description = "Quản lý người tham gia giải đấu")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class ParticipantController {

    private final TournamentRepository tournamentRepository;
    private final ParticipantRepository participantRepository;

    /* ── Shared: list participants ── */
    @Operation(summary = "Danh sách người tham gia (Owner)")
    @GetMapping("/api/v1/owner/tournaments/{id}/participants")
    public ResponseEntity<ApiResponse<List<ParticipantResponse>>> listOwner(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(getParticipants(id)));
    }

    @Operation(summary = "Danh sách người tham gia (Manager)")
    @GetMapping("/api/v1/manager/tournaments/{id}/participants")
    public ResponseEntity<ApiResponse<List<ParticipantResponse>>> listManager(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(getParticipants(id)));
    }

    /* ── Public: list participants ── */
    @Operation(summary = "Danh sách người tham gia công khai")
    @GetMapping("/api/v1/tournaments/{id}/participants")
    public ResponseEntity<ApiResponse<List<ParticipantResponse>>> listPublic(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(getParticipants(id)));
    }

    /* ── Manual add ── */
    @Operation(summary = "Thêm người tham gia thủ công (Owner)")
    @PostMapping("/api/v1/owner/tournaments/{id}/participants/manual")
    public ResponseEntity<ApiResponse<ParticipantResponse>> addManualOwner(
            @PathVariable Long id,
            @Valid @RequestBody ManualAddParticipantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã thêm người tham gia", addManual(id, request)));
    }

    @Operation(summary = "Thêm người tham gia thủ công (Manager)")
    @PostMapping("/api/v1/manager/tournaments/{id}/participants/manual")
    public ResponseEntity<ApiResponse<ParticipantResponse>> addManualManager(
            @PathVariable Long id,
            @Valid @RequestBody ManualAddParticipantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã thêm người tham gia", addManual(id, request)));
    }

    /* ── Excel import ── */
    @Operation(summary = "Import người tham gia từ Excel (Owner)")
    @PostMapping("/api/v1/owner/tournaments/{id}/participants/import-excel")
    public ResponseEntity<ApiResponse<ImportParticipantResultResponse>> importOwner(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(importExcel(id, file)));
    }

    @Operation(summary = "Import người tham gia từ Excel (Manager)")
    @PostMapping("/api/v1/manager/tournaments/{id}/participants/import-excel")
    public ResponseEntity<ApiResponse<ImportParticipantResultResponse>> importManager(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(importExcel(id, file)));
    }

    /* ── Withdraw ── */
    @Operation(summary = "Rút lui khỏi giải (Owner)")
    @PatchMapping("/api/v1/owner/participants/{participantId}/withdraw")
    public ResponseEntity<ApiResponse<ParticipantResponse>> withdrawOwner(@PathVariable Long participantId) {
        return ResponseEntity.ok(ApiResponse.success(withdraw(participantId)));
    }

    @Operation(summary = "Rút lui khỏi giải (Manager)")
    @PatchMapping("/api/v1/manager/participants/{participantId}/withdraw")
    public ResponseEntity<ApiResponse<ParticipantResponse>> withdrawManager(@PathVariable Long participantId) {
        return ResponseEntity.ok(ApiResponse.success(withdraw(participantId)));
    }

    /* ─────────────────── Private helpers ─────────────────── */

    private List<ParticipantResponse> getParticipants(Long tournamentId) {
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return participantRepository.findByTournamentId(tournamentId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ParticipantResponse addManual(Long tournamentId, ManualAddParticipantRequest request) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Participant participant = Participant.builder()
                .tournament(tournament)
                .participantType(tournament.getParticipantType())
                .displayName(request.getDisplayName().trim())
                .status("ACTIVE")
                .build();
        participant = participantRepository.save(participant);
        return toResponse(participant);
    }

    private ImportParticipantResultResponse importExcel(Long tournamentId, MultipartFile file) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<String> errors = new ArrayList<>();
        int imported = 0, skipped = 0, totalRows = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // skip header
                totalRows++;

                String displayName = getCellString(row, 0);
                if (displayName == null || displayName.isBlank()) {
                    errors.add("Hàng " + (row.getRowNum() + 1) + ": Tên hiển thị không được để trống");
                    skipped++;
                    continue;
                }
                if (displayName.length() > 255) {
                    errors.add("Hàng " + (row.getRowNum() + 1) + ": Tên quá dài");
                    skipped++;
                    continue;
                }

                Participant participant = Participant.builder()
                        .tournament(tournament)
                        .participantType(tournament.getParticipantType())
                        .displayName(displayName.trim())
                        .status("ACTIVE")
                        .build();
                participantRepository.save(participant);
                imported++;
            }
        } catch (Exception e) {
            log.error("Excel import error: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        }

        return ImportParticipantResultResponse.builder()
                .totalRows(totalRows)
                .imported(imported)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    private ParticipantResponse withdraw(Long participantId) {
        Participant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        participant.setStatus("WITHDRAWN");
        participant = participantRepository.save(participant);
        return toResponse(participant);
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private ParticipantResponse toResponse(Participant p) {
        String phone = null;
        if (p.getRegistration() != null) {
            phone = p.getRegistration().getPlayerPhone();
        }
        return ParticipantResponse.builder()
                .id(p.getId())
                .tournamentId(p.getTournament().getId())
                .tournamentName(p.getTournament().getName())
                .registrationId(p.getRegistration() != null ? p.getRegistration().getId() : null)
                .participantType(p.getParticipantType())
                .displayName(p.getDisplayName())
                .phone(phone)
                .seedNo(p.getSeedNo())
                .status(p.getStatus())
                .source(p.getRegistration() != null ? "ONLINE_REGISTRATION" : "MANUAL")
                .build();
    }
}
