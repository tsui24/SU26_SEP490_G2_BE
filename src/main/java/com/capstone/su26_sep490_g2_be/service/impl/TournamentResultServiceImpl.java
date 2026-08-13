package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.PlayerPublicProfileResponse;
import com.capstone.su26_sep490_g2_be.dto.response.StandingsEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingResponse;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import com.capstone.su26_sep490_g2_be.entity.TournamentStage;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.RankingLabelFormat;
import com.capstone.su26_sep490_g2_be.enums.RankingPlacementNote;
import com.capstone.su26_sep490_g2_be.enums.TournamentFormat;
import com.capstone.su26_sep490_g2_be.enums.TournamentStageType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentStageRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentResultRepository;
import com.capstone.su26_sep490_g2_be.repository.UserProfileRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.service.TournamentResultService;
import com.capstone.su26_sep490_g2_be.util.AvatarUrlResolver;
import com.capstone.su26_sep490_g2_be.util.TournamentPointsPolicy;
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
	private final TournamentStageRepository stageRepository;
	private final MinioStorageService minioStorageService;
	private final MinioProperties minioProperties;

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
		if (results.isEmpty()) {
			// Trước đây bảng tournament_results chỉ có dữ liệu qua seed demo — không có luồng thật
			// nào gọi record()/finalizeTournamentResults() khi giải đấu thực sự hoàn tất. Tự dựng
			// kết quả từ bảng xếp hạng hiện tại (getRankings) ngay khi BQT chốt COMPLETED.
			results = buildResultsFromRankings(tournamentId);
		}
		Instant now = Instant.now();
		results.forEach(r -> {
			r.setRecordedBy(recordedBy);
			r.setRecordedAt(now);
		});
		resultRepository.saveAll(results);
	}

	/**
	 * Dựng TournamentResult từ getRankings().
	 *
	 * <p><b>finalRank phải duy nhất trong 1 giải</b> — {@link TournamentResult} có unique
	 * {@code (tournament_id, final_rank)}. getRankings() trả nhóm đồng hạng dùng chung một
	 * {@code rankFrom} (2 người thua bán kết cùng #3, nhóm "#5-8" có 4 người cùng #5), nên phải
	 * rải thành hạng liên tiếp trước khi lưu, nếu không sẽ vi phạm ràng buộc khi chốt giải.
	 *
	 * <p>prizeAmount vẫn để 0 — hệ thống chưa có công thức chia thưởng theo hạng, BTC/Admin nhập
	 * tay qua {@link #record(TournamentResult)}. Riêng pointsEarned tính theo
	 * {@link TournamentPointsPolicy} để phục vụ bảng xếp hạng điểm tích lũy.
	 */
	private List<TournamentResult> buildResultsFromRankings(Long tournamentId) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		TournamentRankingResponse ranking = getRankings(tournamentId);
		int participantCount = (int) participantRepository.countByTournamentIdAndStatus(
				tournamentId, ParticipantStatus.ACTIVE.getValue());

		List<TournamentResult> built = new ArrayList<>();
		int currentRank = 0;
		for (TournamentRankingEntryResponse entry : ranking.getEntries()) {
			Integer groupRank = entry.getRankFrom() != null ? entry.getRankFrom() : entry.getSortOrder();
			if (groupRank == null) continue;
			// Rải hạng: giữ hạng gốc của nhóm, nhưng luôn tiến ít nhất 1 so với dòng trước
			currentRank = Math.max(groupRank, currentRank + 1);

			if (entry.getParticipantId() == null) continue;
			Participant participant = participantRepository.findById(entry.getParticipantId()).orElse(null);
			if (participant == null) continue;

			built.add(TournamentResult.builder()
					.tournament(tournament)
					.participant(participant)
					.finalRank(currentRank)
					.prizeAmount(java.math.BigDecimal.ZERO)
					.pointsEarned(TournamentPointsPolicy.compute(currentRank, participantCount))
					.note(entry.getNote())
					.build());
		}
		return built;
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
	 *       PROGRESSIVE_ROUND_ROBIN xếp theo giai đoạn bị loại; các format còn lại dùng
	 *       placement loại trực tiếp.</li>
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
		if (TournamentFormat.PROGRESSIVE_ROUND_ROBIN.getValue().equals(formatCode)) {
			// Vòng tròn loại dần: top playoff theo kết quả knockout; còn lại theo GĐ bị loại
			return fromProgressiveRankings(tournament);
		}
		if (TournamentFormat.DOUBLE_ELIMINATION.getValue().equals(formatCode)) {
			// Loại kép (CUT_TO_SE): 3 stage riêng (WINNERS/LOSERS/FINAL_BRACKET) có roundNo
			// đánh số độc lập — không thể gộp chung như single elimination.
			return computeDoubleEliminationRankings(tournament.getId());
		}
		// Single elimination và knockout playoff: hạng = vòng bị loại
		return computeSingleEliminationRankings(tournament.getId());
	}

	/**
	 * Xếp hạng cho giải DOUBLE_ELIMINATION (luôn ở dạng CUT_TO_SE).
	 *
	 * <p>Tournament có 3 stage tách biệt (WINNERS, LOSERS, FINAL_BRACKET), mỗi stage tự đánh số
	 * {@code roundNo} riêng từ 1 — không thể gộp chung theo roundNo thô như single elimination
	 * (sẽ lẫn lộn người thua vòng 2 nhánh Thắng/Thua với người thua bán kết Last X thật sự).
	 *
	 * <ul>
	 *   <li>Stage FINAL_BRACKET (Last X) là trận đấu loại trực tiếp thật sự cuối cùng của giải →
	 *       xếp hạng 1..seSize bằng đúng thuật toán knockout (VĐ, Á quân, bán kết, tứ kết...).</li>
	 *   <li>Ai bị loại trước khi vào Last X (thua trận thứ 2, tức thua ở nhánh Thua) được xếp ngay
	 *       sau nhóm Last X — thua ở vòng nhánh Thua càng muộn thì hạng càng cao. Nhánh Thắng chỉ
	 *       làm rớt xuống nhánh Thua (chưa loại), nên không dùng vòng nhánh Thắng để xếp hạng.</li>
	 * </ul>
	 */
	private List<TournamentRankingEntryResponse> computeDoubleEliminationRankings(Long tournamentId) {
		List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
		TournamentStage seStage = stages.stream()
				.filter(s -> "FINAL_BRACKET".equals(s.getStageType()))
				.findFirst().orElse(null);
		if (seStage == null) {
			// Chưa bốc thăm / chưa sinh bracket CUT_TO_SE — chưa có gì để xếp hạng
			return List.of();
		}
		TournamentStage lStage = stages.stream()
				.filter(s -> "LOSERS".equals(s.getStageType()))
				.findFirst().orElse(null);

		List<Match> seMatches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(seStage.getId());
		List<TournamentRankingEntryResponse> entries = new ArrayList<>(computeKnockoutPlacements(seMatches));
		Set<Long> placedParticipantIds = new HashSet<>();
		entries.forEach(e -> placedParticipantIds.add(e.getParticipantId()));

		if (lStage != null) {
			List<Match> lMatches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(lStage.getId());
			int lMaxRound = lMatches.stream().mapToInt(Match::getRoundNo).max().orElse(0);
			int nextRank = entries.size() + 1;
			for (int round = lMaxRound; round >= 1; round--) {
				int roundNo = round;
				List<Participant> roundLosers = lMatches.stream()
						.filter(m -> m.getRoundNo() == roundNo && isFinished(m) && m.getLoser() != null)
						.map(Match::getLoser)
						.filter(p -> !placedParticipantIds.contains(p.getId()))
						.toList();
				if (roundLosers.isEmpty()) {
					continue;
				}
				int rankFrom = nextRank;
				int rankTo = nextRank + roundLosers.size() - 1;
				for (Participant p : roundLosers) {
					addEntry(entries, placedParticipantIds, p, rankFrom, rankTo, null);
				}
				nextRank = rankTo + 1;
			}
		}

		entries.sort(Comparator
				.comparingInt(TournamentRankingEntryResponse::getSortOrder)
				.thenComparing(TournamentRankingEntryResponse::getDisplayName,
						Comparator.nullsLast(String::compareToIgnoreCase)));
		return entries;
	}

	/**
	 * Xếp hạng cho giải PROGRESSIVE_ROUND_ROBIN.
	 *
	 * <p>Top {@code final_playoff_size} người xếp theo kết quả vòng Playoff (VĐ, Á quân, hạng 3-4...).
	 * Số còn lại xếp theo giai đoạn League bị loại — bị loại càng MUỘN hạng càng cao; trong cùng
	 * giai đoạn theo bảng xếp hạng của giai đoạn đó (đã tính head-to-head).
	 */
	private List<TournamentRankingEntryResponse> fromProgressiveRankings(Tournament tournament) {
		Long tid = tournament.getId();
		List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tid);

		TournamentStage playoff = stages.stream()
				.filter(s -> TournamentStageType.PROGRESSIVE_PLAYOFF.getValue().equals(s.getStageType()))
				.findFirst().orElse(null);
		List<TournamentStage> leagueStages = stages.stream()
				.filter(s -> TournamentStageType.PROGRESSIVE_ROUND.getValue().equals(s.getStageType()))
				.sorted(Comparator.comparingInt(TournamentStage::getOrderNo))
				.toList();

		List<TournamentRankingEntryResponse> entries = new ArrayList<>();
		Set<Long> placed = new HashSet<>();

		// 1) Placement từ vòng Playoff (nếu đã sinh)
		if (playoff != null) {
			addProgressivePlayoffPlacements(playoff.getId(), entries, placed);
		}

		// 2) Các giai đoạn League — muộn nhất trước (hạng cao hơn), theo standings từng GĐ
		int nextRank = entries.size() + 1;
		for (int si = leagueStages.size() - 1; si >= 0; si--) {
			List<StandingsEntryResponse> standings =
					bracketGenerationService.computeStageStandings(leagueStages.get(si).getId());
			for (StandingsEntryResponse s : standings) {
				if (placed.contains(s.getParticipantId())) {
					continue;
				}
				entries.add(TournamentRankingEntryResponse.builder()
						.sortOrder(nextRank)
						.rankLabel(RankingLabelFormat.single(nextRank))
						.rankFrom(nextRank)
						.rankTo(nextRank)
						.participantId(s.getParticipantId())
						.displayName(s.getDisplayName())
						.build());
				placed.add(s.getParticipantId());
				nextRank++;
			}
		}

		entries.sort(Comparator
				.comparingInt(TournamentRankingEntryResponse::getSortOrder)
				.thenComparing(TournamentRankingEntryResponse::getDisplayName,
						Comparator.nullsLast(String::compareToIgnoreCase)));
		return entries;
	}

	/** Placement cho bracket Playoff single-elimination (không có trận tranh hạng 3). */
	private void addProgressivePlayoffPlacements(Long stageId,
			List<TournamentRankingEntryResponse> entries, Set<Long> placed) {
		List<Match> matches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stageId);
		if (matches.isEmpty()) {
			return;
		}
		int maxRound = matches.stream().mapToInt(Match::getRoundNo).max().orElse(0);
		if (maxRound == 0) {
			return;
		}

		Match finalMatch = matches.stream()
				.filter(m -> m.getRoundNo() == maxRound && m.getPositionNo() == 1)
				.findFirst().orElse(null);
		if (finalMatch != null && isFinished(finalMatch)) {
			addEntry(entries, placed, finalMatch.getWinner(), 1, 1, RankingPlacementNote.CHAMPION.getValue());
			addEntry(entries, placed, finalMatch.getLoser(), 2, 2, RankingPlacementNote.RUNNER_UP.getValue());
		}
		if (maxRound >= 2) {
			int sfRound = maxRound - 1;
			for (Match m : matches) {
				if (m.getRoundNo() == sfRound && isFinished(m) && m.getLoser() != null) {
					addEntry(entries, placed, m.getLoser(), 3, 4, RankingPlacementNote.SEMI_FINAL.getValue());
				}
			}
		}
		for (int er = maxRound - 2; er >= 1; er--) {
			int[] range = placementRangeForEliminationRound(er, maxRound);
			for (Match m : matches) {
				if (m.getRoundNo() == er && isFinished(m) && m.getLoser() != null) {
					addEntry(entries, placed, m.getLoser(), range[0], range[1], null);
				}
			}
		}
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
	 */
	private List<TournamentRankingEntryResponse> computeSingleEliminationRankings(Long tournamentId) {
		List<Match> allMatches = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournamentId);
		return computeKnockoutPlacements(allMatches);
	}

	/**
	 * Xếp hạng loại trực tiếp cho MỘT bracket knockout độc lập (danh sách trận đã được lọc đúng
	 * stage — roundNo trong danh sách này phải liền mạch 1..maxRound của riêng bracket đó).
	 * Dùng chung cho single elimination toàn giải, và cho từng sub-bracket knockout (vd stage
	 * FINAL_BRACKET của DOUBLE_ELIMINATION) — không được truyền match trộn từ nhiều stage khác
	 * nhau vì mỗi stage tự đánh số roundNo riêng.
	 */
	private List<TournamentRankingEntryResponse> computeKnockoutPlacements(List<Match> matches) {
		// Giữ trận 3RD nếu có roundNo hợp lệ
		List<Match> knockoutMatches = matches.stream()
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

		String avatarUrl = participant.getAvtarUrl();
		String billiardRank = null;
		String bio = null;
		Long userId = null;
		String accountName = null;

		if (participant.getRegistration() != null && participant.getRegistration().getUser() != null) {
			userId = participant.getRegistration().getUser().getId();
			UserProfile profile = userProfileRepository.findById(userId).orElse(null);
			if (profile != null) {
				if (avatarUrl == null || avatarUrl.isBlank()) {
					avatarUrl = profile.getAvatarUrl();
				}
				billiardRank = profile.getBilliardRank();
				bio = profile.getBio();
				accountName = profile.getFullName();
			}
		}

		List<TournamentResult> results;
		if (userId != null) {
			results = resultRepository.findByParticipantRegistrationUserId(userId);
		} else {
			results = new ArrayList<>();
			resultRepository.findByTournamentIdAndParticipantId(
					participant.getTournament().getId(), participantId)
					.ifPresent(results::add);
		}

		List<PlayerPublicProfileResponse.TournamentAchievementEntry> achievements = buildAchievements(results);

		return PlayerPublicProfileResponse.builder()
				.participantId(participantId)
				.userId(userId)
				.displayName(participant.getDisplayName())
				.accountName(accountName)
				.avatarUrl(AvatarUrlResolver.resolveForResponse(
						avatarUrl, minioStorageService, minioProperties.getBucket()))
				.billiardRank(billiardRank)
				.bio(bio)
				.achievements(achievements)
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public PlayerPublicProfileResponse getPlayerProfileByUserId(Long userId) {
		UserProfile profile = userProfileRepository.findById(userId).orElse(null);

		String accountName = null;
		String avatarUrl = null;
		String billiardRank = null;
		String bio = null;

		if (profile != null) {
			accountName = profile.getFullName();
			avatarUrl = profile.getAvatarUrl();
			billiardRank = profile.getBilliardRank();
			bio = profile.getBio();
		}

		List<TournamentResult> results = resultRepository.findByParticipantRegistrationUserId(userId);
		List<PlayerPublicProfileResponse.TournamentAchievementEntry> achievements = buildAchievements(results);

		// Lấy displayName từ participant gần nhất
		List<Participant> participants = participantRepository.findByRegistrationUserId(userId);
		String displayName = accountName;
		Long latestParticipantId = null;
		if (!participants.isEmpty()) {
			Participant latest = participants.get(participants.size() - 1);
			latestParticipantId = latest.getId();
			if (displayName == null || displayName.isBlank()) {
				displayName = latest.getDisplayName();
			}
		}

		return PlayerPublicProfileResponse.builder()
				.participantId(latestParticipantId)
				.userId(userId)
				.displayName(displayName)
				.accountName(accountName)
				.avatarUrl(AvatarUrlResolver.resolveForResponse(
						avatarUrl, minioStorageService, minioProperties.getBucket()))
				.billiardRank(billiardRank)
				.bio(bio)
				.achievements(achievements)
				.build();
	}

	private List<PlayerPublicProfileResponse.TournamentAchievementEntry> buildAchievements(
			List<TournamentResult> results) {
		return results.stream()
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
