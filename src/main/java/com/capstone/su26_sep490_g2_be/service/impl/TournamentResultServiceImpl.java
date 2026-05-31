package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.TournamentResultRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.TournamentResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TournamentResultServiceImpl implements TournamentResultService {

	private final TournamentResultRepository resultRepository;
	private final UserRepository userRepository;

	@Override
	public List<TournamentResult> getByTournament(Long tournamentId) {
		return resultRepository.findByTournamentIdOrderByFinalRankAsc(tournamentId);
	}

	@Override
	@Transactional
	public TournamentResult record(TournamentResult result) {
		if (result.getRecordedAt() == null) {
			result.setRecordedAt(Instant.now());
		}
		return resultRepository.save(result);
	}

	@Override
	@Transactional
	public void finalizeTournamentResults(Long tournamentId, Long recordedByUserId) {
		User recordedBy = userRepository.findById(recordedByUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		List<TournamentResult> results = resultRepository.findByTournamentIdOrderByFinalRankAsc(tournamentId);
		Instant now = Instant.now();
		results.forEach(r -> {
			r.setRecordedBy(recordedBy);
			r.setRecordedAt(now);
		});
		resultRepository.saveAll(results);
	}
}
