package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfigFieldDefinitionRepository extends JpaRepository<ConfigFieldDefinition, String> {

	List<ConfigFieldDefinition> findByIsActiveTrueOrderByFieldScopeAsc();

	List<ConfigFieldDefinition> findByFieldScope(String fieldScope);
}
