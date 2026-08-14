package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.EmailAutomationRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailAutomationRuleRepository extends JpaRepository<EmailAutomationRule, Long> {

	Optional<EmailAutomationRule> findByCode(String code);

	boolean existsByCode(String code);

	@EntityGraph(attributePaths = { "template", "tournament" })
	List<EmailAutomationRule> findByEventTypeAndIsEnabledTrueAndTournamentIsNull(String eventType);

	@EntityGraph(attributePaths = { "template", "tournament" })
	List<EmailAutomationRule> findByEventTypeAndIsEnabledTrueAndTournamentId(String eventType, Long tournamentId);

	/** Không lọc isEnabled — cần biết giải có override cho sự kiện này hay không, kể cả khi đang tắt. */
	@EntityGraph(attributePaths = { "template", "tournament" })
	List<EmailAutomationRule> findByEventTypeAndTournamentId(String eventType, Long tournamentId);

	@EntityGraph(attributePaths = { "template", "tournament" })
	List<EmailAutomationRule> findByTournamentId(Long tournamentId);

	Optional<EmailAutomationRule> findByCodeAndTournamentId(String code, Long tournamentId);

	@EntityGraph(attributePaths = { "template", "tournament" })
	List<EmailAutomationRule> findByScope(String scope);

	long countByIsEnabled(Boolean isEnabled);
}
