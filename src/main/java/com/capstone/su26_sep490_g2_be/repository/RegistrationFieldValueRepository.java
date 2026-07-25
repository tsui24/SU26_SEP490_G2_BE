package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.RegistrationFieldValue;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFieldValueId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistrationFieldValueRepository
		extends JpaRepository<RegistrationFieldValue, RegistrationFieldValueId> {

	@EntityGraph(attributePaths = { "fieldDefinition" })
	List<RegistrationFieldValue> findByRegistrationIdOrderByIdAsc(Long registrationId);

	@EntityGraph(attributePaths = { "fieldDefinition" })
	List<RegistrationFieldValue> findByRegistrationIdInOrderByIdAsc(List<Long> registrationIds);

	void deleteByRegistrationId(Long registrationId);
}
