package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.dto.request.RejectRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SubmitTournamentRegistrationRequest;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.*;
import com.capstone.su26_sep490_g2_be.repository.*;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import com.capstone.su26_sep490_g2_be.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Bổ sung dữ liệu mẫu "sống" cho luồng đăng ký/thanh toán — khác với
 * {@link TournamentSeedInitializer} (chỉ tạo participant thẳng, bỏ qua Registration/Payment),
 * seeder này đi qua {@link RegistrationService} thật (submitRegistration → checkout/markAsPaid →
 * approve/reject/cancel) để có đủ dữ liệu test: giải ở nhiều trạng thái (DRAFT, đang mở đăng ký,
 * đã đóng đăng ký, đã bốc thăm, đang thi đấu dở, đã hủy) và đăng ký ở đủ trạng thái
 * (chờ thanh toán, thanh toán thất bại, đã duyệt, bị từ chối tự động/thủ công, đã hủy).
 * <p>
 * Vì đi qua service thật, mỗi bước cũng publish {@link com.capstone.su26_sep490_g2_be.service.MailDomainEvent}.
 * SMTP/push bị {@link com.capstone.su26_sep490_g2_be.config.StartupMailGuard} chặn suốt lúc seed
 * để không spam hộp thư mỗi lần restart. Chạy sau {@link EmailTemplateSeedInitializer} (@Order 3).
 * <p>
 * Idempotent theo tên giải đấu — an toàn khi backend restart nhiều lần trên cùng 1 DB.
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class RegistrationSeedInitializer implements CommandLineRunner {

	private static final Random RND = new Random(2026L);

	private final UserRepository userRepository;
	private final BranchRepository branchRepository;
	private final TournamentRepository tournamentRepository;
	private final TournamentConfigRepository tournamentConfigRepository;
	private final TournamentRaceToRuleRepository raceToRuleRepository;
	private final RegistrationFormTemplateRepository formTemplateRepository;
	private final RegistrationRepository registrationRepository;
	private final PaymentRepository paymentRepository;
	private final ParticipantRepository participantRepository;
	private final MatchRepository matchRepository;
	private final RegistrationService registrationService;
	private final BracketGenerationService bracketGenerationService;
	private final MatchService matchService;

	@Override
	@Transactional
	public void run(String... args) {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) {
			log.warn("RegistrationSeedInitializer: owner@gmail.com chưa tồn tại — bỏ qua.");
			return;
		}
		Branch branch = branchRepository.findByOwnerId(owner.getId()).stream().findFirst().orElse(null);
		Long basicTemplateId = formTemplateRepository.findByCode("PLAYER_REG_BASIC")
				.map(RegistrationFormTemplate::getId).orElse(null);
		Long doublesTemplateId = formTemplateRepository.findByCode("PLAYER_REG_DOUBLES")
				.map(RegistrationFormTemplate::getId).orElse(null);
		if (basicTemplateId == null || doublesTemplateId == null) {
			log.warn("RegistrationSeedInitializer: chưa có form đăng ký mặc định — bỏ qua.");
			return;
		}

		seedDraft(owner, branch);
		seedOpenFreeDoubles(owner, branch, doublesTemplateId);
		seedOpenPaidVariety(owner, branch, basicTemplateId);
		seedRegistrationClosed(owner, branch, basicTemplateId);
		seedDrawDone(owner, branch, basicTemplateId);
		seedInProgressPartial(owner, branch, basicTemplateId);
		seedCancelled(owner, branch, basicTemplateId);

		log.info("RegistrationSeedInitializer hoàn thành — 7 giải đấu mẫu qua luồng Registration/Payment thật");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  1. DRAFT — chưa công bố, chưa mở đăng ký
	// ══════════════════════════════════════════════════════════════════════

	private void seedDraft(User owner, Branch branch) {
		final String name = "Giải 9-Ball Sơ Thảo (Chưa Công Bố) 2026";
		if (tournamentExists(name)) return;

		Tournament t = baseTournament(name, "Giải đang được soạn thảo, chưa công bố cho người chơi thấy.",
				"9_BALL", "SINGLE_ELIMINATION", ParticipantType.SINGLE.getValue(),
				TournamentStatus.DRAFT.getValue(), 8,
				BigDecimal.ZERO, null, null, null, false, false, owner, branch);
		createConfig(t);
		log.info("Seeded DRAFT: {}", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  2. OPEN_FOR_REGISTRATION — miễn phí, đăng ký ĐÔI, chưa đầy
	// ══════════════════════════════════════════════════════════════════════

	private void seedOpenFreeDoubles(User owner, Branch branch, Long doublesTemplateId) {
		final String name = "Giải 8-Ball Đôi Miễn Phí — Đang Mở Đăng Ký 2026";
		if (tournamentExists(name)) return;

		Tournament t = baseTournament(name, "Giải đôi miễn phí, đang mở đăng ký — còn chỗ trống để test luồng đăng ký.",
				"8_BALL", "SINGLE_ELIMINATION", ParticipantType.DOUBLE.getValue(),
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), 8,
				BigDecimal.ZERO, BigDecimal.valueOf(2000000), "Vô địch 1.200.000đ · Á quân 800.000đ",
				doublesTemplateId, true, true, owner, branch);
		createConfig(t);

		submitDouble(t, "player1@gmail.com", "Đối tác của Hùng", "0988000001");
		submitDouble(t, "player2@gmail.com", "Đối tác của Tuấn", "0988000002");
		submitDouble(t, "player3@gmail.com", "Đối tác của Nam", "0988000003");
		log.info("Seeded OPEN_FOR_REGISTRATION (free/doubles, chưa đầy): {}", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  3. OPEN_FOR_REGISTRATION — trả phí, đủ trạng thái đăng ký/thanh toán
	// ══════════════════════════════════════════════════════════════════════

	private void seedOpenPaidVariety(User owner, Branch branch, Long basicTemplateId) {
		final String name = "Giải 9-Ball Trả Phí — Đang Mở Đăng Ký 2026";
		if (tournamentExists(name)) return;

		Tournament t = baseTournament(name, "Giải trả phí 150.000đ, chỉ 4 suất — dữ liệu mẫu đủ mọi trạng thái đăng ký/thanh toán.",
				"9_BALL", "SINGLE_ELIMINATION", ParticipantType.SINGLE.getValue(),
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), 4,
				BigDecimal.valueOf(150000), BigDecimal.valueOf(1000000), "Vô địch 600.000đ · Á quân 400.000đ",
				basicTemplateId, true, true, owner, branch);
		createConfig(t);

		// submitRegistration() tự chặn ngay từ bước nộp đơn nếu approvedCount >= maxParticipants,
		// nên phải nộp đơn cho TẤT CẢ người chơi trước (lúc chưa ai APPROVED), rồi mới thanh
		// toán/duyệt/hủy theo thứ tự bên dưới — không thể xen kẽ nộp-rồi-duyệt như file gốc.
		Registration r6 = submitSingle(t, "player6@gmail.com");
		Registration r7 = submitSingle(t, "player7@gmail.com");
		Registration r8 = submitSingle(t, "player8@gmail.com");
		Registration r9 = submitSingle(t, "player9@gmail.com");
		Registration r10 = submitSingle(t, "player10@gmail.com");
		Registration r11 = submitSingle(t, "player11@gmail.com");
		Registration r12 = submitSingle(t, "player12@gmail.com");
		Registration r13 = submitSingle(t, "player13@gmail.com");
		Registration r14 = submitSingle(t, "player14@gmail.com");

		payFailed(r14); // PENDING_PAYMENT, payment FAILED

		paySuccess(r7); // APPROVED (1/4)
		paySuccess(r8); // APPROVED (2/4)
		paySuccess(r9); // APPROVED (3/4) — sau đó tự hủy bên dưới
		paySuccess(r10); // APPROVED (4/4 — ĐẦY)
		paySuccess(r11); // thanh toán OK nhưng đã đầy → tự động REJECTED

		registrationService.cancel(r9.getId(), r9.getUser().getId()); // CANCELLED (giải phóng lại 1 suất, còn 3/4)

		// Không được approve() tay khi chưa thanh toán — giải có phí sẽ ném PAYMENT_006
		// (làm fail CommandLineRunner → @SpringBootTest/CI không lên context).
		paySuccess(r12); // APPROVED qua thanh toán (4/4 — ĐẦY lại)

		RejectRegistrationRequest rejectReq = new RejectRegistrationRequest();
		rejectReq.setReason("Thông tin đăng ký không hợp lệ — vui lòng đăng ký lại.");
		registrationService.reject(r13.getId(), owner.getId(), rejectReq); // REJECTED thủ công

		// r6: vẫn PENDING_PAYMENT, chưa từng thanh toán

		log.info("Seeded OPEN_FOR_REGISTRATION (paid variety, đủ trạng thái): {}", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  4. REGISTRATION_CLOSED — đã đóng đăng ký, đủ người, chưa bốc thăm
	// ══════════════════════════════════════════════════════════════════════

	private void seedRegistrationClosed(User owner, Branch branch, Long basicTemplateId) {
		final String name = "Giải 10-Ball Đã Đóng Đăng Ký — Chờ Bốc Thăm 2026";
		if (tournamentExists(name)) return;

		Tournament t = baseTournament(name, "Đã đủ 8 người, ban tổ chức vừa đóng đăng ký — sắp bốc thăm.",
				"10_BALL", "SINGLE_ELIMINATION", ParticipantType.SINGLE.getValue(),
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), 8,
				BigDecimal.valueOf(100000), BigDecimal.valueOf(3000000), "Vô địch 1.800.000đ · Á quân 1.000.000đ",
				basicTemplateId, true, true, owner, branch);
		createConfig(t);

		for (int i = 1; i <= 8; i++) {
			Registration r = submitSingle(t, "player" + i + "@gmail.com");
			paySuccess(r);
		}

		t.setStatus(TournamentStatus.REGISTRATION_CLOSED.getValue());
		tournamentRepository.save(t);
		log.info("Seeded REGISTRATION_CLOSED (8/8 APPROVED): {}", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  5. DRAW_DONE — đã bốc thăm xong, chưa thi đấu trận nào
	// ══════════════════════════════════════════════════════════════════════

	private void seedDrawDone(User owner, Branch branch, Long basicTemplateId) {
		final String name = "Giải 9-Ball Đã Bốc Thăm — Chưa Thi Đấu 2026";
		if (tournamentExists(name)) return;

		Tournament t = baseTournament(name, "Giải miễn phí, đã bốc thăm xong nhưng chưa trận nào diễn ra.",
				"9_BALL", "SINGLE_ELIMINATION", ParticipantType.SINGLE.getValue(),
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), 8,
				BigDecimal.ZERO, BigDecimal.valueOf(2000000), "Vô địch 1.200.000đ · Á quân 800.000đ",
				basicTemplateId, true, true, owner, branch);
		addSingleEliminationRaceToRules(t);

		for (int i = 9; i <= 16; i++) {
			submitSingle(t, "player" + i + "@gmail.com"); // miễn phí → tự động APPROVED
		}

		t.setStatus(TournamentStatus.REGISTRATION_CLOSED.getValue());
		tournamentRepository.save(t);

		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId()); // → DRAW_DONE
		log.info("Seeded DRAW_DONE (8/8, chưa thi đấu): {}", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  6. IN_PROGRESS — đang thi đấu dở (mới xong vòng 1)
	// ══════════════════════════════════════════════════════════════════════

	private void seedInProgressPartial(User owner, Branch branch, Long basicTemplateId) {
		final String name = "Giải 9-Ball Đang Thi Đấu (Vòng 1) 2026";
		if (tournamentExists(name)) return;

		Tournament t = baseTournament(name, "Giải miễn phí, đang thi đấu dở — mới hoàn thành vòng 1.",
				"9_BALL", "SINGLE_ELIMINATION", ParticipantType.SINGLE.getValue(),
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), 8,
				BigDecimal.ZERO, BigDecimal.valueOf(2000000), "Vô địch 1.200.000đ · Á quân 800.000đ",
				basicTemplateId, true, true, owner, branch);
		addSingleEliminationRaceToRules(t);

		for (int i = 1; i <= 8; i++) {
			submitSingle(t, "player" + i + "@gmail.com"); // miễn phí → tự động APPROVED
		}

		t.setStatus(TournamentStatus.REGISTRATION_CLOSED.getValue());
		tournamentRepository.save(t);

		bracketGenerationService.generate(t.getId(), owner.getId());
		bracketGenerationService.confirmDraw(t.getId(), owner.getId());
		t.setStatus(TournamentStatus.IN_PROGRESS.getValue());
		tournamentRepository.save(t);

		Map<Long, Integer> power = buildPowerMap(t);
		simulateRoundOne(t, owner, power);
		log.info("Seeded IN_PROGRESS (vòng 1 xong, vòng sau còn PENDING): {}", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  7. CANCELLED — hủy giải vì không đủ người
	// ══════════════════════════════════════════════════════════════════════

	private void seedCancelled(User owner, Branch branch, Long basicTemplateId) {
		final String name = "Giải 9-Ball Đã Hủy — Không Đủ Người 2026";
		if (tournamentExists(name)) return;

		Tournament t = baseTournament(name, "Chỉ có 2 người đăng ký sau nhiều ngày mở — ban tổ chức quyết định hủy giải.",
				"9_BALL", "SINGLE_ELIMINATION", ParticipantType.SINGLE.getValue(),
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), 8,
				BigDecimal.ZERO, null, null,
				basicTemplateId, true, true, owner, branch);
		createConfig(t);

		submitSingle(t, "player15@gmail.com");
		submitSingle(t, "player16@gmail.com");

		t.setStatus(TournamentStatus.CANCELLED.getValue());
		t.setIsRegister(false);
		tournamentRepository.save(t);
		log.info("Seeded CANCELLED (2 đăng ký còn dang dở): {}", name);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Shared helpers
	// ══════════════════════════════════════════════════════════════════════

	private Tournament baseTournament(String name, String description, String gameType, String format,
			String participantType, String status, int maxParticipants, BigDecimal entryFee,
			BigDecimal prizePool, String prizeDescription, Long registrationFormTemplateId,
			boolean isRegister, boolean isShowTournament, User owner, Branch branch) {
		Instant now = Instant.now();
		return tournamentRepository.save(Tournament.builder()
				.name(name)
				.description(description)
				.gameType(gameType)
				.format(format)
				.participantType(participantType)
				.status(status)
				.maxParticipants(maxParticipants)
				.entryFee(entryFee)
				.prizePool(prizePool)
				.prizeDescription(prizeDescription)
				.registrationDeadline(now.plus(20, ChronoUnit.DAYS))
				.startAt(now.plus(25, ChronoUnit.DAYS))
				.endAt(now.plus(26, ChronoUnit.DAYS))
				.isShowTournament(isShowTournament)
				.isPublicRatio(true)
				.isRegister(isRegister)
				.registrationFormTemplateId(registrationFormTemplateId)
				.createdBy(owner)
				.branch(branch)
				.venueName(branch != null ? branch.getName() : null)
				.venueAddress(branch != null ? branch.getAddress() : null)
				.build());
	}

	private void createConfig(Tournament t) {
		if (tournamentConfigRepository.existsById(t.getId())) return;
		tournamentConfigRepository.save(TournamentConfig.builder()
				.tournament(t).formatCode(t.getFormat()).seedingMethod(SeedingMethod.RANDOM.name()).build());
	}

	private void addSingleEliminationRaceToRules(Tournament t) {
		createConfig(t);
		addRaceToRule(t, "quarter_final", "KNOCKOUT", 5);
		addRaceToRule(t, "semi_final", "KNOCKOUT", 7);
		addRaceToRule(t, "third_place", "KNOCKOUT", 7);
		addRaceToRule(t, "final", "KNOCKOUT", 9);
	}

	private void addRaceToRule(Tournament t, String roundKey, String bracketPhase, int raceTo) {
		if (raceToRuleRepository.findByTournamentIdAndRoundKey(t.getId(), roundKey).isPresent()) return;
		raceToRuleRepository.save(TournamentRaceToRule.builder()
				.tournament(t).roundKey(roundKey).bracketPhase(bracketPhase).raceTo(raceTo).build());
	}

	/** Đăng ký đơn qua RegistrationService thật, trả về entity Registration (không phải response DTO). */
	private Registration submitSingle(Tournament t, String playerEmail) {
		User player = userRepository.findByEmail(playerEmail).orElseThrow();
		SubmitTournamentRegistrationRequest req = new SubmitTournamentRegistrationRequest();
		req.setRegistrationType(ParticipantType.SINGLE.name());
		req.setNote("Dữ liệu mẫu — đăng ký thử");
		req.setFieldValues(List.of(
				fieldValue("player_full_name", displayName(player)),
				fieldValue("player_phone", player.getPhone() != null ? player.getPhone() : "0900000000")));
		registrationService.submitRegistration(t.getId(), player.getId(), req);
		return registrationRepository.findByTournamentIdAndUserId(t.getId(), player.getId()).orElseThrow();
	}

	private Registration submitDouble(Tournament t, String captainEmail, String partnerFullName, String partnerPhone) {
		User captain = userRepository.findByEmail(captainEmail).orElseThrow();
		SubmitTournamentRegistrationRequest req = new SubmitTournamentRegistrationRequest();
		req.setRegistrationType(ParticipantType.DOUBLE.name());
		req.setNote("Dữ liệu mẫu — đăng ký đôi thử");
		req.setFieldValues(List.of(
				fieldValue("player_full_name", displayName(captain)),
				fieldValue("player_phone", captain.getPhone() != null ? captain.getPhone() : "0900000000"),
				fieldValue("player2_full_name", partnerFullName),
				fieldValue("player2_phone", partnerPhone)));
		registrationService.submitRegistration(t.getId(), captain.getId(), req);
		return registrationRepository.findByTournamentIdAndUserId(t.getId(), captain.getId()).orElseThrow();
	}

	private SubmitTournamentRegistrationRequest.FieldValueItem fieldValue(String key, String value) {
		SubmitTournamentRegistrationRequest.FieldValueItem item = new SubmitTournamentRegistrationRequest.FieldValueItem();
		item.setFieldKey(key);
		item.setValue(value);
		return item;
	}

	private String displayName(User user) {
		return user.getProfile() != null && user.getProfile().getFullName() != null
				? user.getProfile().getFullName() : user.getEmail();
	}

	/** Tạo Payment PENDING trực tiếp (bỏ qua PayOSService thật) rồi xác nhận thành công như webhook. */
	private void paySuccess(Registration reg) {
		Payment payment = createPendingPayment(reg);
		registrationService.markAsPaid(payment.getId(), "SEED-" + payment.getId());
	}

	/** Tạo Payment PENDING rồi đánh dấu thất bại trực tiếp — registration vẫn PENDING_PAYMENT. */
	private void payFailed(Registration reg) {
		Payment payment = createPendingPayment(reg);
		payment.setStatus(PaymentStatus.FAILED.getValue());
		paymentRepository.save(payment);
	}

	private Payment createPendingPayment(Registration reg) {
		return paymentRepository.save(Payment.builder()
				.user(reg.getUser())
				.registration(reg)
				.amount(reg.getTournament().getEntryFee())
				.paymentMethod("PAYOS")
				.status(PaymentStatus.PENDING.getValue())
				.checkoutUrl("https://seed-data.local/fake-checkout/" + reg.getId())
				.build());
	}

	private Map<Long, Integer> buildPowerMap(Tournament t) {
		List<Participant> participants = participantRepository.findByTournamentId(t.getId());
		Map<Long, Integer> power = new HashMap<>();
		for (int i = 0; i < participants.size(); i++) {
			power.put(participants.get(i).getId(), i);
		}
		return power;
	}

	/** Chỉ hoàn thành các trận vòng 1 — để lại vòng sau ở trạng thái PENDING (giải "đang thi đấu dở"). */
	private void simulateRoundOne(Tournament t, User owner, Map<Long, Integer> power) {
		List<Match> matches = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(t.getId());
		for (Match match : matches) {
			if (match.getRoundNo() == null || match.getRoundNo() != 1) continue;
			if (match.getPlayer1() == null || match.getPlayer2() == null) continue;

			Long p1Id = match.getPlayer1().getId();
			Long p2Id = match.getPlayer2().getId();
			boolean p1Wins = power.getOrDefault(p1Id, 99) < power.getOrDefault(p2Id, 99);
			int raceTo = match.getRaceTo() != null && match.getRaceTo() > 0 ? match.getRaceTo() : 5;
			int loserScore = RND.nextInt(Math.max(1, raceTo));

			match.setPlayer1Score(p1Wins ? raceTo : loserScore);
			match.setPlayer2Score(p1Wins ? loserScore : raceTo);
			matchRepository.save(match);
			matchService.completeMatch(match.getId(), p1Wins ? p1Id : p2Id, false, owner.getId());
		}
	}

	private boolean tournamentExists(String name) {
		return tournamentRepository.findAll().stream().anyMatch(t -> name.equals(t.getName()));
	}
}
