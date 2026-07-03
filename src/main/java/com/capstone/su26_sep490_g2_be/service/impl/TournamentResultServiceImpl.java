package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.PlayerPublicProfileResponse;
import com.capstone.su26_sep490_g2_be.dto.response.StandingsEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingResponse;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.RankingLabelFormat;
import com.capstone.su26_sep490_g2_be.enums.RankingPlacementNote;
import com.capstone.su26_sep490_g2_be.enums.TournamentFormat;
import com.capstone.su26_sep490_g2_be.enums.TournamentStageType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentResultRepository;
import com.capstone.su26_sep490_g2_be.repository.UserProfileRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.TournamentResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service quản lý kết quả giải đấu và bảng xếp hạng cơ thủ.
 *
 * <p>Hai nhóm chức năng:
 * <ul>
 *   <li><b>TournamentResult</b> — ghi nhận hạng chính thức sau khi giải kết thúc (có người duyệt).</li>
 *   <li><b>getRankings</b> — tính xếp hạng tạm thời/chính thức từ kết quả trận đấu trên bracket,
 *       không tính tiền giải.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TournamentResultServiceImpl implements TournamentResultService {

	private final TournamentResultRepository resultRepository;
	private final UserRepository userRepository;
	private final TournamentRepository tournamentRepository;
	private final MatchRepository matchRepository;
	private final BracketGenerationService bracketGenerationService;
	private final ParticipantRepository participantRepository;
	private final UserProfileRepository userProfileRepository;

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

	/**
	 * Gắn người ghi nhận và thời điểm cho toàn bộ kết quả đã có của giải.
	 * Gọi khi BQT chốt kết quả chính thức.
	 */
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

	/* ═══════════════════════════════════════════════════════════
	 *  RANKINGS — xếp hạng cơ thủ theo bracket (chưa tính tiền giải)
	 * ═══════════════════════════════════════════════════════════ */

	/**
	 * API chính trả bảng xếp hạng cho tab "Xếp hạng" trên FE.
	 *
	 * <p>Luồng xử lý:
	 * <ol>
	 *   <li>Load giải đấu — ném lỗi nếu không tồn tại.</li>
	 *   <li>Chọn chiến lược tính hạng theo {@link TournamentFormat}:
	 *       GROUP_PLAYOFF dùng bảng điểm vòng tròn; các format còn lại dùng placement loại trực tiếp.</li>
	 *   <li>Đánh dấu {@code isOfficial = true} chỉ khi giải đã {@link TournamentStatus#COMPLETED}.</li>
	 * </ol>
	 */
	@Override
	@Transactional(readOnly = true)
	public TournamentRankingResponse getRankings(Long tournamentId) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		List<TournamentRankingEntryResponse> entries = resolveRankingEntries(tournament);

		return TournamentRankingResponse.builder()
				.tournamentId(tournamentId)
				.tournamentStatus(tournament.getStatus())
				.isOfficial(TournamentStatus.COMPLETED.getValue().equals(tournament.getStatus()))
				.entries(entries)
				.build();
	}

	/**
	 * Phân nhánh chiến lược xếp hạng theo thể thức giải.
	 * Mỗi format có nguồn dữ liệu và quy tắc placement khác nhau.
	 */
	private List<TournamentRankingEntryResponse> resolveRankingEntries(Tournament tournament) {
		String formatCode = tournament.getFormat();
		if (TournamentFormat.GROUP_PLAYOFF.getValue().equals(formatCode)) {
			// Vòng tròn: hạng = thứ tự trên bảng điểm (điểm, hiệu số, đối đầu...)
			return fromGroupStandings(tournament.getId());
		}
		// Single/Double elimination và knockout playoff: hạng = vòng bị loại
		return computeSingleEliminationRankings(tournament.getId());
	}

	/**
	 * Xếp hạng cho giải GROUP_PLAYOFF — lấy từ bảng điểm vòng tròn đã tính sẵn.
	 *
	 * <p>Mỗi cơ thủ có hạng riêng (không gộp nhóm #3-4 như knockout).
	 * Hạng 1 được gắn note {@link RankingPlacementNote#GROUP_LEADER}.
	 */
	private List<TournamentRankingEntryResponse> fromGroupStandings(Long tournamentId) {
		List<StandingsEntryResponse> standings = bracketGenerationService.getLeagueStandings(tournamentId);
		if (standings.isEmpty()) {
			return List.of();
		}

		List<TournamentRankingEntryResponse> entries = new ArrayList<>();
		for (StandingsEntryResponse standing : standings) {
			int rank = standing.getRank() != null ? standing.getRank() : entries.size() + 1;
			entries.add(TournamentRankingEntryResponse.builder()
					.sortOrder(rank)
					.rankLabel(RankingLabelFormat.single(rank))
					.rankFrom(rank)
					.rankTo(rank)
					.participantId(standing.getParticipantId())
					.displayName(standing.getDisplayName())
					.note(rank == 1 ? RankingPlacementNote.GROUP_LEADER.getValue() : null)
					.build());
		}
		return entries;
	}

	/**
	 * Xếp hạng loại trực tiếp (Single Elimination, Double Elimination playoff, v.v.).
	 *
	 * <p>Quy tắc placement theo vòng bị loại (WNT-style):
	 * <ul>
	 *   <li>Chung kết: winner = #1 (Vô địch), loser = #2 (Á quân)</li>
	 *   <li>Trận tranh hạng 3 (nếu có): winner = #3, loser = #4</li>
	 *   <li>Không có trận tranh hạng 3: 2 người thua bán kết gộp #3-4</li>
	 *   <li>Các vòng trước: gộp theo khoảng (#5-8, #9-16...) theo công thức 2^(maxRound - elimRound)</li>
	 * </ul>
	 *
	 * <p>Chỉ xét trận knockout — bỏ qua vòng bảng GROUP.
	 */
	private List<TournamentRankingEntryResponse> computeSingleEliminationRankings(Long tournamentId) {
		List<Match> allMatches = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournamentId);

		// Lọc chỉ trận knockout: loại vòng bảng GROUP, giữ trận 3RD nếu có roundNo hợp lệ
		List<Match> knockoutMatches = allMatches.stream()
				.filter(m -> !TournamentStageType.GROUP.getValue().equals(stageTypeOf(m)))
				.filter(m -> !MatchCode.THIRD_PLACE.getValue().equals(m.getMatchCode()) || m.getRoundNo() != null)
				.toList();

		if (knockoutMatches.isEmpty()) {
			return List.of();
		}

		// Trận tranh hạng 3 (match_code = 3RD) — tách khỏi nhánh chính khi tính maxRound
		Match thirdPlaceMatch = knockoutMatches.stream()
				.filter(m -> MatchCode.THIRD_PLACE.getValue().equals(m.getMatchCode()))
				.findFirst()
				.orElse(null);

		// Vòng cao nhất của nhánh chính (chung kết = round maxRound, position 1)
		int maxRound = knockoutMatches.stream()
				.filter(m -> !MatchCode.THIRD_PLACE.getValue().equals(m.getMatchCode()))
				.mapToInt(Match::getRoundNo)
				.max()
				.orElse(0);

		if (maxRound == 0) {
			return List.of();
		}

		Match finalMatch = knockoutMatches.stream()
				.filter(m -> m.getRoundNo() == maxRound && m.getPositionNo() == 1)
				.findFirst()
				.orElse(null);

		List<TournamentRankingEntryResponse> entries = new ArrayList<>();
		Set<Long> placedParticipantIds = new HashSet<>();

		// ── Bước 1: Chung kết → hạng 1 và 2 ─────────────────────────────
		if (finalMatch != null && isFinished(finalMatch)) {
			if (finalMatch.getWinner() != null) {
				addEntry(entries, placedParticipantIds, finalMatch.getWinner(),
						1, 1, RankingPlacementNote.CHAMPION.getValue());
			}
			if (finalMatch.getLoser() != null) {
				addEntry(entries, placedParticipantIds, finalMatch.getLoser(),
						2, 2, RankingPlacementNote.RUNNER_UP.getValue());
			}
		}

		// ── Bước 2: Hạng 3-4 — trận tranh hạng 3 HOẶC gộp 2 người thua bán kết ──
		if (thirdPlaceMatch != null && isFinished(thirdPlaceMatch)) {
			if (thirdPlaceMatch.getWinner() != null) {
				addEntry(entries, placedParticipantIds, thirdPlaceMatch.getWinner(),
						3, 3, RankingPlacementNote.THIRD_PLACE.getValue());
			}
			if (thirdPlaceMatch.getLoser() != null) {
				addEntry(entries, placedParticipantIds, thirdPlaceMatch.getLoser(),
						4, 4, RankingPlacementNote.FOURTH_PLACE.getValue());
			}
		} else if (maxRound >= 2) {
			// Không có trận 3RD: 2 người thua bán kết (round = maxRound - 1) gộp #3-4
			int semiFinalRound = maxRound - 1;
			for (Match match : knockoutMatches) {
				if (MatchCode.THIRD_PLACE.getValue().equals(match.getMatchCode())) {
					continue;
				}
				if (match.getRoundNo() == semiFinalRound && isFinished(match) && match.getLoser() != null) {
					addEntry(entries, placedParticipantIds, match.getLoser(),
							3, 4, RankingPlacementNote.SEMI_FINAL.getValue());
				}
			}
		}

		// ── Bước 3: Các vòng trước bán kết — gộp theo khoảng (#5-8, #9-16...) ──
		for (int eliminationRound = maxRound - 2; eliminationRound >= 1; eliminationRound--) {
			int[] rankRange = placementRangeForEliminationRound(eliminationRound, maxRound);
			for (Match match : knockoutMatches) {
				if (MatchCode.THIRD_PLACE.getValue().equals(match.getMatchCode())) {
					continue;
				}
				if (match.getRoundNo() == eliminationRound && isFinished(match) && match.getLoser() != null) {
					addEntry(entries, placedParticipantIds, match.getLoser(),
							rankRange[0], rankRange[1], null);
				}
			}
		}

		// Sắp xếp: hạng thấp trước (#1 đầu danh sách), cùng hạng thì theo tên
		entries.sort(Comparator
				.comparingInt(TournamentRankingEntryResponse::getSortOrder)
				.thenComparing(TournamentRankingEntryResponse::getDisplayName,
						Comparator.nullsLast(String::compareToIgnoreCase)));

		return entries;
	}

	/** Lấy stage_type của trận (GROUP, PLAYOFF, KNOCKOUT...) để lọc vòng bảng. */
	private static String stageTypeOf(Match match) {
		return match.getStage() != null ? match.getStage().getStageType() : "";
	}

	/**
	 * Trận đã có kết quả cuối — dùng để quyết định ai bị loại ở vòng nào.
	 * BYE không tạo placement vì không có người thua thực sự.
	 */
	private static boolean isFinished(Match match) {
		String status = match.getStatus();
		return MatchStatus.COMPLETED.getValue().equals(status)
				|| MatchStatus.WALKOVER.getValue().equals(status);
	}

	/**
	 * Tính khoảng hạng cho người thua ở vòng loại {@code eliminationRound}.
	 *
	 * <p>Công thức bracket 2^n:
	 * <ul>
	 *   <li>Vòng (maxRound - 1) bán kết → #3-4 (xử lý riêng ở bước 2)</li>
	 *   <li>Vòng (maxRound - 2) tứ kết → #5-8</li>
	 *   <li>Vòng (maxRound - 3) vòng 16 → #9-16</li>
	 * </ul>
	 *
	 * @param eliminationRound vòng bị loại (1 = vòng 1, 2 = vòng 2...)
	 * @param maxRound           vòng cao nhất của nhánh chính (chung kết)
	 * @return mảng [rankFrom, rankTo]
	 */
	private static int[] placementRangeForEliminationRound(int eliminationRound, int maxRound) {
		int from = (1 << (maxRound - eliminationRound)) + 1;
		int to = 1 << (maxRound - eliminationRound + 1);
		return new int[] { from, to };
	}

	/* ═══════════════════════════════════════════════════════════
	 *  PLAYER PUBLIC PROFILE — hồ sơ cơ thủ kèm lịch sử thành tích
	 * ═══════════════════════════════════════════════════════════ */

	@Override
	@Transactional(readOnly = true)
	public PlayerPublicProfileResponse getParticipantProfile(Long participantId) {
		Participant participant = participantRepository.findByIdWithDetails(participantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		// Lấy avatar từ participant, fallback sang UserProfile nếu có
		String avatarUrl = participant.getAvtarUrl();
		String billiardRank = null;
		String bio = null;
		Long userId = null;

		if (participant.getRegistration() != null && participant.getRegistration().getUser() != null) {
			userId = participant.getRegistration().getUser().getId();
			UserProfile profile = userProfileRepository.findById(userId).orElse(null);
			if (profile != null) {
				if (avatarUrl == null || avatarUrl.isBlank()) {
					avatarUrl = profile.getAvatarUrl();
				}
				billiardRank = profile.getBilliardRank();
				bio = profile.getBio();
			}
		}

		// Lấy toàn bộ kết quả chính thức — nếu có userId thì lấy tất cả giải đã thi, ngược lại chỉ lấy giải này
		List<TournamentResult> results;
		if (userId != null) {
			results = resultRepository.findByParticipantRegistrationUserId(userId);
		} else {
			results = new ArrayList<>();
			resultRepository.findByTournamentIdAndParticipantId(
					participant.getTournament().getId(), participantId)
					.ifPresent(results::add);
		}

		List<PlayerPublicProfileResponse.TournamentAchievementEntry> achievements = results.stream()
				.map(tr -> PlayerPublicProfileResponse.TournamentAchievementEntry.builder()
						.tournamentId(tr.getTournament().getId())
						.tournamentName(tr.getTournament().getName())
						.rankLabel(RankingLabelFormat.single(tr.getFinalRank()))
						.note(tr.getNote())
						.finalRank(tr.getFinalRank())
						.prizeAmount(tr.getPrizeAmount())
						.pointsEarned(tr.getPointsEarned())
						.isOfficial(TournamentStatus.COMPLETED.getValue().equals(tr.getTournament().getStatus()))
						.build())
				.toList();

		return PlayerPublicProfileResponse.builder()
				.participantId(participantId)
				.displayName(participant.getDisplayName())
				.avatarUrl(avatarUrl)
				.seedNo(participant.getSeedNo())
				.billiardRank(billiardRank)
				.bio(bio)
				.achievements(achievements)
				.build();
	}

	/**
	 * Thêm một dòng xếp hạng nếu cơ thủ chưa được xếp (tránh trùng khi cùng người thua nhiều trận).
	 *
	 * @param rankFrom hạng thấp nhất trong nhóm (dùng sortOrder)
	 * @param rankTo   hạng cao nhất trong nhóm (tạo rankLabel #from-to)
	 * @param note     ghi chú tiếng Việt (null nếu chỉ hiển thị nhãn hạng)
	 */
	private static void addEntry(
			List<TournamentRankingEntryResponse> entries,
			Set<Long> placedParticipantIds,
			Participant participant,
			int rankFrom,
			int rankTo,
			String note) {
		if (participant == null || placedParticipantIds.contains(participant.getId())) {
			return;
		}
		placedParticipantIds.add(participant.getId());
		entries.add(TournamentRankingEntryResponse.builder()
				.sortOrder(rankFrom)
				.rankLabel(RankingLabelFormat.range(rankFrom, rankTo))
				.rankFrom(rankFrom)
				.rankTo(rankTo)
				.participantId(participant.getId())
				.displayName(participant.getDisplayName())
				.note(note)
				.build());
	}
}
