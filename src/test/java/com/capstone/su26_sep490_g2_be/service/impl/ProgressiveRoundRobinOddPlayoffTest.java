package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.SeedingMethod;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.*;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression cho việc bỏ ràng buộc "playoffSize phải chẵn" (xem ProgressiveSurvivorsUtil).
 *
 * <p>Dựng thật một giải PROGRESSIVE_ROUND_ROBIN 6 người, {@code pe_survivors_per_stage = "5"}
 * (1 giai đoạn vòng tròn duy nhất, giữ lại 5 người vào Playoff) — chạy qua đúng luồng production:
 * generate → confirmDraw → chơi hết vòng tròn (kết quả tất định theo "power") →
 * advanceProgressiveStage. Sau đó đọc thẳng các Match của stage Playoff từ DB và so với bracket
 * kỳ vọng: nextPowerOf2(5) = 8 → 3 slot ảo → hạng 1-3 nhận BYE vòng 1, chỉ hạng 4 vs hạng 5 đấu
 * thật; hạng 1 và hạng 2 rơi vào 2 nửa nhánh khác nhau nên không thể gặp nhau trước chung kết.
 *
 * <p>{@code @Transactional} trên test tự rollback toàn bộ dữ liệu sau khi chạy — không để lại rác
 * trong DB dev cục bộ.
 */
@SpringBootTest
@Transactional
class ProgressiveRoundRobinOddPlayoffTest {

	@Autowired private UserRepository userRepository;
	@Autowired private BranchRepository branchRepository;
	@Autowired private TournamentRepository tournamentRepository;
	@Autowired private TournamentConfigRepository tournamentConfigRepository;
	@Autowired private TournamentConfigValueRepository configValueRepository;
	@Autowired private ConfigFieldDefinitionRepository configFieldRepository;
	@Autowired private ParticipantRepository participantRepository;
	@Autowired private TournamentStageRepository stageRepository;
	@Autowired private MatchRepository matchRepository;
	@Autowired private BracketGenerationService bracketGenerationService;
	@Autowired private MatchService matchService;

	@Test
	void playoffOf5_top3GetByeInRound1_top1And2SeparatedUntilFinal() {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		assertNotNull(owner, "Cần DataInitializer đã seed owner@gmail.com trước khi chạy test này");
		Branch branch = branchRepository.findByOwnerId(owner.getId()).stream().findFirst().orElse(null);

		Instant now = Instant.now();
		Tournament t = tournamentRepository.save(Tournament.builder()
				.name("TEST — Playoff 5 người (odd playoff)")
				.description("Test tự động — kiểm tra playoffSize lẻ sau khi bỏ ràng buộc số chẵn")
				.gameType("9_BALL")
				.format("PROGRESSIVE_ROUND_ROBIN")
				.participantType(ParticipantType.SINGLE.getValue())
				.status(TournamentStatus.REGISTRATION_CLOSED.getValue())
				.maxParticipants(6)
				.tableCount(2)
				.entryFee(BigDecimal.ZERO)
				.prizePool(BigDecimal.ZERO)
				.registrationDeadline(now.minus(10, ChronoUnit.DAYS))
				.startAt(now.minus(7, ChronoUnit.DAYS))
				.endAt(now.minus(1, ChronoUnit.DAYS))
				.isShowTournament(false)
				.isPublicRatio(true)
				.isRegister(true)
				.createdBy(owner)
				.branch(branch)
				.venueName(branch != null ? branch.getName() : null)
				.venueAddress(branch != null ? branch.getAddress() : null)
				.build());

		tournamentConfigRepository.save(TournamentConfig.builder()
				.tournament(t).formatCode(t.getFormat()).seedingMethod(SeedingMethod.RANDOM.name()).build());

		addConfigValue(t, "pe_survivors_per_stage", "5");
		addConfigValue(t, "final_playoff_size", "5");
		addConfigValue(t, "break_rule", "ALTERNATE_BREAK");
		addConfigValue(t, "lag_for_break", "true");
		addConfigValue(t, "scoring_unit", "GAME");

		// power thấp hơn = mạnh hơn = hạng cao hơn sau vòng tròn (đấu tất định, không phụ thuộc
		// random). index 0..5 -> hạng 1..6.
		List<String> names = List.of("Hạng 1", "Hạng 2", "Hạng 3", "Hạng 4", "Hạng 5", "Hạng 6");
		Map<Long, Integer> power = new LinkedHashMap<>();
		Map<Integer, Long> idByRank = new HashMap<>();
		for (int i = 0; i < names.size(); i++) {
			Participant p = participantRepository.save(Participant.builder()
					.tournament(t).registration(null)
					.participantType(ParticipantType.SINGLE.getValue())
					.displayName(names.get(i))
					.status(ParticipantStatus.ACTIVE.getValue())
					.build());
			power.put(p.getId(), i);
			idByRank.put(i + 1, p.getId());
		}

		// Draw — sinh vòng tròn GĐ1 (điền người thật) + bracket Playoff rỗng (playoffSize=5).
		// Nếu ProgressiveSurvivorsUtil còn chặn số lẻ thì generate() ném BusinessException ngay đây.
		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId());
		t.setStatus(TournamentStatus.IN_PROGRESS.getValue());
		tournamentRepository.save(t);

		playAllPending(t.getId(), "PROGRESSIVE_ROUND", power, owner.getId());

