package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValue;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValueId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentConfigValueRepository extends JpaRepository<TournamentConfigValue, TournamentConfigValueId> {

	List<TournamentConfigValue> findByIdTournamentId(Long tournamentId);

	List<TournamentConfigValue> findByIdTournamentIdIn(List<Long> tournamentIds);

	Optional<TournamentConfigValue> findByIdTournamentIdAndIdFieldKey(Long tournamentId, String fieldKey);

	void deleteByIdTournamentId(Long tournamentId);

	void deleteByIdFieldKeyIn(List<String> fieldKeys);

	/**
	 * Xoá giá trị đã lưu của một field, nhưng <b>chỉ ở các giải thuộc thể thức chỉ định</b>.
	 * Dùng khi field bị gỡ khỏi một format mà vẫn còn sống ở format khác — nếu xoá theo fieldKey
	 * không thì giải của format kia cũng mất dữ liệu.
	 */
	@Modifying
	@Query("""
		DELETE FROM TournamentConfigValue v
		WHERE v.id.fieldKey = :fieldKey
		  AND v.id.tournamentId IN (SELECT t.id FROM Tournament t WHERE t.format = :formatCode)
		""")
	int deleteByFieldKeyForFormat(@Param("fieldKey") String fieldKey, @Param("formatCode") String formatCode);
}
