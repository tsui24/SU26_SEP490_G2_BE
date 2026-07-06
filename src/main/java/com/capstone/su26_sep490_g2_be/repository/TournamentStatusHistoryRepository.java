package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.TournamentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TournamentStatusHistoryRepository extends JpaRepository<TournamentStatusHistory, Long> {

	@Query("""
		SELECT h FROM TournamentStatusHistory h
		LEFT JOIN FETCH h.changedBy u
		LEFT JOIN FETCH u.profile
		WHERE h.tournament.id = :tournamentId
		ORDER BY h.createdAt DESC, h.id DESC
		""")
	List<TournamentStatusHistory> findByTournamentIdOrderByCreatedAtDesc(@Param("tournamentId") Long tournamentId);

	boolean existsByTournamentIdAndChangeType(Long tournamentId, String changeType);
}
