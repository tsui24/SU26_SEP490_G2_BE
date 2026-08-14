package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.ManualAddParticipantRequest;
import com.capstone.su26_sep490_g2_be.dto.request.ParticipantImportConfirmRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateSeedNoRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ImportParticipantResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantImportPreviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantResponse;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.BilliardRank;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantMemberRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.impl.MailContextBuilder;
import com.capstone.su26_sep490_g2_be.util.ParticipantMemberFactory;
import com.capstone.su26_sep490_g2_be.util.SecurityUtil;
import org.springframework.security.core.Authentication;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.ParticipantExcelService;
import com.capstone.su26_sep490_g2_be.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
    private final ParticipantMemberRepository participantMemberRepository;
    private final RegistrationRepository registrationRepository;
    private final ParticipantExcelService participantExcelService;
    private final RegistrationService registrationService;
    private final SecurityUtil securityUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final MailContextBuilder mailContextBuilder;

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
    @Transactional
    public ResponseEntity<ApiResponse<ParticipantResponse>> addManualOwner(
            @PathVariable Long id,
            @Valid @RequestBody ManualAddParticipantRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã thêm người tham gia", addManual(id, request, authentication)));
    }

    @Operation(summary = "Thêm người tham gia thủ công (Manager)")
    @PostMapping("/api/v1/manager/tournaments/{id}/participants/manual")
    @Transactional
    public ResponseEntity<ApiResponse<ParticipantResponse>> addManualManager(
            @PathVariable Long id,
            @Valid @RequestBody ManualAddParticipantRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã thêm người tham gia", addManual(id, request, authentication)));
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

    @Operation(summary = "Xem trước import người tham gia từ Excel (Owner)", description = "Chỉ validate, không lưu DB. Dùng kết quả để gọi confirm.")
    @PostMapping("/api/v1/owner/tournaments/{id}/participants/import-excel/preview")
    public ResponseEntity<ApiResponse<ParticipantImportPreviewResponse>> previewImportOwner(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(participantExcelService.previewFromExcel(id, file)));
    }

    @Operation(summary = "Xem trước import người tham gia từ Excel (Manager)", description = "Chỉ validate, không lưu DB. Dùng kết quả để gọi confirm.")
    @PostMapping("/api/v1/manager/tournaments/{id}/participants/import-excel/preview")
    public ResponseEntity<ApiResponse<ParticipantImportPreviewResponse>> previewImportManager(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(participantExcelService.previewFromExcel(id, file)));
    }

    @Operation(summary = "Xác nhận import người tham gia từ Excel (Owner)", description = "Lưu các dòng hợp lệ vào DB sau khi người dùng xem trước và xác nhận.")
    @PostMapping("/api/v1/owner/tournaments/{id}/participants/import-excel/confirm")
    public ResponseEntity<ApiResponse<ImportParticipantResultResponse>> confirmImportOwner(
            @PathVariable Long id,
            @Valid @RequestBody ParticipantImportConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success(participantExcelService.confirmImport(id, request)));
    }

    @Operation(summary = "Xác nhận import người tham gia từ Excel (Manager)", description = "Lưu các dòng hợp lệ vào DB sau khi người dùng xem trước và xác nhận.")
    @PostMapping("/api/v1/manager/tournaments/{id}/participants/import-excel/confirm")
    public ResponseEntity<ApiResponse<ImportParticipantResultResponse>> confirmImportManager(
            @PathVariable Long id,
            @Valid @RequestBody ParticipantImportConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success(participantExcelService.confirmImport(id, request)));
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

    /* ── Sửa lại số hạt giống (VD import/nhập tay bị sai) ── */
    @Operation(summary = "Sửa số hạt giống của người tham gia (Owner)",
            description = "Chỉ áp dụng khi roster còn sửa được (chưa bốc thăm). Truyền seedNo=null để bỏ hạt giống.")
    @PatchMapping("/api/v1/owner/participants/{participantId}/seed-no")
    @Transactional
    public ResponseEntity<ApiResponse<ParticipantResponse>> updateSeedNoOwner(
            @PathVariable Long participantId, @Valid @RequestBody UpdateSeedNoRequest request) {
        return ResponseEntity.ok(ApiResponse.success(updateSeedNo(participantId, request)));
    }

    @Operation(summary = "Sửa số hạt giống của người tham gia (Manager)",
            description = "Chỉ áp dụng khi roster còn sửa được (chưa bốc thăm). Truyền seedNo=null để bỏ hạt giống.")
    @PatchMapping("/api/v1/manager/participants/{participantId}/seed-no")
    @Transactional
    public ResponseEntity<ApiResponse<ParticipantResponse>> updateSeedNoManager(
            @PathVariable Long participantId, @Valid @RequestBody UpdateSeedNoRequest request) {
        return ResponseEntity.ok(ApiResponse.success(updateSeedNo(participantId, request)));
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
                .findByTournamentIdAndStatus(tournament.getId(), RegistrationStatus.APPROVED.getValue(),
                        org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        for (Registration reg : approvedRegs) {
            if (!participantRepository.existsByRegistrationId(reg.getId())) {
                registrationService.autoCreateParticipant(reg);
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
                    ? participantExcelService.buildImportTemplateCsv(tournamentId)
                    : participantExcelService.buildImportTemplate(tournamentId);
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

    private ParticipantResponse addManual(Long tournamentId, ManualAddParticipantRequest request,
                                          Authentication authentication) {
        Tournament tournament = tournamentRepository.findByIdWithLock(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!TournamentStatus.isRosterEditable(tournament.getStatus())) {
            throw new BusinessException(ErrorCode.TOURNAMENT_ROSTER_LOCKED);
        }

        if (tournament.getMaxParticipants() != null) {
            long activeCount = participantRepository.countByTournamentIdAndStatus(
                    tournamentId, ParticipantStatus.ACTIVE.getValue());
            if (activeCount >= tournament.getMaxParticipants()) {
                throw new BusinessException(ErrorCode.TOURNAMENT_FULL);
            }
        }

        User approver = securityUtil.resolveCurrentUser(authentication);

        String phone = request.getPhone();
        if (phone != null) phone = phone.trim().isEmpty() ? null : phone.trim();

        boolean isDouble = ParticipantType.DOUBLE.name().equals(tournament.getParticipantType());
        String partnerFullName = request.getPartnerFullName() != null ? request.getPartnerFullName().trim() : null;
        if (isDouble && (partnerFullName == null || partnerFullName.isEmpty())) {
            throw new BusinessException(ErrorCode.PARTICIPANT_PARTNER_REQUIRED);
        }
        String partnerPhone = request.getPartnerPhone();
        if (partnerPhone != null) partnerPhone = partnerPhone.trim().isEmpty() ? null : partnerPhone.trim();

        if (request.getSeedNo() != null && tournament.getMaxParticipants() != null
                && request.getSeedNo() > tournament.getMaxParticipants()) {
            throw new BusinessException(ErrorCode.PARTICIPANT_SEED_OUT_OF_RANGE);
        }

        if (request.getSeedNo() != null && participantRepository.existsByTournamentIdAndSeedNoAndStatus(
                tournamentId, request.getSeedNo(), ParticipantStatus.ACTIVE.getValue())) {
            throw new BusinessException(ErrorCode.PARTICIPANT_SEED_DUPLICATE);
        }

        String displayName = request.getDisplayName().trim();
        if (isDouble) {
            displayName = ParticipantMemberFactory.composeDoubleDisplayName(displayName, partnerFullName);
        }

        Registration registration = Registration.builder()
                .tournament(tournament)
                .user(null)
                .registrationType(RegistrationType.MANUAL.getValue())
                .playerFullName(displayName)
                .playerPhone(phone)
                .note(request.getNote())
                .status(RegistrationStatus.APPROVED.getValue())
                .approvedBy(approver)
                .approvedAt(java.time.Instant.now())
                .build();
        registration = registrationRepository.save(registration);

        Participant participant = Participant.builder()
                .tournament(tournament)
                .registration(registration)
                .participantType(tournament.getParticipantType())
                .displayName(displayName)
                .billiardRank(BilliardRank.fromNullable(request.getBilliardRank()).name())
                .seedNo(request.getSeedNo())
                .status(ParticipantStatus.ACTIVE.getValue())
                .build();
        participant = participantRepository.save(participant);

        if (isDouble) {
            participantMemberRepository.saveAll(ParticipantMemberFactory.buildDoubleMembers(
                    participant,
                    request.getDisplayName().trim(), phone, null,
                    partnerFullName, partnerPhone));
        }
        return toResponse(findParticipantWithDetails(participant.getId()));
    }


    /**
     * Sửa lại seedNo của 1 participant đã tồn tại (VD nhập tay/import Excel gán sai số). Chỉ cho
     * sửa khi roster còn mở (chưa bốc thăm) — sửa sau khi đã sinh bracket không có tác dụng gì vì
     * seeding chỉ đọc lúc bốc thăm, để tránh Owner tưởng nhầm là sửa xong bracket sẽ đổi theo.
     */
    private ParticipantResponse updateSeedNo(Long participantId, UpdateSeedNoRequest request) {
        Participant participant = findParticipantWithDetails(participantId);
        Tournament tournament = participant.getTournament();

        if (!TournamentStatus.isRosterEditable(tournament.getStatus())) {
            throw new BusinessException(ErrorCode.TOURNAMENT_ROSTER_LOCKED);
        }

        Integer newSeedNo = request.getSeedNo();
        if (newSeedNo != null && tournament.getMaxParticipants() != null
                && newSeedNo > tournament.getMaxParticipants()) {
            throw new BusinessException(ErrorCode.PARTICIPANT_SEED_OUT_OF_RANGE);
        }
        if (newSeedNo != null && participantRepository.existsByTournamentIdAndSeedNoAndStatusAndIdNot(
                tournament.getId(), newSeedNo, ParticipantStatus.ACTIVE.getValue(), participantId)) {
            throw new BusinessException(ErrorCode.PARTICIPANT_SEED_DUPLICATE);
        }

        participant.setSeedNo(newSeedNo);
        participantRepository.save(participant);
        return toResponse(participant);
    }

    private ParticipantResponse withdraw(Long participantId) {
        Participant participant = findParticipantWithDetails(participantId);
        participant.setStatus(ParticipantStatus.WITHDRAWN.getValue());
        // Giải phóng số hạt giống ngay khi rút lui — nếu không, ràng buộc unique
        // (tournament_id, seed_no) ở DB vẫn giữ chỗ vì nó không phân biệt theo status, khiến
        // không ai gán lại được số này dù pre-check ở tầng service đã lọc đúng ACTIVE.
        participant.setSeedNo(null);
        participantRepository.save(participant);
        publishParticipantWithdrawnEvent(participant);
        return toResponse(participant);
    }

    private void publishParticipantWithdrawnEvent(Participant participant) {
        Registration registration = participant.getRegistration();
        User user = registration != null ? registration.getUser() : null;
        if (user == null || user.getEmail() == null) {
            return;
        }
        java.util.Map<String, Object> variables = new java.util.HashMap<>(mailContextBuilder.systemContext());
        mailContextBuilder.putUser(variables, user);
        mailContextBuilder.putTournament(variables, participant.getTournament());
        eventPublisher.publishEvent(MailDomainEvent.builder()
                .eventType(EmailEventType.PARTICIPANT_WITHDRAWN)
                .tournamentId(participant.getTournament().getId())
                .variables(variables)
                .explicitRecipients(List.of(new MailRecipient(user.getId(), user.getEmail())))
                .entityKey("PARTICIPANT-WITHDRAWN-" + participant.getId())
                .build());
    }

    private Participant findParticipantWithDetails(Long participantId) {
        return participantRepository.findByIdWithDetails(participantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ParticipantResponse toResponse(Participant p) {
        Registration reg = p.getRegistration();
        String source = (reg != null && RegistrationType.MANUAL.getValue().equals(reg.getRegistrationType()))
                ? RegistrationType.MANUAL.getValue()
                : (reg != null ? RegistrationType.ONLINE_REGISTRATION.getValue() : RegistrationType.MANUAL.getValue());
        Long userId = (reg != null && reg.getUser() != null) ? reg.getUser().getId() : null;
        List<ParticipantResponse.MemberItem> members = participantMemberRepository
                .findByParticipantId(p.getId()).stream()
                .map(m -> ParticipantResponse.MemberItem.builder()
                        .fullName(m.getFullName())
                        .phone(m.getPhone())
                        .role(m.getRole())
                        .build())
                .toList();

        return ParticipantResponse.builder()
                .id(p.getId())
                .tournamentId(p.getTournament().getId())
                .tournamentName(p.getTournament().getName())
                .registrationId(reg != null ? reg.getId() : null)
                .userId(userId)
                .participantType(p.getParticipantType())
                .displayName(p.getDisplayName())
                .phone(reg != null ? reg.getPlayerPhone() : null)
                .billiardRank(p.getBilliardRank())
                .seedNo(p.getSeedNo())
                .status(p.getStatus())
                .source(source)
                .avtarUrl(p.getAvtarUrl())
                .members(members.isEmpty() ? null : members)
                .build();
    }
}
