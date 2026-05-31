package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.TournamentFormatDefinitionRepository;
import com.capstone.su26_sep490_g2_be.service.TournamentFormatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TournamentFormatServiceImpl implements TournamentFormatService {

	private final TournamentFormatDefinitionRepository repository;

	@Override
	public List<TournamentFormatDefinition> getActiveFormats() {
		return repository.findByIsActiveTrueOrderBySortOrderAsc();
	}

	@Override
	public List<TournamentFormatDefinition> getAllFormats() {
		return repository.findAll();
	}

	@Override
	public TournamentFormatDefinition getByCode(String code) {
		return repository.findById(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	@Transactional
	public TournamentFormatDefinition create(TournamentFormatDefinition definition) {
		return repository.save(definition);
	}

	@Override
	@Transactional
	public TournamentFormatDefinition update(String code, TournamentFormatDefinition definition) {
		TournamentFormatDefinition existing = getByCode(code);
		existing.setName(definition.getName());
		existing.setDescription(definition.getDescription());
		existing.setDefaultConfigJson(definition.getDefaultConfigJson());
		existing.setConfigSchemaJson(definition.getConfigSchemaJson());
		existing.setSortOrder(definition.getSortOrder());
		return repository.save(existing);
	}

	@Override
	@Transactional
	public void setActive(String code, boolean isActive) {
		TournamentFormatDefinition existing = getByCode(code);
		existing.setIsActive(isActive);
		repository.save(existing);
	}
}
