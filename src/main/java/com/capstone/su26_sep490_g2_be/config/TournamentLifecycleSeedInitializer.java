package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.config.bootstrap.SeedImages;
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
 * Bổ sung phần còn thiếu sau {@link TournamentSeedInitializer} (RANDOM, đủ 3 thể thức, COMPLETED)
 * và {@link RegistrationSeedInitializer} (SINGLE_ELIMINATION, đủ vòng đời trừ 2 trạng thái):
 * <ol>
 *   <li>3 giải mời xếp hạt giống ({@code SeedingMethod.SEED}) — 1 giải / thể thức, để demo bốc thăm
 *   theo hạt giống khác hẳn bốc thăm ngẫu nhiên (thứ hạng cao gặp thứ hạng thấp ở vòng đầu).</li>
 *   <li>{@code DRAW_PREVIEW} — đã bốc thăm nháp, BTC chưa xác nhận (chỉ gọi {@code generate()}, chưa
 *   {@code confirmDraw()}).</li>
 *   <li>{@code FINAL_BRACKET_READY} — giải loại kép đã đấu xong nhánh thắng/nhánh thua, bracket loại
 *   trực tiếp chung kết (cut-to-SE) đã điền người nhưng chưa đấu trận nào.</li>
 * </ol>
 * Cùng với 2 file kia, bộ 3 seeder phủ đủ 9 giá trị {@link TournamentStatus} và cả 2 phương án bốc
 * thăm ({@code RANDOM}/{@code SEED}) cho cả 3 thể thức giải đấu.
 * <p>
 * Idempotent theo tên giải đấu — an toàn khi backend restart nhiều lần trên cùng 1 DB.
 * Chạy sau {@link NewsSeedInitializer} (@Order 5).
 */
