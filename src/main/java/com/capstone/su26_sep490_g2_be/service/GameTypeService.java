package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;

import java.util.List;

public interface GameTypeService {

	List<GameTypeDefinition> getActiveGameTypes();

	List<GameTypeDefinition> getAllGameTypes();

	GameTypeDefinition getByCode(String code);

	GameTypeDefinition create(GameTypeDefinition definition);

	GameTypeDefinition update(String code, GameTypeDefinition definition);

	void setActive(String code, boolean isActive);
}
