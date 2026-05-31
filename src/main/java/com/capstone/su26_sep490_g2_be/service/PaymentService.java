package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PaymentService {

	Payment create(Payment payment);

	Payment getById(Long id);

	List<Payment> getByRegistration(Long registrationId);

	Page<Payment> getByUser(Long userId, Pageable pageable);

	Payment updateStatus(Long paymentId, String status, String transactionCode);
}
