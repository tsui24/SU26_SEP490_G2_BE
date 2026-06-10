package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.RegistrationFormTemplateField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistrationFormTemplateFieldRepository extends JpaRepository<RegistrationFormTemplateField, Long> {

	List<RegistrationFormTemplateField> findByTemplateIdOrderBySortOrderAscIdAsc(Long templateId);

	Optional<RegistrationFormTemplateField> findByTemplateIdAndFieldKey(Long templateId, String fieldKey);

	long countByTemplateId(Long templateId);

	long countByFieldKey(String fieldKey);

	void deleteByTemplateIdAndFieldKey(Long templateId, String fieldKey);
}
