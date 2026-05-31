package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;

import java.util.List;

public interface ConfigFieldDefinitionService {

	List<ConfigFieldDefinition> getAll();

	List<ConfigFieldDefinition> getActive();

	List<ConfigFieldDefinition> getByScope(String fieldScope);

	ConfigFieldDefinition getByKey(String fieldKey);

	ConfigFieldDefinition create(ConfigFieldDefinition definition);

	ConfigFieldDefinition update(String fieldKey, ConfigFieldDefinition definition);

	void setActive(String fieldKey, boolean isActive);
}
