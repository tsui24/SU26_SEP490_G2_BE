package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailSendLogRepository extends JpaRepository<EmailSendLog, Long> {

	Optional<EmailSendLog> findFirstByIdempotencyKeyAndCreatedAtAfter(String idempotencyKey, Instant after);

	@EntityGraph(attributePaths = { "template", "rule", "tournament" })
	Page<EmailSendLog> findByTournamentId(Long tournamentId, Pageable pageable);

	@EntityGraph(attributePaths = { "template", "rule", "tournament" })
	Page<EmailSendLog> findByTournamentIdAndStatus(Long tournamentId, String status, Pageable pageable);

	@EntityGraph(attributePaths = { "template", "rule", "tournament" })
	@Query("""
		SELECT l FROM EmailSendLog l
		WHERE (:status IS NULL OR l.status = :status)
		AND (:tournamentId IS NULL OR l.tournament.id = :tournamentId)
		AND (:triggerType IS NULL OR l.triggerType = :triggerType)
		AND (:templateCode IS NULL OR l.template.code = :templateCode)
		AND (:recipientEmail IS NULL OR LOWER(l.recipientEmail) LIKE LOWER(CONCAT('%', :recipientEmail, '%')))
		AND (:fromDate IS NULL OR l.createdAt >= :fromDate)
		AND (:toDate IS NULL OR l.createdAt <= :toDate)
		""")
	Page<EmailSendLog> search(
			@Param("status") String status,
			@Param("tournamentId") Long tournamentId,
			@Param("triggerType") String triggerType,
			@Param("templateCode") String templateCode,
			@Param("recipientEmail") String recipientEmail,
			@Param("fromDate") Instant fromDate,
			@Param("toDate") Instant toDate,
			Pageable pageable);
}
