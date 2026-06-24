package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.ManualAddParticipantRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ImportParticipantResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantResponse;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.ParticipantExcelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Tag(name = "Participants", description = "Quản lý người tham gia giải đấu")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class ParticipantController {

    private final TournamentRepository tournamentRepository;
    private final ParticipantRepository participantRepository;
    private final RegistrationRepository registrationRepository;
    private final ParticipantExcelService participantExcelService;

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

    /* ── Excel template / import ── */
    @Operation(summary = "Tải mẫu import người tham gia (Owner)", description = "Mặc định trả file .xlsx. Thêm ?format=csv nếu cần CSV.")
    @GetMapping("/api/v1/owner/tournaments/{id}/participants/import-template")
    public ResponseEntity<byte[]> downloadTemplateOwner(
            @PathVariable Long id,
            @RequestParam(value = "format", defaultValue = "xlsx") String format) {
        return buildTemplateResponse(id, format);
    }

    @Operation(summary = "Tải mẫu import người tham gia (Manager)", description = "Mặc định trả file .xlsx. Thêm ?format=csv nếu cần CSV.")
    @GetMapping("/api/v1/manager/tournaments/{id}/participants/import-template")
    public ResponseEntity<byte[]> downloadTemplateManager(
            @PathVariable Long id,
            @RequestParam(value = "format", defaultValue = "xlsx") String format) {
        return buildTemplateResponse(id, format);
    }

    @Operation(summary = "Import người tham gia từ Excel (Owner)")
    @PostMapping("/api/v1/owner/tournaments/{id}/participants/import-excel")
    public ResponseEntity<ApiResponse<ImportParticipantResultResponse>> importOwner(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(participantExcelService.importFromExcel(id, file)));
    }

    @Operation(summary = "Import người tham gia từ Excel (Manager)")
    @PostMapping("/api/v1/manager/tournaments/{id}/participants/import-excel")
    public ResponseEntity<ApiResponse<ImportParticipantResultResponse>> importManager(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(participantExcelService.importFromExcel(id, file)));
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
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // UC-28: sync APPROVED registrations → Participant records (idempotent)
        syncApprovedRegistrationsToParticipants(tournament);

        return participantRepository.findByTournamentId(tournamentId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void syncApprovedRegistrationsToParticipants(Tournament tournament) {
        List<Registration> approvedRegs = registrationRepository
                .findByTournamentIdAndStatus(tournament.getId(), "APPROVED",
                        org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        for (Registration reg : approvedRegs) {
            if (!participantRepository.existsByRegistrationId(reg.getId())) {
                Participant participant = Participant.builder()
                        .tournament(tournament)
                        .registration(reg)
                        .participantType(tournament.getParticipantType())
                        .displayName(reg.getPlayerFullName())
                        .status("ACTIVE")
                        .build();
                participantRepository.save(participant);
                log.info("Auto-synced participant from registration #{}", reg.getId());
            }
        }
    }

    private ResponseEntity<byte[]> buildTemplateResponse(Long tournamentId, String format) {
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean asCsv = "csv".equalsIgnoreCase(format);
        try {
            byte[] bytes = asCsv
                    ? participantExcelService.buildImportTemplateCsv()
                    : participantExcelService.buildImportTemplate();
            String filename = asCsv
                    ? participantExcelService.getTemplateCsvFilename()
                    : participantExcelService.getTemplateFilename();
            MediaType mediaType = asCsv
                    ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                    : MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(mediaType)
                    .body(bytes);
        } catch (IOException e) {
            log.error("Failed to build participant import template: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        }
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
        return toResponse(findParticipantWithDetails(participant.getId()));
    }

    private ParticipantResponse withdraw(Long participantId) {
        Participant participant = findParticipantWithDetails(participantId);
        participant.setStatus("WITHDRAWN");
        participantRepository.save(participant);
        return toResponse(participant);
    }

    private Participant findParticipantWithDetails(Long participantId) {
        return participantRepository.findByIdWithDetails(participantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
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
