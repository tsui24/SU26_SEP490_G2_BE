package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ConfigFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.service.ConfigFieldDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfigFieldDefinitionServiceImpl implements ConfigFieldDefinitionService {

	private final ConfigFieldDefinitionRepository repository;

	@Override
	public List<ConfigFieldDefinition> getAll() {
		return repository.findAll();
	}

	@Override
	public List<ConfigFieldDefinition> getActive() {
		return repository.findByIsActiveTrueOrderByFieldScopeAsc();
	}

	@Override
	public List<ConfigFieldDefinition> getByScope(String fieldScope) {
		return repository.findByFieldScope(fieldScope);
	}

	@Override
	public ConfigFieldDefinition getByKey(String fieldKey) {
		return repository.findById(fieldKey)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	@Transactional
	public ConfigFieldDefinition create(ConfigFieldDefinition definition) {
		if (repository.existsById(definition.getFieldKey())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
		}
		return repository.save(definition);
	}

	@Override
	@Transactional
	public ConfigFieldDefinition update(String fieldKey, ConfigFieldDefinition data) {
		ConfigFieldDefinition existing = getByKey(fieldKey);
		existing.setLabel(data.getLabel());
		existing.setDescription(data.getDescription());
		existing.setEnumOptions(data.getEnumOptions());
		existing.setMinValue(data.getMinValue());
		existing.setMaxValue(data.getMaxValue());
		existing.setUiComponent(data.getUiComponent());
		return repository.save(existing);
	}

	@Override
	@Transactional
	public void setActive(String fieldKey, boolean isActive) {
		ConfigFieldDefinition existing = getByKey(fieldKey);
		existing.setIsActive(isActive);
		repository.save(existing);
	}
}
