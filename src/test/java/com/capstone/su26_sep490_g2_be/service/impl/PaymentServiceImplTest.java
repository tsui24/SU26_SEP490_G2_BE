package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link PaymentServiceImpl}.
 *
 * <p>Mirrors the <b>PaymentService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-54 (payment records).
 *
 * <p>The decision a payment triggers — approve the entry or refund it — lives in
 * {@link RegistrationServiceImpl}. What this class owns is the record itself.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · PaymentService — UC-54")
class PaymentServiceImplTest {

	@Mock PaymentRepository paymentRepository;

	@InjectMocks PaymentServiceImpl service;

	private static final Long PAYMENT_ID = 77L;
	private static final Long REGISTRATION_ID = 60L;
	private static final Long USER_ID = 11L;

	private static Payment payment(String status) {
		return Payment.builder()
				.id(PAYMENT_ID).amount(new BigDecimal("200000"))
				.paymentMethod("PAYOS").status(status)
				.build();
	}

	@Test
	@DisplayName("TC-001 · Creating a payment stores it as given")
	void TC001_create_delegates() {
		Payment fresh = payment(PaymentStatus.PENDING.getValue());
		when(paymentRepository.save(fresh)).thenReturn(fresh);

		assertEquals(fresh, service.create(fresh));
	}

	@Test
	@DisplayName("TC-002 · Reading a payment by id")
	void TC002_getById_found() {
		when(paymentRepository.findById(PAYMENT_ID))
				.thenReturn(Optional.of(payment(PaymentStatus.PENDING.getValue())));

		assertEquals(new BigDecimal("200000"), service.getById(PAYMENT_ID).getAmount());
	}

	@Test
	@DisplayName("TC-003 · Reading a payment that does not exist")
	void TC003_getById_notFound() {
		when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(PAYMENT_ID));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-004 · One entry may carry several payment attempts")
	void TC004_getByRegistration_returnsEveryAttempt() {
		when(paymentRepository.findByRegistrationId(REGISTRATION_ID)).thenReturn(List.of(
				payment(PaymentStatus.CANCELLED.getValue()),
				payment(PaymentStatus.PENDING.getValue())));

		assertEquals(2, service.getByRegistration(REGISTRATION_ID).size(),
				"a cancelled attempt stays on record beside the live one");
	}

	@Test
	@DisplayName("TC-005 · A player's payment history comes back a page at a time")
	void TC005_getByUser_paged() {
		Pageable pageable = PageRequest.of(0, 10);
		when(paymentRepository.findByUserId(USER_ID, pageable))
				.thenReturn(new PageImpl<>(List.of(payment(PaymentStatus.SUCCESS.getValue()))));

		assertEquals(1, service.getByUser(USER_ID, pageable).getTotalElements());
	}

	@Test
	@DisplayName("TC-006 · Marking a payment successful stamps when it was paid")
	void TC006_updateStatus_successStampsPaidAt() {
		Payment existing = payment(PaymentStatus.PENDING.getValue());
		when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(existing));
		when(paymentRepository.save(existing)).thenReturn(existing);

		Payment updated = service.updateStatus(PAYMENT_ID, PaymentStatus.SUCCESS.getValue(), "PAYOS-REF-1");

		assertEquals(PaymentStatus.SUCCESS.getValue(), updated.getStatus());
		assertEquals("PAYOS-REF-1", updated.getTransactionCode());
		assertNotNull(updated.getPaidAt());
	}

	@Test
	@DisplayName("TC-007 · A failed payment carries no payment time")
	void TC007_updateStatus_failureLeavesPaidAtEmpty() {
		Payment existing = payment(PaymentStatus.PENDING.getValue());
		when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(existing));
		when(paymentRepository.save(existing)).thenReturn(existing);

		Payment updated = service.updateStatus(PAYMENT_ID, PaymentStatus.FAILED.getValue(), null);

		assertEquals(PaymentStatus.FAILED.getValue(), updated.getStatus());
		assertNull(updated.getPaidAt(), "only a successful payment has a moment of payment");
	}

	@Test
	@DisplayName("TC-008 · Updating without a reference keeps the one already recorded")
	void TC008_updateStatus_nullReferenceKeepsExisting() {
		Payment existing = payment(PaymentStatus.PENDING.getValue());
		existing.setTransactionCode("PAYOS-REF-OLD");
		when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(existing));
		when(paymentRepository.save(existing)).thenReturn(existing);

		Payment updated = service.updateStatus(PAYMENT_ID, PaymentStatus.CANCELLED.getValue(), null);

		assertEquals("PAYOS-REF-OLD", updated.getTransactionCode(),
				"a status change must not erase the reference the gateway already gave us");
	}

	@Test
	@DisplayName("TC-009 · Updating a payment that does not exist")
	void TC009_updateStatus_notFound() {
		when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateStatus(PAYMENT_ID, PaymentStatus.SUCCESS.getValue(), "PAYOS-REF-1"));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(paymentRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
	}
}
