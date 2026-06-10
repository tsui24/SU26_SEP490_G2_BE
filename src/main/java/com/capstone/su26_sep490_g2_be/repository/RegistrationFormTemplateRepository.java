package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.RegistrationFormTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistrationFormTemplateRepository extends JpaRepository<RegistrationFormTemplate, Long> {

	boolean existsByCode(String code);

	Optional<RegistrationFormTemplate> findByCode(String code);

	Page<RegistrationFormTemplate> findByIsActive(Boolean isActive, Pageable pageable);

	List<RegistrationFormTemplate> findByIsActiveTrueOrderBySortOrderAscCreatedAtAsc();
}
