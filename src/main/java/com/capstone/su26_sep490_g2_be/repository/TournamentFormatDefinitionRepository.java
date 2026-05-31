package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentFormatDefinitionRepository extends JpaRepository<TournamentFormatDefinition, String> {

	List<TournamentFormatDefinition> findByIsActiveTrueOrderByCreatedAtAsc();

	List<TournamentFormatDefinition> findByIsActiveOrderByCreatedAtAsc(Boolean isActive);

	List<TournamentFormatDefinition> findAllByOrderByCreatedAtAsc();

	boolean existsByHandlerKey(String handlerKey);
}
