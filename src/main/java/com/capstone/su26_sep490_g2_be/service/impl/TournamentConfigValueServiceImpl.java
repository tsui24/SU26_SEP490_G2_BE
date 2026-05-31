package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValue;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValueId;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ConfigFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentConfigValueRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.TournamentConfigValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TournamentConfigValueServiceImpl implements TournamentConfigValueService {

	private final TournamentConfigValueRepository valueRepository;
	private final TournamentRepository tournamentRepository;
	private final ConfigFieldDefinitionRepository fieldRepository;

	@Override
	public List<TournamentConfigValue> getByTournament(Long tournamentId) {
		return valueRepository.findByIdTournamentId(tournamentId);
	}

	@Override
	public Optional<TournamentConfigValue> getByTournamentAndField(Long tournamentId, String fieldKey) {
		return valueRepository.findByIdTournamentIdAndIdFieldKey(tournamentId, fieldKey);
	}

	@Override
	@Transactional
	public List<TournamentConfigValue> saveAll(Long tournamentId, Map<String, String> values) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		List<TournamentConfigValue> result = new ArrayList<>();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			result.add(doSave(tournament, entry.getKey(), entry.getValue()));
		}
		return result;
	}

	@Override
	@Transactional
	public TournamentConfigValue save(Long tournamentId, String fieldKey, String value) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		return doSave(tournament, fieldKey, value);
	}

	@Override
	@Transactional
	public void deleteByTournament(Long tournamentId) {
		valueRepository.deleteByIdTournamentId(tournamentId);
	}

	private TournamentConfigValue doSave(Tournament tournament, String fieldKey, String value) {
		ConfigFieldDefinition fieldDef = fieldRepository.findById(fieldKey)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		TournamentConfigValueId id = new TournamentConfigValueId(tournament.getId(), fieldKey);
		TournamentConfigValue cv = valueRepository.findById(id)
				.orElseGet(() -> TournamentConfigValue.builder()
						.id(id)
						.tournament(tournament)
						.fieldDefinition(fieldDef)
						.build());
		cv.setValue(value);
		return valueRepository.save(cv);
	}
}