@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class TournamentLifecycleSeedInitializer implements CommandLineRunner {

	private static final Random RND = new Random(2026L);

	private final UserRepository userRepository;
	private final TournamentRepository tournamentRepository;
	private final TournamentConfigRepository tournamentConfigRepository;
	private final TournamentRaceToRuleRepository raceToRuleRepository;
	private final TournamentConfigValueRepository configValueRepository;
	private final ConfigFieldDefinitionRepository configFieldRepository;
	private final ParticipantRepository participantRepository;
	private final RegistrationRepository registrationRepository;
	private final PaymentRepository paymentRepository;
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
			log.warn("TournamentLifecycleSeedInitializer: owner@gmail.com chưa tồn tại — bỏ qua.");
			return;
		}
		Branch branch = branchRepository.findByOwnerId(owner.getId()).stream().findFirst().orElse(null);

		seedSingleEliminationSeeded(owner, branch);
		seedDoubleEliminationSeeded(owner, branch);
		seedProgressiveRoundRobinSeeded(owner, branch);
		seedDrawPreview(owner, branch);
		seedFinalBracketReady(owner, branch);

		log.info("TournamentLifecycleSeedInitializer hoàn thành — 3 giải xếp hạt giống (SEED) + "
				+ "2 giải bù trạng thái DRAW_PREVIEW/FINAL_BRACKET_READY");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  1. SINGLE_ELIMINATION — xếp hạt giống, 8 cơ thủ, 10-Ball, COMPLETED
	// ══════════════════════════════════════════════════════════════════════

	private void seedSingleEliminationSeeded(User owner, Branch branch) {
		final String name = "Giải Mời Cơ Thủ Hạng A – Xuân 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải mời dành riêng cho các cơ thủ hạng A trở lên — xếp hạt giống theo thành tích "
						+ "mùa trước, hạt giống số 1 sẽ gặp hạt giống yếu nhất ở vòng đầu.",
				"10_BALL", "SINGLE_ELIMINATION", 8,
				BigDecimal.valueOf(250000), BigDecimal.valueOf(5000000),
				"Vô địch 2.500.000đ · Á quân 1.500.000đ · Hạng 3-4 500.000đ",
				owner, branch);

		createConfig(t, SeedingMethod.SEED.name());
		addSingleEliminationRaceToRules(t);

		List<String> emails = List.of(
				"player28@gmail.com", "player1@gmail.com", "player9@gmail.com", "player19@gmail.com",
				"player24@gmail.com", "player2@gmail.com", "player17@gmail.com", "player3@gmail.com");
		Map<Long, Integer> power = createSeededParticipants(t, emails, true);

		generateConfirmAndPlaySE(t, owner, power);
		finishTournament(t);
		seedResults(t, owner);
		log.info("Seeded SINGLE_ELIMINATION (SEED): {} (8 cơ thủ, COMPLETED)", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  2. DOUBLE_ELIMINATION — xếp hạt giống, 8 cơ thủ, 8-Ball, COMPLETED
	// ══════════════════════════════════════════════════════════════════════

	private void seedDoubleEliminationSeeded(User owner, Branch branch) {
		final String name = "Cúp Mời Song Đấu Các Câu Lạc Bộ 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải mời giao lưu giữa các câu lạc bộ — xếp hạt giống theo hạng bi-a đã khai trên "
						+ "hồ sơ. Thể thức loại kép: thua nhánh thắng vẫn còn cơ hội ở nhánh thua.",
				"8_BALL", "DOUBLE_ELIMINATION", 8,
				BigDecimal.valueOf(180000), BigDecimal.valueOf(4500000),
				"Vô địch 2.200.000đ · Á quân 1.300.000đ · Hạng 3 700.000đ",
				owner, branch);

		createConfig(t, SeedingMethod.SEED.name());
		addDoubleEliminationRaceToRules(t, 4);

		List<String> emails = List.of(
				"player11@gmail.com", "player4@gmail.com", "player12@gmail.com", "player20@gmail.com",
				"player25@gmail.com", "player5@gmail.com", "player18@gmail.com", "player6@gmail.com");
		Map<Long, Integer> power = createSeededParticipants(t, emails, true);

		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId()); // → DRAW_DONE

		// DE cho phép hoàn thành trận ngay ở DRAW_DONE (nhánh thắng/nhánh thua) — không cần IN_PROGRESS.
		simulateAllMatches(t, owner, power);
		bracketGenerationService.populateFinalBracket(t.getId(), owner.getId()); // → FINAL_BRACKET_READY
		simulateAllMatches(t, owner, power); // đấu nốt bracket loại trực tiếp chung kết (Last-4)

		finishTournament(t);
		seedResults(t, owner);
		log.info("Seeded DOUBLE_ELIMINATION (SEED): {} (8 cơ thủ, COMPLETED)", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  3. PROGRESSIVE_ROUND_ROBIN — xếp hạt giống, 8 cơ thủ, 9-Ball, COMPLETED
	// ══════════════════════════════════════════════════════════════════════

	private void seedProgressiveRoundRobinSeeded(User owner, Branch branch) {
		final String name = "Giải Vô Địch Vòng Tròn Cây Cơ Bạc 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải phong trào thể thức vòng tròn loại dần — GĐ1 (8→6) rồi GĐ2 (6→4) trước khi vào "
						+ "Playoff bán kết + chung kết. Xếp hạt giống để tránh 2 cơ thủ mạnh nhất gặp nhau sớm.",
				"9_BALL", "PROGRESSIVE_ROUND_ROBIN", 8,
				BigDecimal.ZERO, BigDecimal.valueOf(3000000),
				"Vô địch 1.800.000đ · Á quân 1.000.000đ · Hạng 3-4 200.000đ",
				owner, branch);

		createConfig(t, SeedingMethod.SEED.name());
		addConfigValue(t, "pe_survivors_per_stage", "6,4");
		addConfigValue(t, "final_playoff_size", "4");
		addConfigValue(t, "break_rule", "ALTERNATE_BREAK");
		addConfigValue(t, "lag_for_break", "true");
		addConfigValue(t, "scoring_unit", "GAME");

		List<String> emails = List.of(
				"player13@gmail.com", "player21@gmail.com", "player7@gmail.com", "player22@gmail.com",
				"player14@gmail.com", "player8@gmail.com", "player23@gmail.com", "player15@gmail.com");
		Map<Long, Integer> power = createSeededParticipants(t, emails, true);

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
		log.info("Seeded PROGRESSIVE_ROUND_ROBIN (SEED): {} (8 cơ thủ, COMPLETED)", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  4. DRAW_PREVIEW — đã bốc thăm nháp, BTC chưa xác nhận
	// ══════════════════════════════════════════════════════════════════════

	private void seedDrawPreview(User owner, Branch branch) {
		final String name = "Giải Bi-a Chào Xuân Golden Break 2026";
		if (tournamentExists(name)) return;

		Instant now = Instant.now();
		Tournament t = tournamentRepository.save(Tournament.builder()
				.name(name)
				.description("Giải chào xuân miễn phí dành cho hội viên — vừa đóng đăng ký và bốc thăm "
						+ "nháp xong, ban tổ chức đang chờ xác nhận bảng đấu trước khi công bố chính thức.")
				.thumbnailUrl(SeedImages.thumbnailFor(name))
				.bannerUrl(SeedImages.bannerFor(name))
				.gameType("9_BALL")
				.format("SINGLE_ELIMINATION")
				.participantType(ParticipantType.SINGLE.getValue())
				.status(TournamentStatus.OPEN_FOR_REGISTRATION.getValue())
				.maxParticipants(8)
				.entryFee(BigDecimal.ZERO)
				.prizePool(BigDecimal.valueOf(2000000))
				.prizeDescription("Vô địch 1.200.000đ · Á quân 800.000đ")
				.registrationDeadline(now.minus(2, ChronoUnit.DAYS))
				.startAt(now.plus(3, ChronoUnit.DAYS))
				.endAt(now.plus(4, ChronoUnit.DAYS))
				.isShowTournament(true)
				.isPublicRatio(true)
				.isRegister(true)
				.createdBy(owner)
				.branch(branch)
				.venueName(branch != null ? branch.getName() : null)
				.venueAddress(branch != null ? branch.getAddress() : null)
				.build());

		createConfig(t, SeedingMethod.RANDOM.name());
		addSingleEliminationRaceToRules(t);

		List<String> emails = List.of(
				"player16@gmail.com", "player26@gmail.com", "player27@gmail.com", "player29@gmail.com",
				"player30@gmail.com", "player10@gmail.com", "player6@gmail.com", "player7@gmail.com");
		createSeededParticipants(t, emails, false);

		t.setStatus(TournamentStatus.REGISTRATION_CLOSED.getValue());
		tournamentRepository.save(t);

		bracketGenerationService.generate(t.getId(), owner.getId()); // → DRAW_PREVIEW, dừng ở đây
		log.info("Seeded DRAW_PREVIEW: {} (8 cơ thủ, chờ BTC xác nhận bốc thăm)", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  5. FINAL_BRACKET_READY — loại kép, đã đấu xong 2 nhánh, chờ đấu chung kết
	// ══════════════════════════════════════════════════════════════════════

	private void seedFinalBracketReady(User owner, Branch branch) {
		final String name = "Cúp Tân Niên Song Đấu 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Cúp mừng năm mới thể thức loại kép — nhánh thắng và nhánh thua đã thi đấu xong, "
						+ "bracket loại trực tiếp chung kết vừa được điền người, chờ ngày thi đấu quyết định.",
				"8_BALL", "DOUBLE_ELIMINATION", 8,
				BigDecimal.valueOf(120000), BigDecimal.valueOf(3000000),
				"Vô địch 1.500.000đ · Á quân 900.000đ · Hạng 3 600.000đ",
				owner, branch);
		// Giải đang thi đấu dở — ghi đè lại mốc thời gian cho khớp (createTournament() mặc định set
		// khoảng thời gian đã kết thúc trong quá khứ).
		t.setStartAt(Instant.now().minus(3, ChronoUnit.DAYS));
		t.setEndAt(Instant.now().plus(2, ChronoUnit.DAYS));
		t.setRegistrationDeadline(Instant.now().minus(10, ChronoUnit.DAYS));
		tournamentRepository.save(t);

		createConfig(t, SeedingMethod.RANDOM.name());
		addDoubleEliminationRaceToRules(t, 4);

		List<String> emails = List.of(
				"player18@gmail.com", "player28@gmail.com", "player2@gmail.com", "player12@gmail.com",
				"player22@gmail.com", "player9@gmail.com", "player30@gmail.com", "player4@gmail.com");
		Map<Long, Integer> power = createSeededParticipants(t, emails, false);

		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId()); // → DRAW_DONE

		simulateAllMatches(t, owner, power); // đấu xong nhánh thắng + nhánh thua
		bracketGenerationService.populateFinalBracket(t.getId(), owner.getId()); // → FINAL_BRACKET_READY, dừng ở đây

		log.info("Seeded FINAL_BRACKET_READY: {} (8 cơ thủ, chờ đấu chung kết Last-4)", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Shared helpers (bản sao rút gọn từ TournamentSeedInitializer — mỗi seeder tự chứa, không
	//  chia sẻ method riêng tư giữa các CommandLineRunner độc lập)
	// ══════════════════════════════════════════════════════════════════════

	private Tournament createTournament(String name, String description,
			String gameType, String format, int maxParticipants,
			BigDecimal entryFee, BigDecimal prizePool, String prizeDescription,
			User owner, Branch branch) {
		Instant now = Instant.now();
		return tournamentRepository.save(Tournament.builder()
				.name(name)
				.description(description)
				.thumbnailUrl(SeedImages.thumbnailFor(name))
				.bannerUrl(SeedImages.bannerFor(name))
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
				.tournament(t).formatCode(t.getFormat()).seedingMethod(seedingMethod).build());
	}

	private void addSingleEliminationRaceToRules(Tournament t) {
		addStandardSingleEliminationConfig(t);
		addRaceToRule(t, "quarter_final", "KNOCKOUT", 5);
		addRaceToRule(t, "semi_final", "KNOCKOUT", 7);
		addRaceToRule(t, "third_place", "KNOCKOUT", 7);
		addRaceToRule(t, "final", "KNOCKOUT", 9);
	}

	/**
	 * roundKey PHẢI khớp {@code resolveWinnersRoundKey}/{@code resolveLosersRoundKey}
	 * (BracketGenerationServiceImpl) — nhánh thắng đặt tên theo "còn cách chung kết mấy vòng"
	 * (winners_qf/sf/final), KHÔNG phải winners_r1/r2/r3 (key đó không bao giờ được tra tới, seed
	 * cũ dùng nhầm khiến override vô tác dụng, luôn rơi về default catalog).
	 */
	private void addDoubleEliminationRaceToRules(Tournament t, int sePhaseSize) {
		addStandardDoubleEliminationConfig(t, sePhaseSize);
		addRaceToRule(t, "winners_qf", "WINNERS", 5);
		addRaceToRule(t, "winners_sf", "WINNERS", 7);
		addRaceToRule(t, "winners_final", "WINNERS", 7);
		addRaceToRule(t, "losers_r1", "LOSERS", 5);
		addRaceToRule(t, "losers_r2", "LOSERS", 5);
		addRaceToRule(t, "losers_r3", "LOSERS", 7);
		addRaceToRule(t, "losers_final", "LOSERS", 7);
		addRaceToRule(t, "grand_final", "GRAND_FINAL", 9);
	}

	private void addRaceToRule(Tournament t, String roundKey, String bracketPhase, int raceTo) {
		if (raceToRuleRepository.findByTournamentIdAndRoundKey(t.getId(), roundKey).isPresent()) return;
		raceToRuleRepository.save(TournamentRaceToRule.builder()
				.tournament(t).roundKey(roundKey).bracketPhase(bracketPhase).raceTo(raceTo).build());
	}

	/** Field bắt buộc của SINGLE_ELIMINATION — thiếu thì Owner thấy "Cấu hình chưa hoàn tất". */
	private void addStandardSingleEliminationConfig(Tournament t) {
		addConfigValue(t, "break_rule", "ALTERNATE_BREAK");
		addConfigValue(t, "lag_for_break", "true");
		addConfigValue(t, "third_place_match", "true");
		addConfigValue(t, "scoring_unit", "GAME");
	}

	/** Field bắt buộc của DOUBLE_ELIMINATION, gồm {@code se_phase_size} (số người vào Last X). */
	private void addStandardDoubleEliminationConfig(Tournament t, int sePhaseSize) {
		addConfigValue(t, "break_rule", "ALTERNATE_BREAK");
		addConfigValue(t, "lag_for_break", "true");
		addConfigValue(t, "scoring_unit", "GAME");
		addConfigValue(t, "se_phase_size", String.valueOf(sePhaseSize));
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

	/**
	 * Tạo Participant kèm Registration (APPROVED) + Payment (SUCCESS nếu giải có phí) cho từng cơ
	 * thủ trong {@code emails}. {@code assignSeed=true} thì gán {@code seedNo} = thứ tự trong danh
	 * sách (1 = hạt giống mạnh nhất) — dùng cho giải {@code SeedingMethod.SEED}.
	 * Thứ tự trong {@code emails} luôn đóng vai trò "độ mạnh" giả lập để mô phỏng kết quả trận đấu,
	 * bất kể có gán seedNo hay không.
	 */
	private Map<Long, Integer> createSeededParticipants(Tournament t, List<String> emails, boolean assignSeed) {
		long existing = participantRepository.countByTournamentIdAndStatus(
				t.getId(), ParticipantStatus.ACTIVE.getValue());
		if (existing >= emails.size()) return Map.of();

		// paidAt PHẢI sau createdAt (Hibernate @CreationTimestamp tự stamp lúc insert) — lùi ngày ở
		// đây từng làm avgConversionMinutes âm hàng chục nghìn phút trên dashboard giao dịch.
		Instant paidAt = Instant.now().plusSeconds(5);
		Map<Long, Integer> power = new HashMap<>();
		for (int i = 0; i < emails.size(); i++) {
			String email = emails.get(i);
			User player = userRepository.findByEmail(email)
					.orElseThrow(() -> new IllegalStateException("Player không tồn tại: " + email));
			String displayName = player.getProfile() != null
					? player.getProfile().getFullName() : player.getEmail();

			Registration reg = registrationRepository.save(Registration.builder()
					.tournament(t)
					.user(player)
					.registrationType(ParticipantType.SINGLE.name())
					.playerFullName(displayName)
					.playerPhone(player.getPhone())
					.status(RegistrationStatus.APPROVED.getValue())
					.approvedBy(t.getCreatedBy())
					.approvedAt(paidAt)
					.build());

			if (t.getEntryFee() != null && t.getEntryFee().compareTo(BigDecimal.ZERO) > 0) {
				paymentRepository.save(Payment.builder()
						.user(player)
						.registration(reg)
						.amount(t.getEntryFee())
						.paymentMethod("PAYOS")
						.status(PaymentStatus.SUCCESS.getValue())
						.transactionCode("SEED-" + t.getId() + "-" + player.getId())
						.paidAt(paidAt)
						.build());
			}

			String rank = player.getProfile() != null ? player.getProfile().getBilliardRank() : null;
			Participant p = participantRepository.save(Participant.builder()
					.tournament(t)
					.registration(reg)
					.participantType(ParticipantType.SINGLE.getValue())
					.displayName(displayName)
					.billiardRank(rank)
					.seedNo(assignSeed ? i + 1 : null)
					.status(ParticipantStatus.ACTIVE.getValue())
					.build());
			power.put(p.getId(), i);
		}
		return power;
	}

	/** SINGLE_ELIMINATION: generate → confirm → IN_PROGRESS → đấu hết. */
	private void generateConfirmAndPlaySE(Tournament t, User owner, Map<Long, Integer> power) {
		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId());
		t.setStatus(TournamentStatus.IN_PROGRESS.getValue());
		tournamentRepository.save(t);
		simulateAllMatches(t, owner, power);
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
