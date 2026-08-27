package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.config.bootstrap.SeedImages;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.*;
import com.capstone.su26_sep490_g2_be.repository.*;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Bổ sung phần DOUBLE_ELIMINATION và PROGRESSIVE_ROUND_ROBIN còn thiếu trong ma trận
 * "thể thức × trạng thái" — {@link TournamentSeedInitializer}/{@link TournamentLifecycleSeedInitializer}
 * đã phủ đủ 9 {@link TournamentStatus} cho SINGLE_ELIMINATION (qua {@link RegistrationSeedInitializer}),
 * nhưng DOUBLE_ELIMINATION và PROGRESSIVE_ROUND_ROBIN trước đây chỉ tồn tại ở COMPLETED (+
 * FINAL_BRACKET_READY riêng cho DE) — không có giải nào demo được DRAFT/OPEN_FOR_REGISTRATION/
 * REGISTRATION_CLOSED/DRAW_PREVIEW/DRAW_DONE/CANCELLED (và IN_PROGRESS cho riêng PRR) của 2 thể
 * thức này.
 * <p>
 * Không seed DOUBLE_ELIMINATION ở IN_PROGRESS — trạng thái đó không tồn tại thật trong vòng đời
 * Loại kép (CUT_TO_SE): trận Nhánh Thắng/Thua chấm điểm được ngay từ DRAW_DONE, và
 * {@code patchStatus} đã chặn cứng transition DRAW_DONE→IN_PROGRESS cho format này (xem
 * {@code OwnerTournamentServiceImpl#patchStatus}) — seed 1 giải DE "IN_PROGRESS" sẽ tạo ra 1 trạng
 * thái không ai có thể đạt tới bằng luồng thật.
 * <p>
 * Cả SEED lẫn RANDOM đều có mặt (không chỉ RANDOM) để hạt giống hiển thị được ở nhiều trạng thái
 * khác nhau, không chỉ lúc giải đã xong.
 * <p>
 * Idempotent theo tên giải đấu. Chạy sau {@link TournamentLifecycleSeedInitializer} (@Order 6).
 */
@Slf4j
@Component
@Order(7)
@RequiredArgsConstructor
public class TournamentFormatStatusSeedInitializer implements CommandLineRunner {

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
	private final BranchRepository branchRepository;
	private final BracketGenerationService bracketGenerationService;
	private final MatchService matchService;

	@Override
	@Transactional
	public void run(String... args) {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) {
			log.warn("TournamentFormatStatusSeedInitializer: owner@gmail.com chưa tồn tại — bỏ qua.");
			return;
		}
		Branch branch = branchRepository.findByOwnerId(owner.getId()).stream().findFirst().orElse(null);

		// DOUBLE_ELIMINATION — 6 giải, phủ nốt các trạng thái còn thiếu
		seedDeDraft(owner, branch);
		seedDeOpenForRegistration(owner, branch);
		seedDeRegistrationClosed(owner, branch);
		seedDeDrawPreview(owner, branch);
		seedDeDrawDoneMidPlay(owner, branch);
		seedDeCancelled(owner, branch);

		// PROGRESSIVE_ROUND_ROBIN — 7 giải, phủ nốt các trạng thái còn thiếu
		seedPrrDraft(owner, branch);
		seedPrrOpenForRegistration(owner, branch);
		seedPrrRegistrationClosed(owner, branch);
		seedPrrDrawPreview(owner, branch);
		seedPrrDrawDone(owner, branch);
		seedPrrInProgress(owner, branch);
		seedPrrCancelled(owner, branch);

		log.info("TournamentFormatStatusSeedInitializer hoàn thành — 6 giải DOUBLE_ELIMINATION + "
				+ "7 giải PROGRESSIVE_ROUND_ROBIN, phủ đủ trạng thái còn thiếu cho 2 thể thức");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  DOUBLE_ELIMINATION
	// ══════════════════════════════════════════════════════════════════════

	private void seedDeDraft(User owner, Branch branch) {
		final String name = "Giải Loại Kép Sơ Khởi Xuân 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải loại kép đang được soạn thảo, chưa công bố cho người chơi thấy.",
				"9_BALL", "DOUBLE_ELIMINATION", 8,
				BigDecimal.ZERO, null, null,
				TournamentStatus.DRAFT, owner, branch);
		createConfig(t, SeedingMethod.RANDOM.name());
		addDoubleEliminationRaceToRules(t, 4);
		log.info("Seeded DOUBLE_ELIMINATION DRAFT: {}", name);
	}

	private void seedDeOpenForRegistration(User owner, Branch branch) {
		final String name = "Cúp Loại Kép Mùa Hè Mở Rộng 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải loại kép đang mở đăng ký — còn 3 chỗ trống, chưa đủ người để đóng sổ.",
				"8_BALL", "DOUBLE_ELIMINATION", 8,
				BigDecimal.valueOf(150000), BigDecimal.valueOf(3000000),
				"Vô địch 1.500.000đ · Á quân 900.000đ · Hạng 3 600.000đ",
				TournamentStatus.OPEN_FOR_REGISTRATION, owner, branch);
		createConfig(t, SeedingMethod.RANDOM.name());
		addDoubleEliminationRaceToRules(t, 4);

		List<String> emails = List.of(
				"player1@gmail.com", "player2@gmail.com", "player3@gmail.com",
				"player4@gmail.com", "player5@gmail.com");
		createSeededParticipants(t, emails, false);
		log.info("Seeded DOUBLE_ELIMINATION OPEN_FOR_REGISTRATION: {} (5/8 đã đăng ký)", name);
	}

	private void seedDeRegistrationClosed(User owner, Branch branch) {
		final String name = "Giải Loại Kép Thu Đông Các Câu Lạc Bộ 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải loại kép giao lưu các câu lạc bộ — vừa đóng đăng ký đủ 8 người, chờ ban tổ chức "
						+ "bốc thăm. Xếp hạt giống theo hạng bi-a đã khai.",
				"10_BALL", "DOUBLE_ELIMINATION", 8,
				BigDecimal.valueOf(180000), BigDecimal.valueOf(4000000),
				"Vô địch 2.000.000đ · Á quân 1.200.000đ · Hạng 3 800.000đ",
				TournamentStatus.REGISTRATION_CLOSED, owner, branch);
		createConfig(t, SeedingMethod.SEED.name());
		addDoubleEliminationRaceToRules(t, 4);

		List<String> emails = List.of(
				"player6@gmail.com", "player7@gmail.com", "player8@gmail.com", "player9@gmail.com",
				"player10@gmail.com", "player11@gmail.com", "player12@gmail.com", "player13@gmail.com");
		createSeededParticipants(t, emails, true);
		log.info("Seeded DOUBLE_ELIMINATION REGISTRATION_CLOSED: {} (8/8, xếp hạt giống)", name);
	}

	private void seedDeDrawPreview(User owner, Branch branch) {
		final String name = "Cúp Loại Kép Tân Niên Chờ Xác Nhận 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Cúp mừng năm mới thể thức loại kép — vừa bốc thăm nháp xong, ban tổ chức đang xem "
						+ "lại bảng đấu trước khi khoá bracket chính thức.",
				"8_BALL", "DOUBLE_ELIMINATION", 8,
				BigDecimal.valueOf(200000), BigDecimal.valueOf(4500000),
				"Vô địch 2.200.000đ · Á quân 1.300.000đ · Hạng 3 1.000.000đ",
				TournamentStatus.REGISTRATION_CLOSED, owner, branch);
		createConfig(t, SeedingMethod.SEED.name());
		addDoubleEliminationRaceToRules(t, 4);

		List<String> emails = List.of(
				"player14@gmail.com", "player15@gmail.com", "player16@gmail.com", "player17@gmail.com",
				"player18@gmail.com", "player19@gmail.com", "player20@gmail.com", "player21@gmail.com");
		createSeededParticipants(t, emails, true);

		bracketGenerationService.generate(t.getId(), owner.getId()); // → DRAW_PREVIEW, dừng ở đây
		log.info("Seeded DOUBLE_ELIMINATION DRAW_PREVIEW: {} (8 cơ thủ, chờ BTC xác nhận bốc thăm)", name);
	}

	private void seedDeDrawDoneMidPlay(User owner, Branch branch) {
		final String name = "Giải Loại Kép Trung Thu Đang Thi Đấu 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải loại kép mừng Trung Thu — bracket đã khoá, một số trận Nhánh Thắng/Thua đã có "
						+ "kết quả, các trận còn lại đang chờ trọng tài chấm điểm.",
				"9_BALL", "DOUBLE_ELIMINATION", 8,
				BigDecimal.valueOf(160000), BigDecimal.valueOf(3500000),
				"Vô địch 1.800.000đ · Á quân 1.000.000đ · Hạng 3 700.000đ",
				TournamentStatus.REGISTRATION_CLOSED, owner, branch);
		t.setStartAt(Instant.now().minus(1, ChronoUnit.DAYS));
		t.setEndAt(Instant.now().plus(2, ChronoUnit.DAYS));
		tournamentRepository.save(t);

		createConfig(t, SeedingMethod.SEED.name());
		addDoubleEliminationRaceToRules(t, 4);

		List<String> emails = List.of(
				"player22@gmail.com", "player23@gmail.com", "player24@gmail.com", "player25@gmail.com",
				"player26@gmail.com", "player27@gmail.com", "player28@gmail.com", "player29@gmail.com");
		Map<Long, Integer> power = createSeededParticipants(t, emails, true);

		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId()); // → DRAW_DONE

		// Chỉ đấu 1 phần Nhánh Thắng/Thua rồi dừng — mô phỏng giải đang thi đấu dở, KHÔNG gọi
		// populateFinalBracket() (đó là kịch bản của seedFinalBracketReady bên
		// TournamentLifecycleSeedInitializer, giải này cố tình dừng sớm hơn).
		simulatePartialMatches(t, owner, power, 3);
		log.info("Seeded DOUBLE_ELIMINATION DRAW_DONE (đang thi đấu dở): {} (8 cơ thủ)", name);
	}

	private void seedDeCancelled(User owner, Branch branch) {
		final String name = "Cúp Loại Kép Hủy Vì Thiếu Người 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải loại kép đã bị huỷ do không đủ số người đăng ký tối thiểu trước hạn chót.",
				"8_BALL", "DOUBLE_ELIMINATION", 8,
				BigDecimal.valueOf(150000), BigDecimal.valueOf(3000000),
				"Vô địch 1.500.000đ · Á quân 900.000đ",
				TournamentStatus.CANCELLED, owner, branch);
		createConfig(t, SeedingMethod.RANDOM.name());
		addDoubleEliminationRaceToRules(t, 4);

		List<String> emails = List.of("player30@gmail.com", "player1@gmail.com", "player2@gmail.com");
		createSeededParticipants(t, emails, false);
		log.info("Seeded DOUBLE_ELIMINATION CANCELLED: {} (3 người đã đăng ký trước khi huỷ)", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  PROGRESSIVE_ROUND_ROBIN
	// ══════════════════════════════════════════════════════════════════════

	private void seedPrrDraft(User owner, Branch branch) {
		final String name = "Giải Vòng Tròn Sơ Khởi Đông Xuân 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải vòng tròn loại dần đang được soạn thảo, chưa công bố cho người chơi thấy.",
				"9_BALL", "PROGRESSIVE_ROUND_ROBIN", 8,
				BigDecimal.ZERO, null, null,
				TournamentStatus.DRAFT, owner, branch);
		createConfig(t, SeedingMethod.RANDOM.name());
		addProgressiveConfig(t);
		log.info("Seeded PROGRESSIVE_ROUND_ROBIN DRAFT: {}", name);
	}

	private void seedPrrOpenForRegistration(User owner, Branch branch) {
		final String name = "Giải Vòng Tròn Mùa Hè Đang Mở Đăng Ký 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải vòng tròn loại dần đang mở đăng ký — còn 3 chỗ trống, chưa đủ người để đóng sổ.",
				"9_BALL", "PROGRESSIVE_ROUND_ROBIN", 8,
				BigDecimal.ZERO, BigDecimal.valueOf(2500000),
				"Vô địch 1.500.000đ · Á quân 700.000đ · Hạng 3-4 150.000đ",
				TournamentStatus.OPEN_FOR_REGISTRATION, owner, branch);
		createConfig(t, SeedingMethod.RANDOM.name());
		addProgressiveConfig(t);

		List<String> emails = List.of(
				"player3@gmail.com", "player4@gmail.com", "player5@gmail.com",
				"player6@gmail.com", "player7@gmail.com");
		createSeededParticipants(t, emails, false);
		log.info("Seeded PROGRESSIVE_ROUND_ROBIN OPEN_FOR_REGISTRATION: {} (5/8 đã đăng ký)", name);
	}

	private void seedPrrRegistrationClosed(User owner, Branch branch) {
		final String name = "Giải Vòng Tròn Thu Đông Đã Đóng Đăng Ký 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải vòng tròn loại dần vừa đóng đăng ký đủ 8 người, chờ ban tổ chức bốc thăm xếp "
						+ "lịch vòng tròn. Xếp hạt giống để tránh 2 cơ thủ mạnh nhất gặp nhau sớm.",
				"9_BALL", "PROGRESSIVE_ROUND_ROBIN", 8,
				BigDecimal.valueOf(100000), BigDecimal.valueOf(3000000),
				"Vô địch 1.800.000đ · Á quân 900.000đ · Hạng 3-4 150.000đ",
				TournamentStatus.REGISTRATION_CLOSED, owner, branch);
		createConfig(t, SeedingMethod.SEED.name());
		addProgressiveConfig(t);

		List<String> emails = List.of(
				"player8@gmail.com", "player9@gmail.com", "player10@gmail.com", "player11@gmail.com",
				"player12@gmail.com", "player13@gmail.com", "player14@gmail.com", "player15@gmail.com");
		createSeededParticipants(t, emails, true);
		log.info("Seeded PROGRESSIVE_ROUND_ROBIN REGISTRATION_CLOSED: {} (8/8, xếp hạt giống)", name);
	}

	private void seedPrrDrawPreview(User owner, Branch branch) {
		final String name = "Giải Vòng Tròn Tân Niên Chờ Xác Nhận Lịch 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải vòng tròn mừng năm mới — lịch vòng tròn giai đoạn 1 vừa được sinh nháp, ban tổ "
						+ "chức đang xem lại trước khi khoá lịch thi đấu chính thức.",
				"10_BALL", "PROGRESSIVE_ROUND_ROBIN", 8,
				BigDecimal.valueOf(120000), BigDecimal.valueOf(3200000),
				"Vô địch 1.900.000đ · Á quân 1.000.000đ · Hạng 3-4 150.000đ",
				TournamentStatus.REGISTRATION_CLOSED, owner, branch);
		createConfig(t, SeedingMethod.SEED.name());
		addProgressiveConfig(t);

		List<String> emails = List.of(
				"player16@gmail.com", "player17@gmail.com", "player18@gmail.com", "player19@gmail.com",
				"player20@gmail.com", "player21@gmail.com", "player22@gmail.com", "player23@gmail.com");
		createSeededParticipants(t, emails, true);

		bracketGenerationService.generate(t.getId(), owner.getId()); // → DRAW_PREVIEW, dừng ở đây
		log.info("Seeded PROGRESSIVE_ROUND_ROBIN DRAW_PREVIEW: {} (8 cơ thủ, chờ BTC xác nhận lịch)", name);
	}

	private void seedPrrDrawDone(User owner, Branch branch) {
		final String name = "Giải Vòng Tròn Trung Thu Vừa Xác Nhận Lịch 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải vòng tròn mừng Trung Thu — lịch giai đoạn 1 đã được ban tổ chức xác nhận, chưa "
						+ "trận nào diễn ra, chờ đến ngày thi đấu.",
				"9_BALL", "PROGRESSIVE_ROUND_ROBIN", 8,
				BigDecimal.ZERO, BigDecimal.valueOf(2000000),
				"Vô địch 1.200.000đ · Á quân 600.000đ",
				TournamentStatus.REGISTRATION_CLOSED, owner, branch);
		t.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
		t.setEndAt(Instant.now().plus(3, ChronoUnit.DAYS));
		tournamentRepository.save(t);

		createConfig(t, SeedingMethod.RANDOM.name());
		addProgressiveConfig(t);

		List<String> emails = List.of(
				"player24@gmail.com", "player25@gmail.com", "player26@gmail.com", "player27@gmail.com",
				"player28@gmail.com", "player29@gmail.com", "player30@gmail.com", "player1@gmail.com");
		createSeededParticipants(t, emails, false);

		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId()); // → DRAW_DONE, dừng ở đây
		log.info("Seeded PROGRESSIVE_ROUND_ROBIN DRAW_DONE: {} (lịch đã khoá, chưa đấu trận nào)", name);
	}

	private void seedPrrInProgress(User owner, Branch branch) {
		final String name = "Giải Vòng Tròn Cây Cơ Đồng Đang Thi Đấu 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải vòng tròn loại dần phong trào — giai đoạn 1 (8→6 người) đang diễn ra, một số "
						+ "trận đã có kết quả.",
				"9_BALL", "PROGRESSIVE_ROUND_ROBIN", 8,
				BigDecimal.valueOf(80000), BigDecimal.valueOf(2800000),
				"Vô địch 1.600.000đ · Á quân 800.000đ · Hạng 3-4 150.000đ",
				TournamentStatus.REGISTRATION_CLOSED, owner, branch);
		t.setStartAt(Instant.now().minus(1, ChronoUnit.DAYS));
		t.setEndAt(Instant.now().plus(4, ChronoUnit.DAYS));
		tournamentRepository.save(t);

		createConfig(t, SeedingMethod.SEED.name());
		addProgressiveConfig(t);

		List<String> emails = List.of(
				"player2@gmail.com", "player3@gmail.com", "player4@gmail.com", "player5@gmail.com",
				"player6@gmail.com", "player7@gmail.com", "player8@gmail.com", "player9@gmail.com");
		Map<Long, Integer> power = createSeededParticipants(t, emails, true);

		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId());
		t.setStatus(TournamentStatus.IN_PROGRESS.getValue());
		tournamentRepository.save(t);

		// Đấu 1 phần lịch giai đoạn 1 rồi dừng — KHÔNG advanceProgressiveStage(), để giải còn đang
		// thật sự "dở" ở giai đoạn 1, khác hẳn 2 giải PROGRESSIVE_ROUND_ROBIN COMPLETED đã có sẵn.
		playSomePendingMatchesByStageType(t.getId(), "PROGRESSIVE_ROUND", power, owner.getId(), 6);
		log.info("Seeded PROGRESSIVE_ROUND_ROBIN IN_PROGRESS: {} (đang giữa giai đoạn 1)", name);
	}

	private void seedPrrCancelled(User owner, Branch branch) {
		final String name = "Giải Vòng Tròn Hủy Vì Mưa Bão 2026";
		if (tournamentExists(name)) return;

		Tournament t = createTournament(name,
				"Giải vòng tròn loại dần đã bị huỷ do thời tiết xấu ảnh hưởng tới lịch thi đấu.",
				"9_BALL", "PROGRESSIVE_ROUND_ROBIN", 8,
				BigDecimal.ZERO, BigDecimal.valueOf(2000000),
				"Vô địch 1.200.000đ · Á quân 600.000đ",
				TournamentStatus.CANCELLED, owner, branch);
		createConfig(t, SeedingMethod.RANDOM.name());
		addProgressiveConfig(t);

		List<String> emails = List.of("player10@gmail.com", "player11@gmail.com",
				"player12@gmail.com", "player13@gmail.com");
		createSeededParticipants(t, emails, false);
		log.info("Seeded PROGRESSIVE_ROUND_ROBIN CANCELLED: {} (4 người đã đăng ký trước khi huỷ)", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Shared helpers (bản sao rút gọn từ 2 seeder kia — mỗi CommandLineRunner tự chứa, không chia
	//  sẻ method riêng tư, đúng quy ước đã có trong TournamentLifecycleSeedInitializer)
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * {@code status} truyền tường minh (khác {@code TournamentSeedInitializer}/
	 * {@code TournamentLifecycleSeedInitializer} — 2 file đó luôn hard-code REGISTRATION_CLOSED vì
	 * chỉ seed giải đã/sắp đấu xong). Giải còn ở DRAFT/OPEN_FOR_REGISTRATION thì set mốc thời gian
	 * trong TƯƠNG LAI thay vì quá khứ — giải chưa diễn ra thì không thể có startAt/endAt đã qua.
	 */
	private Tournament createTournament(String name, String description,
			String gameType, String format, int maxParticipants,
			BigDecimal entryFee, BigDecimal prizePool, String prizeDescription,
			TournamentStatus status, User owner, Branch branch) {
		Instant now = Instant.now();
		boolean upcoming = status == TournamentStatus.DRAFT || status == TournamentStatus.OPEN_FOR_REGISTRATION;
		Instant regDeadline = upcoming ? now.plus(5, ChronoUnit.DAYS) : now.minus(10, ChronoUnit.DAYS);
		Instant startAt = upcoming ? now.plus(10, ChronoUnit.DAYS) : now.minus(7, ChronoUnit.DAYS);
		Instant endAt = upcoming ? now.plus(11, ChronoUnit.DAYS) : now.minus(1, ChronoUnit.DAYS);
		return tournamentRepository.save(Tournament.builder()
				.name(name)
				.description(description)
				.thumbnailUrl(SeedImages.thumbnailFor(name))
				.bannerUrl(SeedImages.bannerFor(name))
				.gameType(gameType)
				.format(format)
				.participantType(ParticipantType.SINGLE.getValue())
				.status(status.getValue())
				.maxParticipants(maxParticipants)
				.entryFee(entryFee)
				.prizePool(prizePool)
				.prizeDescription(prizeDescription)
				.registrationDeadline(regDeadline)
				.startAt(startAt)
				.endAt(endAt)
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

	/**
	 * roundKey PHẢI khớp {@code resolveWinnersRoundKey}/{@code resolveLosersRoundKey}
	 * (BracketGenerationServiceImpl) — nhánh thắng đặt tên theo "còn cách chung kết mấy vòng".
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

	private void addProgressiveConfig(Tournament t) {
		addConfigValue(t, "pe_survivors_per_stage", "6,4");
		addConfigValue(t, "final_playoff_size", "4");
		addConfigValue(t, "break_rule", "ALTERNATE_BREAK");
		addConfigValue(t, "lag_for_break", "true");
		addConfigValue(t, "scoring_unit", "GAME");
	}

	private void addRaceToRule(Tournament t, String roundKey, String bracketPhase, int raceTo) {
		if (raceToRuleRepository.findByTournamentIdAndRoundKey(t.getId(), roundKey).isPresent()) return;
		raceToRuleRepository.save(TournamentRaceToRule.builder()
				.tournament(t).roundKey(roundKey).bracketPhase(bracketPhase).raceTo(raceTo).build());
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

	/** Field bắt buộc của DOUBLE_ELIMINATION, gồm {@code se_phase_size} (số người vào Last X). */
	private void addStandardDoubleEliminationConfig(Tournament t, int sePhaseSize) {
		addConfigValue(t, "break_rule", "ALTERNATE_BREAK");
		addConfigValue(t, "lag_for_break", "true");
		addConfigValue(t, "scoring_unit", "GAME");
		addConfigValue(t, "se_phase_size", String.valueOf(sePhaseSize));
	}

	/**
	 * Tạo Participant kèm Registration (APPROVED) + Payment (SUCCESS nếu giải có phí) cho từng cơ
	 * thủ trong {@code emails}. {@code assignSeed=true} thì gán {@code seedNo} = thứ tự trong danh
	 * sách (1 = hạt giống mạnh nhất). Thứ tự trong {@code emails} luôn đóng vai trò "độ mạnh" giả
	 * lập để mô phỏng kết quả trận đấu, bất kể có gán seedNo hay không.
	 */
	private Map<Long, Integer> createSeededParticipants(Tournament t, List<String> emails, boolean assignSeed) {
		long existing = participantRepository.countByTournamentIdAndStatus(
				t.getId(), ParticipantStatus.ACTIVE.getValue());
		if (existing >= emails.size()) return Map.of();

		// paidAt PHẢI sau createdAt (Hibernate @CreationTimestamp tự stamp lúc insert).
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

	/**
	 * Hoàn thành tối đa {@code limit} trận PENDING đủ 2 người (thứ tự vòng/vị trí), rồi dừng lại
	 * giữa chừng — mô phỏng giải "đang thi đấu dở", khác {@code simulateAllMatches} ở 2 file kia
	 * (đấu tới khi xong hết).
	 */
	private void simulatePartialMatches(Tournament t, User owner, Map<Long, Integer> power, int limit) {
		List<Match> matches = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(t.getId());
		matches.sort(Comparator.comparing(Match::getRoundNo).thenComparing(Match::getPositionNo));

		int completed = 0;
		for (Match match : matches) {
			if (completed >= limit) break;
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
		}
		log.debug("Simulated {}/{} matches (partial) for '{}'", completed, limit, t.getName());
	}

	/** Như trên nhưng giới hạn theo {@code stageType} — dùng cho PROGRESSIVE_ROUND_ROBIN dở dang. */
	private void playSomePendingMatchesByStageType(Long tournamentId, String stageType,
			Map<Long, Integer> power, Long actorUserId, int limit) {
		List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
		int completed = 0;
		for (TournamentStage stage : stages) {
			if (!stageType.equals(stage.getStageType())) continue;
			List<Match> matches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stage.getId());
			for (Match m : matches) {
				if (completed >= limit) return;
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
				completed++;
			}
		}
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
