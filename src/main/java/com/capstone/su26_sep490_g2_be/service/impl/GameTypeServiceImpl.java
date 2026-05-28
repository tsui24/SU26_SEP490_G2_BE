package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import com.capstone.su26_sep490_g2_be.service.GameTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GameTypeServiceImpl implements GameTypeService {

	private final GameTypeDefinitionRepository repository;

	@Override
	public List<GameTypeDefinition> getActiveGameTypes() {
		return repository.findByIsActiveTrueOrderBySortOrderAsc();
	}

	@Override
	public List<GameTypeDefinition> getAllGameTypes() {
		return repository.findAll();
	}

	@Override
	public GameTypeDefinition getByCode(String code) {
		return repository.findById(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	@Transactional
	public GameTypeDefinition create(GameTypeDefinition definition) {
		return repository.save(definition);
	}

	@Override
	@Transactional
	public GameTypeDefinition update(String code, GameTypeDefinition definition) {
		GameTypeDefinition existing = getByCode(code);
		existing.setName(definition.getName());
		existing.setDescription(definition.getDescription());
		existing.setDefaultRaceTo(definition.getDefaultRaceTo());
		existing.setCompatibleTableTypes(definition.getCompatibleTableTypes());
		existing.setSortOrder(definition.getSortOrder());
		return repository.save(existing);
	}

	@Override
	@Transactional
	public void setActive(String code, boolean isActive) {
		GameTypeDefinition existing = getByCode(code);
		existing.setIsActive(isActive);
		repository.save(existing);
	}
}
