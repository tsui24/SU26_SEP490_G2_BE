package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailSendLogRepository extends JpaRepository<EmailSendLog, Long> {

	Optional<EmailSendLog> findFirstByIdempotencyKeyAndCreatedAtAfter(String idempotencyKey, Instant after);

	Page<EmailSendLog> findByTournamentId(Long tournamentId, Pageable pageable);

	Page<EmailSendLog> findByTournamentIdAndStatus(Long tournamentId, String status, Pageable pageable);

	@Query("""
		SELECT l FROM EmailSendLog l
		WHERE (:status IS NULL OR l.status = :status)
		AND (:tournamentId IS NULL OR l.tournament.id = :tournamentId)
		""")
	Page<EmailSendLog> search(@Param("status") String status, @Param("tournamentId") Long tournamentId, Pageable pageable);
}
