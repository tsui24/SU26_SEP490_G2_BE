package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Tournament;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

	Page<Tournament> findByStatus(String status, Pageable pageable);

	List<Tournament> findByCreatedById(Long userId);

	@Query("""
			SELECT t FROM Tournament t
			WHERE (:createdById IS NULL OR t.createdBy.id = :createdById)
			AND (:status IS NULL OR t.status = :status)
			AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<Tournament> searchTournaments(
			@Param("createdById") Long createdById,
			@Param("status") String status,
			@Param("search") String search,
			Pageable pageable);
}
