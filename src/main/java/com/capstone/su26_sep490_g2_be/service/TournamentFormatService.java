package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;

import java.util.List;

public interface TournamentFormatService {

	List<TournamentFormatDefinition> getActiveFormats();

	List<TournamentFormatDefinition> getAllFormats();

	TournamentFormatDefinition getByCode(String code);

	TournamentFormatDefinition create(TournamentFormatDefinition definition);

	TournamentFormatDefinition update(String code, TournamentFormatDefinition definition);

	void setActive(String code, boolean isActive);
}
