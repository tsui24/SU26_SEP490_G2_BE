package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.DashboardStatsResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Dashboard", description = "Thống kê tổng quan")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DashboardController {

    private final TournamentRepository tournamentRepository;
    private final RegistrationRepository registrationRepository;
    private final ParticipantRepository participantRepository;
    private final PaymentRepository paymentRepository;

    @Operation(summary = "Thống kê tổng quan — Owner")
    @GetMapping("/owner/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> ownerStats(Authentication authentication) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(buildStats(userId)));
    }

    @Operation(summary = "Thống kê tổng quan — Manager")
    @GetMapping("/manager/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> managerStats() {
        return ResponseEntity.ok(ApiResponse.success(buildStats(null)));
    }

    private DashboardStatsResponse buildStats(Long ownerUserId) {
        long total = ownerUserId != null
                ? tournamentRepository.findByCreatedById(ownerUserId).size()
                : tournamentRepository.count();

        long active = countByStatuses(ownerUserId,
                List.of(
                        TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
                        TournamentStatus.REGISTRATION_CLOSED.getValue(),
                        TournamentStatus.DRAW_DONE.getValue(),
                        TournamentStatus.IN_PROGRESS.getValue()));

        long openReg = countByStatuses(ownerUserId,
                List.of(TournamentStatus.OPEN_FOR_REGISTRATION.getValue()));

        long totalReg = registrationRepository.count();
        long pendingReg = registrationRepository.findAll().stream()
                .filter(r -> RegistrationStatus.PENDING_PAYMENT.getValue().equals(r.getStatus()))
                .count();

        long         totalParticipants = participantRepository.countByTournamentIdAndStatus(
                0L, ParticipantStatus.ACTIVE.getValue()); // fallback approach

        // Count all active participants
        totalParticipants = participantRepository.findAll().stream()
                .filter(p -> ParticipantStatus.ACTIVE.getValue().equals(p.getStatus()))
                .count();

        BigDecimal revenue = paymentRepository.findAll().stream()
                .filter(p -> PaymentStatus.SUCCESS.getValue().equals(p.getStatus()))
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardStatsResponse.builder()
                .totalTournaments(total)
                .activeTournaments(active)
                .openTournaments(openReg)
                .totalRegistrations(totalReg)
                .pendingRegistrations(pendingReg)
                .totalParticipants(totalParticipants)
                .totalRevenue(revenue)
                .build();
    }

    private long countByStatuses(Long ownerUserId, List<String> statuses) {
        if (ownerUserId != null) {
            return tournamentRepository.findByCreatedById(ownerUserId).stream()
                    .filter(t -> statuses.contains(t.getStatus()))
                    .count();
        }
        return statuses.stream()
                .mapToLong(s -> tournamentRepository.findByStatus(s, PageRequest.of(0, 1)).getTotalElements())
                .sum();
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || !(auth.getCredentials() instanceof Long)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return (Long) auth.getCredentials();
    }
}
