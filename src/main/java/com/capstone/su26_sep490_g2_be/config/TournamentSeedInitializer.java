package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.*;
import com.capstone.su26_sep490_g2_be.repository.*;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import com.capstone.su26_sep490_g2_be.service.TournamentResultService;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingResponse;
import com.capstone.su26_sep490_g2_be.util.TournamentPointsPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tạo 1 giải đấu hoàn chỉnh (đã thi đấu xong) cho mỗi thể thức:
 * <ol>
 *   <li>SINGLE_ELIMINATION — 8 cơ thủ, 9-Ball</li>
 *   <li>DOUBLE_ELIMINATION — 8 cơ thủ, 8-Ball</li>
 *   <li>PROGRESSIVE_ROUND_ROBIN — 8 cơ thủ, 9-Ball</li>
 * </ol>
 *
 * Mỗi giải: tạo tournament → participants → generate bracket → simulate matches → COMPLETED → results.
 * Idempotent — kiểm tra tên giải trước khi tạo.
 * Chạy sau DataInitializer (@Order 1).
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class TournamentSeedInitializer implements CommandLineRunner {

	private static final Random RND = new Random(2026L);

	private final UserRepository userRepository;
	private final TournamentRepository tournamentRepository;
	private final TournamentConfigRepository tournamentConfigRepository;
	private final TournamentRaceToRuleRepository raceToRuleRepository;
	private final TournamentConfigValueRepository configValueRepository;
	private final ConfigFieldDefinitionRepository configFieldRepository;
	private final ParticipantRepository participantRepository;
	private final TournamentStageRepository stageRepository;
	private final MatchRepository matchRepository;
	private final TournamentResultRepository resultRepository;
	private final BranchRepository branchRepository;
	private final BracketGenerationService bracketGenerationService;
	private final MatchService matchService;
	private final TournamentResultService tournamentResultService;

	@Override
	@Transactional
	public void run(String... args) {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) {
			log.warn("TournamentSeedInitializer: owner@gmail.com chưa tồn tại — bỏ qua.");
			return;
		}

		Branch branch = branchRepository.findByOwnerId(owner.getId()).stream().findFirst().orElse(null);

		seedSingleElimination(owner, branch);
		seedDoubleElimination(owner, branch);
		seedProgressiveRoundRobin(owner, branch);

		log.info("TournamentSeedInitializer hoàn thành — 4 giải đấu mẫu (1 per format)");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  1. SINGLE ELIMINATION — 8 cơ thủ, 9-Ball
	// ══════════════════════════════════════════════════════════════════════

	private void seedSingleElimination(User owner, Branch branch) {
		final String name = "Giải 9-Ball Loại Trực Tiếp 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải đấu 9-Ball loại trực tiếp — 8 cơ thủ, race-to-7. Thua 1 trận là bị loại.",
				"9_BALL", "SINGLE_ELIMINATION", 8,
				BigDecimal.valueOf(200000), BigDecimal.valueOf(4000000),
				"Vô địch 2.000.000đ · Á quân 1.200.000đ · Hạng 3-4 400.000đ",
				owner, branch);

		createConfig(t, SeedingMethod.RANDOM.name());

		addRaceToRule(t, "quarter_final", "KNOCKOUT", 5);
		addRaceToRule(t, "semi_final", "KNOCKOUT", 7);
		addRaceToRule(t, "third_place", "KNOCKOUT", 7);
		addRaceToRule(t, "final", "KNOCKOUT", 9);

		List<String> names = List.of(
				"Nguyễn Văn Hùng", "Trần Minh Tuấn", "Lê Hoàng Nam", "Phạm Đức Anh",
				"Hoàng Quốc Việt", "Vũ Thanh Bình", "Phan Trọng Khôi", "Trương Xuân Long");
		Map<Long, Integer> power = createParticipantsWithPower(t, names);

		generateAndComplete(t, owner, power, null);
		log.info("Seeded SINGLE_ELIMINATION: {} (8 cơ thủ, COMPLETED)", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  2. DOUBLE ELIMINATION — 8 cơ thủ, 8-Ball
	// ══════════════════════════════════════════════════════════════════════

	private void seedDoubleElimination(User owner, Branch branch) {
		final String name = "Giải 8-Ball Loại Kép 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải đấu 8-Ball loại kép — 8 cơ thủ. Nhánh thắng + nhánh thua → chung kết lớn.",
				"8_BALL", "DOUBLE_ELIMINATION", 8,
				BigDecimal.valueOf(150000), BigDecimal.valueOf(4000000),
				"Vô địch 2.000.000đ · Á quân 1.200.000đ · Hạng 3 800.000đ",
				owner, branch);

		createConfig(t, SeedingMethod.RANDOM.name());

		addRaceToRule(t, "winners_r1", "WINNERS", 5);
		addRaceToRule(t, "winners_r2", "WINNERS", 7);
		addRaceToRule(t, "winners_r3", "WINNERS", 7);
		addRaceToRule(t, "losers_r1", "LOSERS", 5);
		addRaceToRule(t, "losers_r2", "LOSERS", 5);
		addRaceToRule(t, "losers_r3", "LOSERS", 7);
		addRaceToRule(t, "losers_final", "LOSERS", 7);
		addRaceToRule(t, "grand_final", "GRAND_FINAL", 9);

		List<String> names = List.of(
				"Bùi Hữu Phúc", "Đặng Đình Khoa", "Đỗ Công Danh", "Hồ Quang Minh",
				"Ngô Thành Trung", "Dương Bảo Khánh", "Nguyễn Nhật Huy", "Trần Tiến Đạt");
		Map<Long, Integer> power = createParticipantsWithPower(t, names);

		generateAndComplete(t, owner, power, null);
		log.info("Seeded DOUBLE_ELIMINATION: {} (8 cơ thủ, COMPLETED)", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  4. PROGRESSIVE_ROUND_ROBIN — 8 cơ thủ, 9-Ball
	// ══════════════════════════════════════════════════════════════════════

	private void seedProgressiveRoundRobin(User owner, Branch branch) {
		final String name = "Giải 9-Ball Vòng Tròn Loại Dần 2026";
		if (tournamentExists(name)) return;

		Instant now = Instant.now();
		Tournament t = tournamentRepository.save(Tournament.builder()
				.name(name)
				.description("Giải 9-Ball vòng tròn loại dần — 8 cơ thủ, GĐ1(8→6) → GĐ2(6→4) → Playoff(BK+CK).")
				.gameType("9_BALL")
				.format("PROGRESSIVE_ROUND_ROBIN")
				.participantType(ParticipantType.SINGLE.getValue())
				.status(TournamentStatus.REGISTRATION_CLOSED.getValue())
				.maxParticipants(8)
				.tableCount(2)
				.entryFee(BigDecimal.ZERO)
				.prizePool(BigDecimal.valueOf(4000000))
				.prizeDescription("Vô địch 2.000.000đ · Á quân 1.200.000đ · Hạng 3-4 400.000đ")
				.registrationDeadline(now.minus(10, ChronoUnit.DAYS))
				.startAt(now.minus(7, ChronoUnit.DAYS))
				.endAt(now.minus(1, ChronoUnit.DAYS))
				.isShowTournament(true)
				.isPublicRatio(true)
				.isRegister(true)
				.createdBy(owner)
				.branch(branch)
				.venueName(branch != null ? branch.getName() : null)
				.venueAddress(branch != null ? branch.getAddress() : null)
				.build());

		tournamentConfigRepository.save(TournamentConfig.builder()
				.tournament(t).formatCode(t.getFormat()).seedingMethod(SeedingMethod.RANDOM.name()).build());

		addConfigValue(t, "pe_survivors_per_stage", "6,4");
		addConfigValue(t, "final_playoff_size", "4");
		addConfigValue(t, "break_rule", "ALTERNATE_BREAK");
		addConfigValue(t, "lag_for_break", "true");
		addConfigValue(t, "scoring_unit", "GAME");

		List<String> names = List.of(
				"Nguyễn Minh Cường", "Trần Bảo Long", "Lê Quang Huy", "Phạm Anh Tuấn",
				"Hoàng Đức Thịnh", "Vũ Trọng Nghĩa", "Đặng Văn Sơn", "Bùi Nhật Minh");
		Map<Long, Integer> power = createParticipantsWithPower(t, names);

		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId());
		t.setStatus(TournamentStatus.IN_PROGRESS.getValue());
		tournamentRepository.save(t);

		playAllPendingMatchesByStageType(t.getId(), "PROGRESSIVE_ROUND", power, owner.getId());
		bracketGenerationService.advanceProgressiveStage(t.getId());
		playAllPendingMatchesByStageType(t.getId(), "PROGRESSIVE_ROUND", power, owner.getId());
		bracketGenerationService.advanceProgressiveStage(t.getId());
		playAllPendingMatchesByStageType(t.getId(), "PROGRESSIVE_PLAYOFF", power, owner.getId());

		finishTournament(t);
		seedResults(t, owner);
		log.info("Seeded PROGRESSIVE_ROUND_ROBIN: {} (8 cơ thủ, COMPLETED)", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Shared helpers
	// ══════════════════════════════════════════════════════════════════════

	private Tournament createTournament(String name, String description,
			String gameType, String format, int maxParticipants,
			BigDecimal entryFee, BigDecimal prizePool, String prizeDescription,
			User owner, Branch branch) {
		Instant now = Instant.now();
		return tournamentRepository.save(Tournament.builder()
				.name(name)
				.description(description)
				.gameType(gameType)
				.format(format)
				.participantType(ParticipantType.SINGLE.getValue())
				.status(TournamentStatus.REGISTRATION_CLOSED.getValue())
				.maxParticipants(maxParticipants)
				.entryFee(entryFee)
				.prizePool(prizePool)
				.prizeDescription(prizeDescription)
				.registrationDeadline(now.minus(10, ChronoUnit.DAYS))
				.startAt(now.minus(7, ChronoUnit.DAYS))
				.endAt(now.minus(1, ChronoUnit.DAYS))
				.isShowTournament(true)
				.isPublicRatio(true)
				.isRegister(true)
				.createdBy(owner)
				.branch(branch)
				.venueName(branch != null ? branch.getName() : null)
				.venueAddress(branch != null ? branch.getAddress() : null)
				.build());
	}

	private void createConfig(Tournament t, String seedingMethod) {
		if (tournamentConfigRepository.existsById(t.getId())) return;
		tournamentConfigRepository.save(TournamentConfig.builder()
				.tournament(t)
				.formatCode(t.getFormat())
				.seedingMethod(seedingMethod)
				.build());
	}

	private void addRaceToRule(Tournament t, String roundKey, String bracketPhase, int raceTo) {
		if (raceToRuleRepository.findByTournamentIdAndRoundKey(t.getId(), roundKey).isPresent()) return;
		raceToRuleRepository.save(TournamentRaceToRule.builder()
				.tournament(t)
				.roundKey(roundKey)
				.bracketPhase(bracketPhase)
				.raceTo(raceTo)
				.build());
	}

	private void addConfigValue(Tournament t, String key, String value) {
		TournamentConfigValueId id = new TournamentConfigValueId(t.getId(), key);
		if (configValueRepository.existsById(id)) return;
		ConfigFieldDefinition fieldDef = configFieldRepository.findById(key).orElse(null);
		if (fieldDef == null) {
			log.warn("ConfigFieldDefinition '{}' chưa tồn tại, bỏ qua.", key);
			return;
		}
		configValueRepository.save(TournamentConfigValue.builder()
				.id(id).tournament(t).fieldDefinition(fieldDef).value(value).build());
	}

	private Map<Long, Integer> createParticipantsWithPower(Tournament t, List<String> displayNames) {
		long existing = participantRepository.countByTournamentIdAndStatus(
				t.getId(), ParticipantStatus.ACTIVE.getValue());
		if (existing >= displayNames.size()) return Map.of();

		Map<Long, Integer> power = new HashMap<>();
		for (int i = 0; i < displayNames.size(); i++) {
			Participant p = participantRepository.save(Participant.builder()
					.tournament(t)
					.registration(null)
					.participantType(ParticipantType.SINGLE.getValue())
					.displayName(displayNames.get(i))
					.status(ParticipantStatus.ACTIVE.getValue())
					.build());
			power.put(p.getId(), i);
		}
		return power;
	}

	/**
	 * Generate bracket → simulate all matches → finish.
	 * Works for SINGLE_ELIMINATION, DOUBLE_ELIMINATION.
	 */
	private void generateAndComplete(Tournament t, User owner, Map<Long, Integer> power,
			String stageTypeFilter) {
		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId());

		t.setStatus(TournamentStatus.IN_PROGRESS.getValue());
		tournamentRepository.save(t);

		simulateAllMatches(t, owner, power);
		finishTournament(t);
		seedResults(t, owner);
	}

	private void simulateAllMatches(Tournament t, User owner, Map<Long, Integer> power) {
		int completed = 0;
		boolean progress;
		int guard = 0;

		do {
			progress = false;
			guard++;
			if (guard > 200) break;

			List<Match> matches = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(t.getId());
			matches.sort(Comparator.comparing(Match::getRoundNo).thenComparing(Match::getPositionNo));

			for (Match match : matches) {
				if (isFinished(match) || match.getPlayer1() == null || match.getPlayer2() == null) continue;

				Long p1Id = match.getPlayer1().getId();
				Long p2Id = match.getPlayer2().getId();
				int p1Power = power.getOrDefault(p1Id, 99);
				int p2Power = power.getOrDefault(p2Id, 99);
				boolean p1Wins = p1Power < p2Power;
				if (p1Power == p2Power) p1Wins = RND.nextBoolean();

				int raceTo = match.getRaceTo() != null && match.getRaceTo() > 0 ? match.getRaceTo() : 7;
				int loserScore = RND.nextInt(Math.max(1, raceTo));

				if (p1Wins) {
					match.setPlayer1Score(raceTo);
					match.setPlayer2Score(loserScore);
				} else {
					match.setPlayer1Score(loserScore);
					match.setPlayer2Score(raceTo);
				}
				matchRepository.save(match);

				Long winnerId = p1Wins ? p1Id : p2Id;
				matchService.completeMatch(match.getId(), winnerId, false, owner.getId());
				completed++;
				progress = true;
			}
		} while (progress);

		log.debug("Simulated {} matches for '{}'", completed, t.getName());
	}

	private void playAllPendingMatchesByStageType(Long tournamentId, String stageType,
			Map<Long, Integer> power, Long actorUserId) {
		List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
		for (TournamentStage stage : stages) {
			if (!stageType.equals(stage.getStageType())) continue;
			List<Match> matches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stage.getId());
			for (Match m : matches) {
				if (!MatchStatus.PENDING.getValue().equals(m.getStatus())) continue;
				if (m.getPlayer1() == null || m.getPlayer2() == null) continue;

				Long p1Id = m.getPlayer1().getId();
				Long p2Id = m.getPlayer2().getId();
				boolean p1Wins = power.getOrDefault(p1Id, 99) < power.getOrDefault(p2Id, 99);
				int raceTo = m.getRaceTo() != null ? m.getRaceTo() : 5;
				int loserScore = raceTo <= 1 ? 0 : RND.nextInt(raceTo);

				matchService.updateScore(m.getId(), p1Wins ? raceTo : loserScore,
						p1Wins ? loserScore : raceTo, actorUserId);
				matchService.completeMatch(m.getId(), p1Wins ? p1Id : p2Id, false, actorUserId);
			}
		}
	}

	private void finishTournament(Tournament t) {
		t.setStatus(TournamentStatus.COMPLETED.getValue());
		tournamentRepository.save(t);

		stageRepository.findByTournamentIdOrderByOrderNoAsc(t.getId())
				.forEach(stage -> {
					stage.setStatus(TournamentStageStatus.COMPLETED.getValue());
					stageRepository.save(stage);
				});
	}

	private void seedResults(Tournament t, User recorder) {
		if (!resultRepository.findByTournamentIdOrderByFinalRankAsc(t.getId()).isEmpty()) return;

		TournamentRankingResponse ranking;
		try {
			ranking = tournamentResultService.getRankings(t.getId());
		} catch (Exception e) {
			log.warn("Không tính được xếp hạng cho '{}': {}", t.getName(), e.getMessage());
			return;
		}

		List<TournamentRankingEntryResponse> entries = ranking.getEntries();
		if (entries == null || entries.isEmpty()) return;

		Map<Long, Participant> participantMap = participantRepository
				.findByTournamentId(t.getId()).stream()
				.collect(Collectors.toMap(Participant::getId, p -> p, (a, b) -> a));

		BigDecimal prizePool = t.getPrizePool() != null ? t.getPrizePool() : BigDecimal.ZERO;
		Instant recordedAt = Instant.now();
		int currentRank = 0;

		for (TournamentRankingEntryResponse entry : entries) {
			currentRank = Math.max(entry.getRankFrom(), currentRank + 1);
			Long participantId = entry.getParticipantId();
			if (participantId == null) continue;

			Participant participant = participantMap.get(participantId);
			if (participant == null) continue;
			if (resultRepository.existsByTournamentIdAndParticipantId(t.getId(), participantId)) continue;

			BigDecimal prize = computePrize(prizePool, entry.getRankFrom(), entry.getRankTo());
			int points = TournamentPointsPolicy.compute(currentRank, participantMap.size());

			resultRepository.save(TournamentResult.builder()
					.tournament(t)
					.participant(participant)
					.finalRank(currentRank)
					.prizeAmount(prize)
					.pointsEarned(points)
					.note(entry.getNote())
					.recordedAt(recordedAt)
					.recordedBy(recorder)
					.build());
		}
	}

	private BigDecimal computePrize(BigDecimal pool, int rankFrom, int rankTo) {
		if (pool.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
		int groupSize = Math.max(1, rankTo - rankFrom + 1);
		double groupPct = switch (rankFrom) {
			case 1 -> 0.40;
			case 2 -> 0.20;
			case 3 -> 0.15;
			case 5 -> 0.10;
			case 9 -> 0.08;
			case 17 -> 0.05;
			case 33 -> 0.02;
			default -> 0.0;
		};
		if (groupPct == 0) return BigDecimal.ZERO;
		return pool.multiply(BigDecimal.valueOf(groupPct / groupSize))
				.setScale(0, RoundingMode.HALF_UP);
	}


	private boolean tournamentExists(String name) {
		return tournamentRepository.findAll().stream()
				.anyMatch(t -> name.equals(t.getName()));
	}

	private static boolean isFinished(Match match) {
		String status = match.getStatus();
		return MatchStatus.COMPLETED.getValue().equals(status)
				|| MatchStatus.BYE.getValue().equals(status)
				|| MatchStatus.WALKOVER.getValue().equals(status);
	}
}
