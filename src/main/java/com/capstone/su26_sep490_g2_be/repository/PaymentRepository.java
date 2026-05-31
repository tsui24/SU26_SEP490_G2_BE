package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	List<Payment> findByRegistrationId(Long registrationId);

	Page<Payment> findByUserId(Long userId, Pageable pageable);

	Optional<Payment> findByTransactionCode(String transactionCode);
}
