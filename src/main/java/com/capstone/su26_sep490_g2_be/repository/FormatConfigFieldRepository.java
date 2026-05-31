package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.FormatConfigField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormatConfigFieldRepository extends JpaRepository<FormatConfigField, Long> {

	List<FormatConfigField> findByFormatCodeOrderByIdAsc(String formatCode);

	List<FormatConfigField> findByFormatCodeAndIsVisibleToOwnerTrueOrderByIdAsc(String formatCode);

	Optional<FormatConfigField> findByFormatCodeAndFieldKey(String formatCode, String fieldKey);

	boolean existsByFormatCodeAndFieldKey(String formatCode, String fieldKey);

	long countByFormatCode(String formatCode);
}