		// Chỉ 1 giai đoạn vòng tròn (list "5" có đúng 1 phần tử) -> lệnh này đi thẳng vào nhánh
		// "GĐ League cuối -> điền seeding vào Playoff" (fillProgressivePlayoff -> assignSeededRound1,
		// CÙNG hàm mà SINGLE_ELIMINATION dùng).
		bracketGenerationService.advanceProgressiveStage(t.getId());

		TournamentStage playoff = stageRepository.findByTournamentIdOrderByOrderNoAsc(t.getId()).stream()
				.filter(s -> "PROGRESSIVE_PLAYOFF".equals(s.getStageType()))
				.findFirst().orElseThrow();

		List<Match> playoffMatches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(playoff.getId());
		Match r1m1 = pick(playoffMatches, 1, 1);
		Match r1m2 = pick(playoffMatches, 1, 2);
		Match r1m3 = pick(playoffMatches, 1, 3);
		Match r1m4 = pick(playoffMatches, 1, 4);
		Match r2m1 = pick(playoffMatches, 2, 1);
		Match r2m2 = pick(playoffMatches, 2, 2);

		// R1-M1: Hạng 1 BYE, tự thắng ngay
		assertTrue(r1m1.getIsBye(), "R1-M1 phải là BYE");
		assertEquals(idByRank.get(1), r1m1.getPlayer1().getId());
		assertNull(r1m1.getPlayer2());
		assertEquals(idByRank.get(1), r1m1.getWinner().getId());

		// R1-M2: Hạng 4 vs Hạng 5 — trận thật DUY NHẤT của vòng 1
		assertFalse(r1m2.getIsBye(), "R1-M2 (hạng4 vs hạng5) phải là trận thật, không BYE");
		assertEquals(idByRank.get(4), r1m2.getPlayer1().getId());
		assertEquals(idByRank.get(5), r1m2.getPlayer2().getId());

		// R1-M3: Hạng 2 BYE
		assertTrue(r1m3.getIsBye(), "R1-M3 phải là BYE");
		assertEquals(idByRank.get(2), r1m3.getPlayer1().getId());
		assertEquals(idByRank.get(2), r1m3.getWinner().getId());

		// R1-M4: Hạng 3 BYE
		assertTrue(r1m4.getIsBye(), "R1-M4 phải là BYE");
		assertEquals(idByRank.get(3), r1m4.getPlayer1().getId());
		assertEquals(idByRank.get(3), r1m4.getWinner().getId());

		// R2-M1: Hạng 1 đã có mặt sẵn (từ BYE), chờ người thắng trận Hạng4-Hạng5
		assertEquals(idByRank.get(1), r2m1.getPlayer1().getId());
		assertNull(r2m1.getPlayer2());

		// R2-M2: Hạng 2 vs Hạng 3 — cả 2 slot đã điền sẵn (đều tới từ BYE vòng 1)
		assertEquals(idByRank.get(2), r2m2.getPlayer1().getId());
		assertEquals(idByRank.get(3), r2m2.getPlayer2().getId());

		// Khẳng định yêu cầu cốt lõi: Hạng 1 và Hạng 2 nằm ở 2 trận bán kết khác nhau (2 nửa nhánh
		// khác nhau) -> không thể gặp nhau trước chung kết.
		assertNotEquals(r2m1.getId(), r2m2.getId());
	}

	private Match pick(List<Match> matches, int round, int pos) {
		return matches.stream()
				.filter(m -> m.getRoundNo() == round && m.getPositionNo() == pos)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Không thấy match R" + round + "-M" + pos));
	}

	private void addConfigValue(Tournament t, String key, String value) {
		TournamentConfigValueId id = new TournamentConfigValueId(t.getId(), key);
		ConfigFieldDefinition fieldDef = configFieldRepository.findById(key)
				.orElseThrow(() -> new AssertionError("ConfigFieldDefinition '" + key + "' chưa được seed"));
		configValueRepository.save(TournamentConfigValue.builder()
				.id(id).tournament(t).fieldDefinition(fieldDef).value(value).build());
	}

	/** Chơi hết các trận PENDING của mọi stage cùng stageType — người power thấp hơn luôn thắng. */
	private void playAllPending(Long tournamentId, String stageType, Map<Long, Integer> power, Long actorUserId) {
		List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
		for (TournamentStage stage : stages) {
			if (!stageType.equals(stage.getStageType())) continue;
			List<Match> matches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stage.getId());
			for (Match m : matches) {
				if (!"PENDING".equals(m.getStatus())) continue;
				if (m.getPlayer1() == null || m.getPlayer2() == null) continue;

				Long p1Id = m.getPlayer1().getId();
				Long p2Id = m.getPlayer2().getId();
				boolean p1Wins = power.getOrDefault(p1Id, 99) < power.getOrDefault(p2Id, 99);
				int raceTo = m.getRaceTo() != null && m.getRaceTo() > 0 ? m.getRaceTo() : 5;
				int loserScore = raceTo <= 1 ? 0 : raceTo - 1;

				matchService.updateScore(m.getId(), p1Wins ? raceTo : loserScore,
						p1Wins ? loserScore : raceTo, actorUserId);
				matchService.completeMatch(m.getId(), p1Wins ? p1Id : p2Id, false, actorUserId);
			}
		}
	}
}
