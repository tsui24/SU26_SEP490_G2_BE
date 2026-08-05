package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.FormatConfigField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FormatConfigFieldRepository extends JpaRepository<FormatConfigField, Long> {

	List<FormatConfigField> findByFormatCodeOrderByIdAsc(String formatCode);

	@Query("""
		SELECT f FROM FormatConfigField f
		LEFT JOIN FETCH f.fieldDefinition
		WHERE f.formatCode IN :formatCodes
		ORDER BY f.formatCode ASC, f.id ASC
		""")
	List<FormatConfigField> findByFormatCodeInOrderByIdAsc(@Param("formatCodes") java.util.Collection<String> formatCodes);

	List<FormatConfigField> findByFormatCodeAndIsVisibleToOwnerTrueOrderByIdAsc(String formatCode);

	Optional<FormatConfigField> findByFormatCodeAndFieldKey(String formatCode, String fieldKey);

	boolean existsByFormatCodeAndFieldKey(String formatCode, String fieldKey);

	long countByFormatCode(String formatCode);

	void deleteByFieldKeyIn(List<String> fieldKeys);

	/**
	 * Gỡ liên kết field khỏi <b>một thể thức duy nhất</b>, giữ nguyên ở các thể thức khác.
	 * Khác với {@link #deleteByFieldKeyIn} (xoá field khỏi mọi thể thức) — dùng cho trường hợp
	 * field vẫn có tác dụng thật ở format này nhưng hoàn toàn vô nghĩa ở format kia.
	 */
	void deleteByFormatCodeAndFieldKey(String formatCode, String fieldKey);
}
