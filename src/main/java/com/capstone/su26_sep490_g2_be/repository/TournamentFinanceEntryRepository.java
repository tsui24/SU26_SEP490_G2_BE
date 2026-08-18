package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.TournamentFinanceEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TournamentFinanceEntryRepository extends JpaRepository<TournamentFinanceEntry, Long> {

	List<TournamentFinanceEntry> findByTournamentIdOrderByOccurredAtDescIdDesc(Long tournamentId);

	/** Gộp SUM theo (tournament, entryType) cho nhiều giải cùng lúc — tránh N+1 query khi Analytics
	 * cần net profit cho cả danh sách giải thay vì 1 giải. Mỗi phần tử: [tournamentId, entryType, sum]. */
	@Query("""
		SELECT e.tournament.id, e.entryType, SUM(e.amount)
		FROM TournamentFinanceEntry e
		WHERE e.tournament.id IN :tournamentIds
		GROUP BY e.tournament.id, e.entryType
		""")
	List<Object[]> sumAmountsByTournamentIds(@Param("tournamentIds") List<Long> tournamentIds);
}
