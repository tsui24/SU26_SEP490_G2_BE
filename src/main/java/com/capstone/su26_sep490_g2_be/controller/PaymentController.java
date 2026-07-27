package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.CheckoutResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PaymentHistoryResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.PayOSService;
import com.capstone.su26_sep490_g2_be.service.PaymentService;
import com.capstone.su26_sep490_g2_be.service.RegistrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Payment", description = "Thanh toán qua PayOS")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final RegistrationService registrationService;
    private final PaymentService paymentService;
    private final PayOSService payOSService;
    private final BranchAccessService branchAccessService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /* ── Player: tạo checkout session ── */
    @Operation(summary = "Tạo link thanh toán PayOS cho đăng ký")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/player/registrations/{id}/checkout")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            Authentication authentication,
            @PathVariable Long id) {
        Long userId = extractUserId(authentication);
        CheckoutResponse response = registrationService.checkout(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Link thanh toán đã được tạo", response));
    }

    /* ── Player: lịch sử thanh toán ── */
    @Operation(summary = "Lịch sử thanh toán của tôi")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/player/payments")
    public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryResponse>>> getMyPayments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = extractUserId(authentication);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = paymentService.getByUser(userId, pageable);
        var mapped = PageResponse.of(result, this::toHistoryResponse);
        return ResponseEntity.ok(ApiResponse.success(mapped));
    }

    /**
     * Xử lý return URL từ PayOS — được gọi từ frontend sau khi user thanh toán thành công.
     * Đây là backup cho webhook (webhook không hoạt động khi dev trên localhost).
     *
     * KHÔNG được tin trạng thái do client gửi lên (query param {@code status} cũ) — client có thể
     * tự gọi endpoint này với orderCode bất kỳ để tự đánh dấu đã thanh toán. Phải luôn hỏi lại PayOS
     * xem đơn hàng thực sự đã PAID chưa, và chỉ cho phép chủ sở hữu payment xác nhận đơn của mình.
     */
    @Operation(summary = "Xác nhận thanh toán từ return URL (player)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/player/payments/confirm-return")
    public ResponseEntity<ApiResponse<Void>> confirmReturn(
            Authentication authentication,
            @RequestParam long orderCode) {
        Long userId = extractUserId(authentication);
        Payment payment = paymentService.getById(orderCode);
        if (payment.getUser() == null || !payment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
        String remoteStatus = payOSService.getOrderStatus(orderCode);
        if ("PAID".equalsIgnoreCase(remoteStatus)) {
            registrationService.markAsPaid(orderCode, null);
            log.info("Payment confirmed via return URL: orderCode={}", orderCode);
        } else {
            log.info("Payment confirm-return ignored, PayOS status={} for orderCode={}", remoteStatus, orderCode);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /* ── Webhook PayOS (public) ── */
    @Operation(summary = "PayOS webhook — nhận kết quả thanh toán")
    @PostMapping("/payments/payos/webhook")
    public ResponseEntity<String> webhook(@RequestBody String rawBody) {
        try {
            if (!payOSService.verifyWebhookSignature(rawBody)) {
                log.warn("PayOS webhook: invalid signature");
                return ResponseEntity.ok("{\"error\":0}");
            }
            JsonNode root = objectMapper.readTree(rawBody);
            String code = root.path("code").asText();
            if ("00".equals(code)) {
                long orderCode = root.path("data").path("orderCode").asLong();
                String ref = root.path("data").path("reference").asText(null);
                registrationService.markAsPaid(orderCode, ref);
                log.info("PayOS payment confirmed: orderCode={}", orderCode);
            }
        } catch (Exception e) {
            log.error("PayOS webhook error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok("{\"error\":0}");
    }

    /* ── Manager/Owner: lịch sử thanh toán theo đăng ký ── */
    @Operation(summary = "Danh sách thanh toán theo registration (Manager/Owner)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/manager/registrations/{id}/payments")
    public ResponseEntity<ApiResponse<java.util.List<PaymentHistoryResponse>>> getPaymentsByRegistration(
            Authentication authentication,
            @PathVariable Long id) {
        assertStaffCanAccessRegistration(extractUserId(authentication), id);
        var list = paymentService.getByRegistration(id).stream()
                .map(this::toHistoryResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/owner/registrations/{id}/payments")
    public ResponseEntity<ApiResponse<java.util.List<PaymentHistoryResponse>>> getPaymentsByRegistrationOwner(
            Authentication authentication,
            @PathVariable Long id) {
        assertStaffCanAccessRegistration(extractUserId(authentication), id);
        var list = paymentService.getByRegistration(id).stream()
                .map(this::toHistoryResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /** 1 chuỗi duy nhất nên Owner xem được thanh toán của cả chuỗi; Manager chỉ chi nhánh được cấp quyền. */
    private void assertStaffCanAccessRegistration(Long staffUserId, Long registrationId) {
        Registration registration = registrationService.getById(registrationId);
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
        Branch branch = registration.getTournament().getBranch();
        Long branchId = branch != null ? branch.getId() : null;
        if (!branchAccessService.canActorAccessBranch(staff, branchId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
    }

    private PaymentHistoryResponse toHistoryResponse(Payment p) {
        String tournamentName = null;
        Long tournamentId = null;
        if (p.getRegistration() != null && p.getRegistration().getTournament() != null) {
            tournamentName = p.getRegistration().getTournament().getName();
            tournamentId = p.getRegistration().getTournament().getId();
        }
        return PaymentHistoryResponse.builder()
                .id(p.getId())
                .registrationId(p.getRegistration() != null ? p.getRegistration().getId() : null)
                .tournamentId(tournamentId)
                .tournamentName(tournamentName)
                .amount(p.getAmount())
                .paymentMethod(p.getPaymentMethod())
                .status(p.getStatus())
                .transactionCode(p.getTransactionCode())
                .checkoutUrl(p.getCheckoutUrl())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getCredentials() instanceof Long)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return (Long) authentication.getCredentials();
    }
}
