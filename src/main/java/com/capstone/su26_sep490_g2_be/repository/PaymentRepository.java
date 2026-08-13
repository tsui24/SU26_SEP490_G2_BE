package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

	@Override
	@EntityGraph(attributePaths = { "registration", "registration.tournament" })
	Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "registration", "registration.tournament" })
	List<Payment> findByRegistrationId(Long registrationId);

	@EntityGraph(attributePaths = { "registration", "registration.tournament" })
	Page<Payment> findByUserId(Long userId, Pageable pageable);

	Optional<Payment> findByTransactionCode(String transactionCode);

	@EntityGraph(attributePaths = { "registration", "registration.tournament" })
	List<Payment> findByRegistration_Tournament_IdIn(List<Long> tournamentIds);

	Optional<Payment> findFirstByRegistrationIdAndStatusOrderByPaidAtDesc(Long registrationId, String status);

	List<Payment> findByRegistrationIdInAndStatus(List<Long> registrationIds, String status);
}
