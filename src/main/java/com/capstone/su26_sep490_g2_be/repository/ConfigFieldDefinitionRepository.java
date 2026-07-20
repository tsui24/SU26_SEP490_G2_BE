package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ConfigFieldDefinitionRepository extends JpaRepository<ConfigFieldDefinition, String>,
		JpaSpecificationExecutor<ConfigFieldDefinition> {

	List<ConfigFieldDefinition> findByIsActiveTrueOrderByFieldScopeAsc();

	List<ConfigFieldDefinition> findByFieldScope(String fieldScope);

	List<ConfigFieldDefinition> findByIsActiveTrueAndFieldScopeInOrderByFieldScopeAsc(List<String> scopes);

	List<ConfigFieldDefinition> findByIsActiveFalseOrderByFieldScopeAsc();

	void deleteByFieldKeyIn(List<String> fieldKeys);
}
