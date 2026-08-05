package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.dto.response.LeaderboardEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import com.capstone.su26_sep490_g2_be.enums.LeaderboardPeriod;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentResultRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentResultRepository.LeaderboardRow;
import com.capstone.su26_sep490_g2_be.service.LeaderboardService;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.util.AvatarUrlResolver;
import com.capstone.su26_sep490_g2_be.util.TournamentPointsPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

	private final TournamentResultRepository resultRepository;
	private final ParticipantRepository participantRepository;
	private final MinioStorageService minioStorageService;
	private final MinioProperties minioProperties;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<LeaderboardEntryResponse> getLeaderboard(
			LeaderboardPeriod period, Integer year, Integer quarter, Integer month, int page, int size) {

		LeaderboardPeriod resolved = period != null ? period : LeaderboardPeriod.ALL;
		LeaderboardPeriod.Range range = resolved.resolve(year, quarter, month);

		List<LeaderboardRow> rows = resultRepository.aggregateLeaderboard(range.from(), range.to());

		// Query đã ORDER BY tổng điểm giảm dần — hạng chính là vị trí trong danh sách
		List<LeaderboardEntryResponse> entries = new ArrayList<>(rows.size());
		int rank = 1;
		for (LeaderboardRow row : rows) {
			entries.add(LeaderboardEntryResponse.builder()
					.rank(rank++)
					.userId(row.getUserId())
					.playerName(resolveName(row))
					.avatarUrl(AvatarUrlResolver.resolveForList(
							row.getAvatarUrl(), minioStorageService, minioProperties.getBucket()))
					.totalPoints(row.getTotalPoints())
					.tournamentsPlayed(row.getTournamentsPlayed())
					.championCount(row.getChampionCount())
					.top3Count(row.getTop3Count())
					.totalPrizeAmount(row.getTotalPrizeAmount())
					.build());
		}

		return PageResponse.of(entries, page, size);
	}

	/** Ưu tiên tên trên hồ sơ, cuối cùng mới lấy tên đã dùng khi thi đấu. */
	private static String resolveName(LeaderboardRow row) {
		if (hasText(row.getProfileFullName())) return row.getProfileFullName();
		if (hasText(row.getProfileDisplayName())) return row.getProfileDisplayName();
		if (hasText(row.getParticipantName())) return row.getParticipantName();
		return "Cơ thủ #" + row.getUserId();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	@Override
	@Transactional
	public int recalculatePoints() {
		List<TournamentResult> results = resultRepository.findAllOfCompletedTournaments();
		if (results.isEmpty()) {
			return 0;
		}

		// Cache số participant theo giải — nhiều kết quả dùng chung một giải
		Map<Long, Integer> participantCountCache = new HashMap<>();
		List<TournamentResult> changed = new ArrayList<>();

		for (TournamentResult result : results) {
			if (result.getFinalRank() == null) continue;
			Long tournamentId = result.getTournament().getId();
			int participantCount = participantCountCache.computeIfAbsent(tournamentId,
					id -> (int) participantRepository.countByTournamentIdAndStatus(
							id, ParticipantStatus.ACTIVE.getValue()));

			int points = TournamentPointsPolicy.compute(result.getFinalRank(), participantCount);
			if (!Integer.valueOf(points).equals(result.getPointsEarned())) {
				result.setPointsEarned(points);
				changed.add(result);
			}
		}

		if (!changed.isEmpty()) {
			resultRepository.saveAll(changed);
		}
		log.info("Recalculate leaderboard points: {}/{} dòng được cập nhật", changed.size(), results.size());
		return changed.size();
	}
}
