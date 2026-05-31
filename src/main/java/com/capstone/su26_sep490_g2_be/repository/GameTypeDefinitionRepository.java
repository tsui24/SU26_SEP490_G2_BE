package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameTypeDefinitionRepository extends JpaRepository<GameTypeDefinition, String> {

	List<GameTypeDefinition> findByIsActiveTrueOrderByCreatedAtAsc();

	List<GameTypeDefinition> findAllByOrderByCreatedAtAsc();
}
