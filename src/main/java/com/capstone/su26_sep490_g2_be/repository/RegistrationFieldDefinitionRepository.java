package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.RegistrationFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface RegistrationFieldDefinitionRepository
		extends JpaRepository<RegistrationFieldDefinition, String>, JpaSpecificationExecutor<RegistrationFieldDefinition> {

	List<RegistrationFieldDefinition> findByIsActiveTrueOrderByFieldKeyAsc();
}
