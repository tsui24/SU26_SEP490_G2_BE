package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfig;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValue;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfigValueId;
import com.capstone.su26_sep490_g2_be.entity.TournamentStage;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.SeedingMethod;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.ConfigFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentConfigRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentConfigValueRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentStageRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Tạo 1 giải PROGRESSIVE_ROUND_ROBIN (thể thức "tổng hợp") ĐÃ HOÀN THÀNH TOÀN BỘ — dùng để
 * kiểm chứng end-to-end: sinh khung (draw) → chơi hết GĐ1 → advance → chơi hết GĐ2 → advance →
 * chơi hết Playoff → COMPLETED. Toàn bộ bước gọi thẳng service thật (BracketGenerationService,
 * MatchService) — không tự viết lại logic — để đảm bảo dữ liệu đúng y hệt luồng thật của app.
 *
 * 8 cơ thủ, pe_survivors_per_stage="6,4": GĐ1 (8→6) → GĐ2 (6→4) → Playoff (4 người, BK+CK).
 * "Sức mạnh" mỗi cơ thủ = thứ tự tạo (người đầu mạnh nhất) — người mạnh hơn luôn thắng, để kết
 * quả có thể đoán trước và dễ kiểm tra (đường tiến của cơ thủ #1 phải luôn đi tới vô địch).
 *
 * Idempotent — kiểm tra tên giải tồn tại trước khi chạy. Chạy sau EmailTemplateSeedInitializer.
 */
@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class ProgressiveRoundRobinSeedInitializer implements CommandLineRunner {

	private static final String NAME = "BTMS [Test] Vòng Tròn Loại Dần — 8 Cơ Thủ (Hoàn Thành)";

	private final UserRepository userRepository;
	private final TournamentRepository tournamentRepository;
	private final TournamentConfigRepository tournamentConfigRepository;
	private final TournamentConfigValueRepository configValueRepository;
	private final ConfigFieldDefinitionRepository configFieldRepository;
	private final ParticipantRepository participantRepository;
	private final TournamentStageRepository stageRepository;
	private final MatchRepository matchRepository;
	private final BracketGenerationService bracketGenerationService;
	private final MatchService matchService;

	@Override
	@Transactional
	public void run(String... args) {
		if (tournamentExists(NAME)) return;

		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) {
			log.warn("ProgressiveRoundRobinSeedInitializer: owner@gmail.com chưa tồn tại — bỏ qua.");
			return;
		}

		Instant now = Instant.now();
		Tournament t = tournamentRepository.save(Tournament.builder()
				.name(NAME)
				.description("Giải test Vòng tròn loại dần (PROGRESSIVE_ROUND_ROBIN) — 8 cơ thủ, "
						+ "đã chơi hết GĐ1 (8→6), GĐ2 (6→4) và Playoff (Bán kết + Chung kết). "
						+ "Dùng để kiểm tra hiển thị nhiều giai đoạn + bảng xếp hạng + bracket playoff.")
				.gameType("9_BALL")
				.format("PROGRESSIVE_ROUND_ROBIN")
				.participantType(ParticipantType.SINGLE.getValue())
				.status(TournamentStatus.REGISTRATION_CLOSED.getValue())
				.maxParticipants(8)
				.tableCount(2)
				.entryFee(BigDecimal.ZERO)
				.prizePool(BigDecimal.valueOf(4000000))
				.prizeDescription("Vô địch 2.000.000đ · Á quân 1.200.000đ · Hạng 3-4 400.000đ")
				.registrationDeadline(now.minus(2, ChronoUnit.DAYS))
				.startAt(now.minus(1, ChronoUnit.DAYS))
				.endAt(now.plus(1, ChronoUnit.DAYS))
				.isRegister(false)
				.createdBy(owner)
				.build());

		tournamentConfigRepository.save(TournamentConfig.builder()
				.tournament(t).formatCode(t.getFormat()).seedingMethod(SeedingMethod.RANDOM.name()).build());

		addStringConfig(t, "pe_survivors_per_stage", "6,4");
		addStringConfig(t, "final_playoff_size", "4");
		addStringConfig(t, "group_tiebreaker_order", "POINTS,RACK_DIFF,RACKS_WON,HEAD_TO_HEAD");
		addStringConfig(t, "break_rule", "ALTERNATE_BREAK");
		addStringConfig(t, "lag_for_break", "true");
		addStringConfig(t, "scoring_unit", "GAME");

		List<String> names = List.of(
				"Nguyễn Minh Cường",  // #1 — mạnh nhất, dự kiến vô địch
				"Trần Bảo Long",
				"Lê Quang Huy",
				"Phạm Anh Tuấn",
				"Hoàng Đức Thịnh",
				"Vũ Trọng Nghĩa",
				"Đặng Văn Sơn",
				"Bùi Nhật Minh"       // #8 — yếu nhất
		);
		List<Participant> participants = new ArrayList<>();
		for (String name : names) {
			participants.add(participantRepository.save(Participant.builder()
					.tournament(t).registration(null)
					.participantType(ParticipantType.SINGLE.getValue())
					.displayName(name).status(ParticipantStatus.ACTIVE.getValue())
					.build()));
		}
		// Sức mạnh: rank theo thứ tự tạo (0 = mạnh nhất)
		Map<Long, Integer> power = new HashMap<>();
		for (int i = 0; i < participants.size(); i++) power.put(participants.get(i).getId(), i);

		// ── Sinh khung + chơi hết từng giai đoạn bằng service thật ──────────
		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId());

		t.setStatus(TournamentStatus.IN_PROGRESS.getValue());
		tournamentRepository.save(t);

		Random rnd = new Random(42); // seed cố định — kết quả tái lập được

		// GĐ1 → advance → GĐ2 → advance → Playoff (điền seeding) → chơi Playoff
		playAllPendingMatches(t.getId(), "PROGRESSIVE_ROUND", power, rnd, owner.getId());
		bracketGenerationService.advanceProgressiveStage(t.getId());   // GĐ1 → điền GĐ2
		playAllPendingMatches(t.getId(), "PROGRESSIVE_ROUND", power, rnd, owner.getId());
		bracketGenerationService.advanceProgressiveStage(t.getId());   // GĐ2 → điền Playoff
		playAllPendingMatches(t.getId(), "PROGRESSIVE_PLAYOFF", power, rnd, owner.getId());

		t.setStatus(TournamentStatus.COMPLETED.getValue());
		tournamentRepository.save(t);

		log.info("Seeded: {} (8 cơ thủ, GĐ1→GĐ2→Playoff đã hoàn thành, vô địch dự kiến: {})",
				NAME, names.get(0));
	}

	/**
	 * Chơi hết mọi trận PENDING đã có đủ 2 người của các stage thuộc {@code stageType} chưa
	 * COMPLETED. Người "mạnh" hơn (power thấp hơn) luôn thắng — điểm thắng = raceTo, điểm thua
	 * ngẫu nhiên (có seed cố định) trong khoảng [0, raceTo-1). Gọi qua MatchService thật nên
	 * next-match links (Playoff) tự động được điền đúng như luồng thật.
	 */
	private void playAllPendingMatches(Long tournamentId, String stageType,
			Map<Long, Integer> power, Random rnd, Long actorUserId) {
		List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
		for (TournamentStage stage : stages) {
			if (!stageType.equals(stage.getStageType())) continue;
			List<Match> matches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stage.getId());
			for (Match m : matches) {
				if (!MatchStatus.PENDING.getValue().equals(m.getStatus())) continue;      // đã xong/BYE
				if (m.getPlayer1() == null || m.getPlayer2() == null) continue;           // chưa có đủ người (vòng sau)

				Long p1Id = m.getPlayer1().getId(), p2Id = m.getPlayer2().getId();
				boolean p1Wins = power.getOrDefault(p1Id, 99) < power.getOrDefault(p2Id, 99);
				int raceTo = m.getRaceTo() != null ? m.getRaceTo() : 5;
				int loserScore = raceTo <= 1 ? 0 : rnd.nextInt(raceTo);
				int p1Score = p1Wins ? raceTo : loserScore;
				int p2Score = p1Wins ? loserScore : raceTo;

				matchService.updateScore(m.getId(), p1Score, p2Score, actorUserId);
				matchService.completeMatch(m.getId(), p1Wins ? p1Id : p2Id, true, actorUserId);
			}
		}
	}

	private void addStringConfig(Tournament t, String key, String value) {
		TournamentConfigValueId id = new TournamentConfigValueId(t.getId(), key);
		if (configValueRepository.existsById(id)) return;
		ConfigFieldDefinition fieldDef = configFieldRepository.findById(key).orElse(null);
		if (fieldDef == null) { log.warn("ConfigFieldDefinition '{}' chưa tồn tại, bỏ qua.", key); return; }
		configValueRepository.save(TournamentConfigValue.builder()
				.id(id).tournament(t).fieldDefinition(fieldDef).value(value).build());
	}

	private boolean tournamentExists(String name) {
		return tournamentRepository.findAll().stream().anyMatch(t -> name.equals(t.getName()));
	}
}
