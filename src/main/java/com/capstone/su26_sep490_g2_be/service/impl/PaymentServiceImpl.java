package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

	private final PaymentRepository paymentRepository;

	@Override
	@Transactional
	public Payment create(Payment payment) {
		return paymentRepository.save(payment);
	}

	@Override
	public Payment getById(Long id) {
		return paymentRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	public List<Payment> getByRegistration(Long registrationId) {
		return paymentRepository.findByRegistrationId(registrationId);
	}

	@Override
	public Page<Payment> getByUser(Long userId, Pageable pageable) {
		return paymentRepository.findByUserId(userId, pageable);
	}

	@Override
	@Transactional
	public Payment updateStatus(Long paymentId, String status, String transactionCode) {
		Payment payment = getById(paymentId);
		payment.setStatus(status);
		if (transactionCode != null) {
			payment.setTransactionCode(transactionCode);
		}
		if ("SUCCESS".equals(status)) {
			payment.setPaidAt(Instant.now());
		}
		return paymentRepository.save(payment);
	}
}
