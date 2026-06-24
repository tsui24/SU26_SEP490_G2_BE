package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.config.bootstrap.DatabaseSeedData;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

	private static final String DEFAULT_ADMIN_EMAIL = "admin@gmail.com";
	private static final String DEFAULT_ADMIN_PASSWORD = "admin";

	private final RoleRepository roleRepository;
	private final UserRepository userRepository;
	private final ConfigFieldDefinitionRepository configFieldRepository;
	private final RegistrationFieldDefinitionRepository registrationFieldRepository;
	private final RegistrationFormTemplateRepository registrationFormTemplateRepository;
	private final RegistrationFormTemplateFieldRepository registrationFormTemplateFieldRepository;
	private final GameTypeDefinitionRepository gameTypeRepository;
	private final TournamentFormatDefinitionRepository formatRepository;
	private final FormatConfigFieldRepository formatConfigFieldRepository;
	private final FormatRaceToRuleRepository formatRaceToRuleRepository;
	private final TournamentRepository tournamentRepository;
	private final TournamentConfigRepository tournamentConfigRepository;
	private final RegistrationRepository registrationRepository;
	private final RegistrationFieldValueRepository registrationFieldValueRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(String... args) {
		seedRoles();
		seedConfigFieldCatalog();
		seedGameTypes();
		seedTournamentFormats();
		seedFormatConfigFields();
		seedFormatRaceToRules();
		seedAdminAccount();
		seedOwnerAccount();
		seedRegistrationTestData();
		seedManyPlayers();
		seedSampleTournaments();
		seedLargeTournamentRegistrations();
		log.info("Database seed completed (Strategy B)");
	}

	private void seedRoles() {
		for (RoleCode rc : RoleCode.values()) {
			if (!roleRepository.existsByCode(rc.getCode())) {
				Role role = Role.builder()
						.code(rc.getCode())
						.name(rc.getDisplayName())
						.description("Vai trò hệ thống: " + rc.getDisplayName())
						.isActive(true)
						.build();
				roleRepository.save(role);
				log.info("Seeded role: {}", rc.getCode());
			}
		}
	}

	private void seedConfigFieldCatalog() {
		int seeded = 0;
		for (ConfigFieldDefinition field : DatabaseSeedData.configFieldCatalog()) {
			if (!configFieldRepository.existsById(field.getFieldKey())) {
				configFieldRepository.save(field);
				seeded++;
			}
		}
		if (seeded > 0) {
			log.info("Seeded config_field_definitions: {} rows", seeded);
		}
	}

	private void seedGameTypes() {
		int seeded = 0;
		for (GameTypeDefinition gameType : DatabaseSeedData.gameTypes()) {
			if (!gameTypeRepository.existsById(gameType.getCode())) {
				gameTypeRepository.save(gameType);
				seeded++;
			}
		}
		if (seeded > 0) {
			log.info("Seeded game_type_definitions: {} rows", seeded);
		}
	}

	private void seedTournamentFormats() {
		int seeded = 0;
		for (TournamentFormatDefinition format : DatabaseSeedData.tournamentFormats()) {
			if (!formatRepository.existsById(format.getCode())) {
				formatRepository.save(format);
				seeded++;
			}
		}
		if (seeded > 0) {
			log.info("Seeded tournament_format_definitions: {} rows", seeded);
		}
	}

	private void seedFormatConfigFields() {
		int seeded = 0;
		for (DatabaseSeedData.FormatConfigFieldSeed seed : DatabaseSeedData.formatConfigFields()) {
			if (formatConfigFieldRepository.existsByFormatCodeAndFieldKey(seed.formatCode(), seed.fieldKey())) {
				continue;
			}
			formatConfigFieldRepository.save(FormatConfigField.builder()
					.formatCode(seed.formatCode())
					.fieldKey(seed.fieldKey())
					.defaultValue(seed.defaultValue())
					.isRequired(true)
					.isVisibleToOwner(seed.visibleToOwner())
					.build());
			seeded++;
		}
		if (seeded > 0) {
			log.info("Seeded format_config_fields: {} rows", seeded);
		}
	}

	private void seedFormatRaceToRules() {
		int seeded = 0;
		for (DatabaseSeedData.FormatRaceToRuleSeed seed : DatabaseSeedData.formatRaceToRules()) {
			if (formatRaceToRuleRepository.findByFormatCodeAndRoundKey(seed.formatCode(), seed.roundKey())
					.isPresent()) {
				continue;
			}
			formatRaceToRuleRepository.save(FormatRaceToRule.builder()
					.formatCode(seed.formatCode())
					.roundKey(seed.roundKey())
					.label(seed.label())
					.bracketPhase(seed.bracketPhase())
					.raceTo(seed.raceTo())
					.build());
			seeded++;
		}
		if (seeded > 0) {
			log.info("Seeded format_race_to_rules: {} rows", seeded);
		}
	}

	private void seedAdminAccount() {
		if (userRepository.existsByEmail(DEFAULT_ADMIN_EMAIL)) {
			return;
		}

		Role adminRole = roleRepository.findByCode(RoleCode.ADMIN.getCode())
				.orElseThrow(() -> new IllegalStateException("Không tìm thấy vai trò ADMIN sau khi seed dữ liệu"));

		User admin = User.builder()
				.email(DEFAULT_ADMIN_EMAIL)
				.passwordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
				.role(adminRole)
				.status(UserStatus.ACTIVE)
				.build();

		UserProfile profile = UserProfile.builder()
				.user(admin)
				.fullName("System Administrator")
				.build();

		admin.setProfile(profile);
		userRepository.save(admin);
		log.info("Seeded default admin account (email: {} / password: {})", DEFAULT_ADMIN_EMAIL,
				DEFAULT_ADMIN_PASSWORD);
	}

	private void seedOwnerAccount() {
		if (userRepository.existsByEmail("owner@gmail.com"))
			return;

		Role ownerRole = roleRepository.findByCode(RoleCode.OWNER.getCode())
				.orElseThrow(() -> new IllegalStateException("OWNER role not found"));

		User owner = User.builder()
				.email("owner@gmail.com")
				.passwordHash(passwordEncoder.encode("owner123"))
				.role(ownerRole)
				.status(UserStatus.ACTIVE)
				.build();
		UserProfile ownerProfile = UserProfile.builder()
				.user(owner)
				.fullName("CLB Bi-a FPT")
				.build();
		owner.setProfile(ownerProfile);
		userRepository.save(owner);
		log.info("Seeded owner account: owner@gmail.com / owner123");
	}

	private void seedRegistrationTestData() {
		seedRegistrationFieldCatalog();
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) {
			return;
		}

		RegistrationFormTemplate template = seedRegistrationFormTemplate(owner);
		seedTestPlayers();
		seedRegistrationTournaments(owner, template);
		seedSampleRegistrations(owner, template);
	}

	private void seedRegistrationFieldCatalog() {
		seedRegistrationField("player_full_name", "Họ và tên", "Nhập họ và tên đầy đủ", "STRING", "TEXT");
		seedRegistrationField("player_phone", "Số điện thoại", "Nhập số điện thoại liên hệ", "PHONE", "PHONE_INPUT");
	}

	private void seedRegistrationField(String fieldKey, String label, String description,
			String dataType, String uiComponent) {
		if (registrationFieldRepository.existsById(fieldKey)) {
			return;
		}
		registrationFieldRepository.save(RegistrationFieldDefinition.builder()
				.fieldKey(fieldKey)
				.label(label)
				.description(description)
				.dataType(dataType)
				.uiComponent(uiComponent)
				.isActive(true)
				.build());
	}

	private RegistrationFormTemplate seedRegistrationFormTemplate(User owner) {
		RegistrationFormTemplate template = registrationFormTemplateRepository.findByCode("PLAYER_REG_BASIC")
				.orElseGet(() -> registrationFormTemplateRepository.save(RegistrationFormTemplate.builder()
						.code("PLAYER_REG_BASIC")
						.name("Form đăng ký cơ bản")
						.description("Dùng để test đăng ký giải đấu")
						.isActive(true)
						.sortOrder(0)
						.createdBy(owner)
						.build()));

		seedTemplateField(template, "player_full_name", "Họ và tên", "Nhập họ tên cơ thủ", "Nguyễn Văn A", 1);
		seedTemplateField(template, "player_phone", "Số điện thoại", "Số điện thoại liên hệ", "0900000000", 2);
		return template;
	}

	private void seedTemplateField(RegistrationFormTemplate template, String fieldKey, String labelOverride,
			String descriptionOverride, String placeholder, int sortOrder) {
		if (registrationFormTemplateFieldRepository.findByTemplateIdAndFieldKey(template.getId(), fieldKey)
				.isPresent()) {
			return;
		}
		registrationFormTemplateFieldRepository.save(RegistrationFormTemplateField.builder()
				.templateId(template.getId())
				.fieldKey(fieldKey)
				.labelOverride(labelOverride)
				.descriptionOverride(descriptionOverride)
				.placeholder(placeholder)
				.isRequired(true)
				.sortOrder(sortOrder)
				.build());
	}

	private void seedTestPlayers() {
		seedPlayer("player1@gmail.com", "player123", "Player One", "0900000001");
		seedPlayer("player2@gmail.com", "player123", "Player Two", "0900000002");
		seedPlayer("player3@gmail.com", "player123", "Player Three", "0900000003");
	}

	private void seedPlayer(String email, String rawPassword, String fullName, String phone) {
		if (userRepository.existsByEmail(email)) {
			return;
		}
		Role playerRole = roleRepository.findByCode(RoleCode.PLAYER.getCode())
				.orElseThrow(() -> new IllegalStateException("PLAYER role not found"));
		User player = User.builder()
				.email(email)
				.phone(phone)
				.passwordHash(passwordEncoder.encode(rawPassword))
				.role(playerRole)
				.status(UserStatus.ACTIVE)
				.build();
		UserProfile profile = UserProfile.builder()
				.user(player)
				.fullName(fullName)
				.displayName(fullName)
				.build();
		player.setProfile(profile);
		userRepository.save(player);
		log.info("Seeded player account: {} / {}", email, rawPassword);
	}

	private void seedRegistrationTournaments(User owner, RegistrationFormTemplate template) {
		seedRegistrationTournamentIfMissing(
				"BTMS Test Registration Free",
				"Giải miễn phí để test submit đăng ký.",
				"9_BALL",
				"SINGLE_ELIMINATION",
				BigDecimal.ZERO,
				BigDecimal.valueOf(1000000),
				8,
				true,
				owner,
				template);

		seedRegistrationTournamentIfMissing(
				"BTMS Test Registration Paid",
				"Giải có phí để test trạng thái PENDING_PAYMENT.",
				"8_BALL",
				"SINGLE_ELIMINATION",
				BigDecimal.valueOf(150000),
				BigDecimal.valueOf(5000000),
				16,
				true,
				owner,
				template);
	}

	private void seedRegistrationTournamentIfMissing(String name, String description, String gameType,
			String format, BigDecimal entryFee, BigDecimal prizePool, int maxParticipants,
			boolean registerOnline, User owner, RegistrationFormTemplate template) {
		if (findTournamentByName(name).isPresent()) {
			return;
		}
		Instant now = Instant.now();
		Tournament tournament = Tournament.builder()
				.name(name)
				.description(description)
				.gameType(gameType)
				.format(format)
				.participantType("SINGLE")
				.status("OPEN_FOR_REGISTRATION")
				.maxParticipants(maxParticipants)
				.entryFee(entryFee)
				.prizePool(prizePool)
				.prizeDescription("Vô địch 50% · Á quân 30% · Hạng 3-4 20%")
				.registrationDeadline(now.plus(5, ChronoUnit.DAYS))
				.startAt(now.plus(7, ChronoUnit.DAYS))
				.endAt(now.plus(8, ChronoUnit.DAYS))
				.isRegister(registerOnline)
				.registrationFormTemplateId(template.getId())
				.createdBy(owner)
				.build();
		tournament = tournamentRepository.save(tournament);

		TournamentConfig config = TournamentConfig.builder()
				.tournament(tournament)
				.formatCode(format)
				.seedingMethod("RANDOM")
				.build();
		tournamentConfigRepository.save(config);
		log.info("Seeded registration tournament: {}", name);
	}

	private void seedSampleRegistrations(User owner, RegistrationFormTemplate template) {
		Tournament paidTournament = findTournamentByName("BTMS Test Registration Paid").orElse(null);
		if (paidTournament == null) {
			return;
		}

		User player1 = userRepository.findByEmail("player1@gmail.com").orElse(null);
		User player2 = userRepository.findByEmail("player2@gmail.com").orElse(null);
		if (player1 != null) {
			seedRegistration(paidTournament, player1, "SINGLE", "Player One", "0900000001",
					"Seed pending payment", "PENDING_PAYMENT", null, null);
		}
		if (player2 != null) {
			seedRegistration(paidTournament, player2, "SINGLE", "Player Two", "0900000002",
					"Seed approved registration", "APPROVED", owner, Instant.now().minus(1, ChronoUnit.HOURS));
		}
	}

	private void seedRegistration(Tournament tournament, User user, String registrationType,
			String fullName, String phone, String note, String status, User approvedBy, Instant approvedAt) {
		if (registrationRepository.existsByTournamentIdAndUserId(tournament.getId(), user.getId())) {
			return;
		}

		Registration registration = Registration.builder()
				.tournament(tournament)
				.user(user)
				.registrationType(registrationType)
				.playerFullName(fullName)
				.playerPhone(phone)
				.note(note)
				.status(status)
				.approvedBy(approvedBy)
				.approvedAt(approvedAt)
				.build();
		registration = registrationRepository.save(registration);

		seedRegistrationFieldValue(registration, "player_full_name", fullName);
		seedRegistrationFieldValue(registration, "player_phone", phone);
		log.info("Seeded registration: tournament={} user={} status={}", tournament.getName(), user.getEmail(), status);
	}

	private void seedRegistrationFieldValue(Registration registration, String fieldKey, String value) {
		RegistrationFieldDefinition field = registrationFieldRepository.findById(fieldKey).orElse(null);
		if (field == null) {
			return;
		}
		RegistrationFieldValueId id = new RegistrationFieldValueId(registration.getId(), fieldKey);
		if (registrationFieldValueRepository.existsById(id)) {
			return;
		}
		registrationFieldValueRepository.save(RegistrationFieldValue.builder()
				.id(id)
				.registration(registration)
				.fieldDefinition(field)
				.value(value)
				.build());
	}

	private java.util.Optional<Tournament> findTournamentByName(String name) {
		return tournamentRepository.findAll().stream()
				.filter(tournament -> name.equals(tournament.getName()))
				.findFirst();
	}

	private void seedSampleTournaments() {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) return;

		Long templateId = registrationFormTemplateRepository.findByCode("PLAYER_REG_BASIC")
				.map(t -> t.getId()).orElse(null);

		Instant now = Instant.now();

		record TData(String name, String desc, String gameType, String format,
				String status, int max, BigDecimal fee, BigDecimal prize,
				String prizeDesc, Instant regDeadline, Instant start, Instant end,
				boolean isRegister) {
		}

		var list = java.util.List.of(
				// ── Giải cũ (16 người) ──────────────────────────────────────────────────
				new TData(
						"FPT 9-Ball Open 2026",
						"Giải đấu 9-Ball mở rộng — dành cho mọi cơ thủ. Alternate break, race-to-7.",
						"9_BALL", "SINGLE_ELIMINATION", "OPEN_FOR_REGISTRATION", 16,
						BigDecimal.valueOf(200000), BigDecimal.valueOf(8000000),
						"Vô địch 4.000.000đ · Á quân 2.400.000đ · Hạng 3-4 800.000đ",
						now.plus(10, ChronoUnit.DAYS), now.plus(20, ChronoUnit.DAYS), now.plus(22, ChronoUnit.DAYS),
						true),
				new TData(
						"Giải Loại Kép 8-Ball FPT 2026",
						"Double Elimination 8-Ball — cơ hội thua 1 lần vẫn tiếp tục.",
						"8_BALL", "DOUBLE_ELIMINATION", "REGISTRATION_CLOSED", 16,
						BigDecimal.valueOf(150000), BigDecimal.valueOf(6000000),
						"Vô địch 3.000.000đ · Á quân 1.800.000đ · Hạng 3-4 600.000đ",
						now.minus(2, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS),
						false),
				new TData(
						"Giải 10-Ball Đà Nẵng Open 2026",
						"Giải đấu 10-Ball đầu tiên tại miền Trung — vòng bảng kết hợp playoff.",
						"10_BALL", "GROUP_PLAYOFF", "COMPLETED", 16,
						BigDecimal.valueOf(100000), BigDecimal.valueOf(4000000),
						"Vô địch 2.000.000đ · Á quân 1.200.000đ · Hạng 3-4 400.000đ",
						now.minus(30, ChronoUnit.DAYS), now.minus(20, ChronoUnit.DAYS), now.minus(18, ChronoUnit.DAYS),
						false),
				new TData(
						"CLB FPT — Giải Nội Bộ Q3/2026",
						"Giải đấu nội bộ dành cho thành viên CLB — không tính phí.",
						"8_BALL", "SINGLE_ELIMINATION", "OPEN_FOR_REGISTRATION", 8,
						BigDecimal.ZERO, BigDecimal.valueOf(1000000),
						"Vô địch 600.000đ · Á quân 250.000đ · Hạng 3 150.000đ",
						now.plus(7, ChronoUnit.DAYS), now.plus(14, ChronoUnit.DAYS), now.plus(14, ChronoUnit.DAYS),
						true),

				// ── Giải 32 cơ thủ ──────────────────────────────────────────────────────
				new TData(
						"Vietnam 9-Ball Masters 2026",
						"Giải đấu đỉnh cao với 32 cơ thủ hàng đầu. Single elimination race-to-9.",
						"9_BALL", "SINGLE_ELIMINATION", "IN_PROGRESS", 32,
						BigDecimal.valueOf(500000), BigDecimal.valueOf(20000000),
						"Vô địch 10.000.000đ · Á quân 5.000.000đ · Hạng 3-4 2.000.000đ · Hạng 5-8 750.000đ",
						now.minus(10, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS),
						false),
				new TData(
						"Hanoi 8-Ball Grand Prix 2026",
						"Giải đấu 8-Ball tại Hà Nội — 32 cơ thủ tranh tài. Race-to-7.",
						"8_BALL", "SINGLE_ELIMINATION", "REGISTRATION_CLOSED", 32,
						BigDecimal.valueOf(300000), BigDecimal.valueOf(15000000),
						"Vô địch 7.500.000đ · Á quân 4.000.000đ · Hạng 3-4 1.750.000đ",
						now.minus(1, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS),
						false),
				new TData(
						"Miền Trung 10-Ball Masters 2026",
						"Giải đấu 10-Ball tại Đà Nẵng — 32 cơ thủ, vòng bảng + playoff.",
						"10_BALL", "GROUP_PLAYOFF", "IN_PROGRESS", 32,
						BigDecimal.valueOf(200000), BigDecimal.valueOf(12000000),
						"Vô địch 6.000.000đ · Á quân 3.000.000đ · Hạng 3-4 1.500.000đ",
						now.minus(5, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
						false),
				new TData(
						"Hà Nội 9-Ball Open 2026",
						"Giải 9-Ball mở rộng tại Hà Nội — 32 cơ thủ, alternate break, race-to-7.",
						"9_BALL", "SINGLE_ELIMINATION", "OPEN_FOR_REGISTRATION", 32,
						BigDecimal.valueOf(250000), BigDecimal.valueOf(14000000),
						"Vô địch 7.000.000đ · Á quân 4.000.000đ · Hạng 3-4 1.500.000đ",
						now.plus(12, ChronoUnit.DAYS), now.plus(20, ChronoUnit.DAYS), now.plus(22, ChronoUnit.DAYS),
						true),
				new TData(
						"FPT 8-Ball Group Stage 2026",
						"Giải 8-Ball nội bộ FPT — 32 cơ thủ, vòng bảng round-robin + knockout.",
						"8_BALL", "GROUP_PLAYOFF", "IN_PROGRESS", 32,
						BigDecimal.valueOf(200000), BigDecimal.valueOf(10000000),
						"Vô địch 5.000.000đ · Á quân 2.500.000đ · Hạng 3-4 1.250.000đ",
						now.minus(7, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
						false),

				// ── Giải 64 cơ thủ ──────────────────────────────────────────────────────
				new TData(
						"Vietnam Open 9-Ball Championship 2026",
						"Giải vô địch 9-Ball lớn nhất năm — 64 cơ thủ, single elimination race-to-9.",
						"9_BALL", "SINGLE_ELIMINATION", "OPEN_FOR_REGISTRATION", 64,
						BigDecimal.valueOf(500000), BigDecimal.valueOf(40000000),
						"Vô địch 20.000.000đ · Á quân 10.000.000đ · Hạng 3-4 5.000.000đ · Hạng 5-8 1.250.000đ",
						now.plus(15, ChronoUnit.DAYS), now.plus(25, ChronoUnit.DAYS), now.plus(27, ChronoUnit.DAYS),
						true),
				new TData(
						"HCMC Double Elimination 9-Ball 2026",
						"Giải đấu double elimination tại TP.HCM — 64 cơ thủ, 2 nhánh thắng/thua.",
						"9_BALL", "DOUBLE_ELIMINATION", "REGISTRATION_CLOSED", 64,
						BigDecimal.valueOf(400000), BigDecimal.valueOf(30000000),
						"Vô địch 15.000.000đ · Á quân 7.500.000đ · Hạng 3-4 3.750.000đ",
						now.minus(2, ChronoUnit.DAYS), now.plus(4, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS),
						false),
				new TData(
						"FPT Double Elimination Cup 2026",
						"FPT Cup — double elimination 8-Ball, 64 cơ thủ tranh tài, race-to-7.",
						"8_BALL", "DOUBLE_ELIMINATION", "OPEN_FOR_REGISTRATION", 64,
						BigDecimal.valueOf(350000), BigDecimal.valueOf(25000000),
						"Vô địch 12.500.000đ · Á quân 6.250.000đ · Hạng 3-4 3.125.000đ",
						now.plus(8, ChronoUnit.DAYS), now.plus(18, ChronoUnit.DAYS), now.plus(21, ChronoUnit.DAYS),
						true),
				new TData(
						"Giải Vô Địch 9-Ball Toàn Quốc 2026",
						"Giải vô địch toàn quốc 9-Ball — 64 cơ thủ tốt nhất cả nước. Đã kết thúc.",
						"9_BALL", "SINGLE_ELIMINATION", "COMPLETED", 64,
						BigDecimal.valueOf(500000), BigDecimal.valueOf(50000000),
						"Vô địch 25.000.000đ · Á quân 12.500.000đ · Hạng 3-4 6.250.000đ · Hạng 5-8 1.562.500đ",
						now.minus(40, ChronoUnit.DAYS), now.minus(30, ChronoUnit.DAYS), now.minus(27, ChronoUnit.DAYS),
						false));

		int count = 0;
		for (var d : list) {
			if (findTournamentByName(d.name()).isPresent()) continue;
			Tournament t = Tournament.builder()
					.name(d.name())
					.description(d.desc())
					.gameType(d.gameType())
					.format(d.format())
					.participantType("SINGLE")
					.status(d.status())
					.maxParticipants(d.max())
					.entryFee(d.fee())
					.prizePool(d.prize())
					.prizeDescription(d.prizeDesc())
					.registrationDeadline(d.regDeadline())
					.startAt(d.start())
					.endAt(d.end())
					.isRegister(d.isRegister())
					.registrationFormTemplateId(d.isRegister() ? templateId : null)
					.createdBy(owner)
					.build();
			t = tournamentRepository.save(t);

			TournamentConfig cfg = TournamentConfig.builder()
					.tournament(t)
					.formatCode(t.getFormat())
					.seedingMethod("RANDOM")
					.build();
			tournamentConfigRepository.save(cfg);
			count++;
		}
		if (count > 0) log.info("Seeded {} sample tournaments", count);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Seed 64 cơ thủ (player1-3 đã được tạo bởi seedTestPlayers)
	// ─────────────────────────────────────────────────────────────────────────
	private void seedManyPlayers() {
		record P(String email, String name, String phone) {}
		var players = java.util.List.of(
				new P("player4@gmail.com",  "Nguyễn Văn Hùng",   "0912000004"),
				new P("player5@gmail.com",  "Trần Minh Tuấn",    "0912000005"),
				new P("player6@gmail.com",  "Lê Hoàng Nam",      "0912000006"),
				new P("player7@gmail.com",  "Phạm Đức Anh",      "0912000007"),
				new P("player8@gmail.com",  "Hoàng Quốc Việt",   "0912000008"),
				new P("player9@gmail.com",  "Vũ Thanh Bình",     "0912000009"),
				new P("player10@gmail.com", "Phan Trọng Khôi",   "0912000010"),
				new P("player11@gmail.com", "Trương Xuân Long",  "0912000011"),
				new P("player12@gmail.com", "Bùi Hữu Phúc",     "0912000012"),
				new P("player13@gmail.com", "Đặng Đình Khoa",    "0912000013"),
				new P("player14@gmail.com", "Đỗ Công Danh",      "0912000014"),
				new P("player15@gmail.com", "Hồ Quang Minh",     "0912000015"),
				new P("player16@gmail.com", "Ngô Thành Trung",   "0912000016"),
				new P("player17@gmail.com", "Dương Bảo Khánh",   "0912000017"),
				new P("player18@gmail.com", "Nguyễn Nhật Huy",   "0912000018"),
				new P("player19@gmail.com", "Trần Tiến Đạt",     "0912000019"),
				new P("player20@gmail.com", "Lê Mạnh Hào",       "0912000020"),
				new P("player21@gmail.com", "Phạm Văn Tài",      "0912000021"),
				new P("player22@gmail.com", "Hoàng Đức Lợi",     "0912000022"),
				new P("player23@gmail.com", "Vũ Minh Phát",      "0912000023"),
				new P("player24@gmail.com", "Phan Quang Thắng",  "0912000024"),
				new P("player25@gmail.com", "Trương Văn Toản",   "0912000025"),
				new P("player26@gmail.com", "Bùi Thanh Liêm",    "0912000026"),
				new P("player27@gmail.com", "Đặng Hoàng Sơn",    "0912000027"),
				new P("player28@gmail.com", "Đỗ Minh Dũng",      "0912000028"),
				new P("player29@gmail.com", "Hồ Văn Phong",      "0912000029"),
				new P("player30@gmail.com", "Ngô Hữu Lộc",       "0912000030"),
				new P("player31@gmail.com", "Dương Công Tâm",    "0912000031"),
				new P("player32@gmail.com", "Nguyễn Quốc Bảo",  "0912000032"),
				new P("player33@gmail.com", "Trần Hoàng Vũ",     "0912000033"),
				new P("player34@gmail.com", "Lê Đức Toàn",       "0912000034"),
				new P("player35@gmail.com", "Phạm Thanh Tùng",   "0912000035"),
				new P("player36@gmail.com", "Hoàng Minh Châu",   "0912000036"),
				new P("player37@gmail.com", "Vũ Đình Kiên",      "0912000037"),
				new P("player38@gmail.com", "Phan Văn Nghĩa",    "0912000038"),
				new P("player39@gmail.com", "Trương Quốc Hải",   "0912000039"),
				new P("player40@gmail.com", "Bùi Công Quý",      "0912000040"),
				new P("player41@gmail.com", "Đặng Nhật Linh",    "0912000041"),
				new P("player42@gmail.com", "Đỗ Hoàng Tùng",     "0912000042"),
				new P("player43@gmail.com", "Hồ Đức Hữu",        "0912000043"),
				new P("player44@gmail.com", "Ngô Minh Lâm",      "0912000044"),
				new P("player45@gmail.com", "Dương Văn Thọ",     "0912000045"),
				new P("player46@gmail.com", "Nguyễn Trọng Toán", "0912000046"),
				new P("player47@gmail.com", "Trần Xuân Phú",     "0912000047"),
				new P("player48@gmail.com", "Lê Bảo Lộc",        "0912000048"),
				new P("player49@gmail.com", "Phạm Hữu Trọng",    "0912000049"),
				new P("player50@gmail.com", "Hoàng Văn Lực",     "0912000050"),
				new P("player51@gmail.com", "Vũ Quốc Duy",       "0912000051"),
				new P("player52@gmail.com", "Phan Đức Tín",      "0912000052"),
				new P("player53@gmail.com", "Trương Minh Hải",   "0912000053"),
				new P("player54@gmail.com", "Bùi Văn Linh",      "0912000054"),
				new P("player55@gmail.com", "Đặng Quang Tuấn",   "0912000055"),
				new P("player56@gmail.com", "Đỗ Trọng Nghĩa",    "0912000056"),
				new P("player57@gmail.com", "Hồ Thanh Phong",    "0912000057"),
				new P("player58@gmail.com", "Ngô Đức Hùng",      "0912000058"),
				new P("player59@gmail.com", "Dương Minh Triết",  "0912000059"),
				new P("player60@gmail.com", "Nguyễn Công Hiếu",  "0912000060"),
				new P("player61@gmail.com", "Trần Hoàng Phát",   "0912000061"),
				new P("player62@gmail.com", "Lê Minh Sang",      "0912000062"),
				new P("player63@gmail.com", "Phạm Đình Luân",    "0912000063"),
				new P("player64@gmail.com", "Hoàng Bảo Thắng",  "0912000064"));
		int count = 0;
		for (var p : players) {
			if (!userRepository.existsByEmail(p.email())) {
				seedPlayer(p.email(), "player123", p.name(), p.phone());
				count++;
			}
		}
		if (count > 0) log.info("Seeded {} extra player accounts (player4–64)", count);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Seed đăng ký cho các giải 32/64 người
	// ─────────────────────────────────────────────────────────────────────────
	private void seedLargeTournamentRegistrations() {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) return;
		Instant approvedAt = Instant.now().minus(2, ChronoUnit.HOURS);

		java.util.List<User> allPlayers = new java.util.ArrayList<>();
		for (int i = 1; i <= 64; i++) {
			userRepository.findByEmail("player" + i + "@gmail.com").ifPresent(allPlayers::add);
		}
		if (allPlayers.isEmpty()) return;

		record RegConfig(String name, int count, String status) {}
		var configs = java.util.List.of(
				new RegConfig("Giải Vô Địch 9-Ball Toàn Quốc 2026",       64, "APPROVED"),
				new RegConfig("HCMC Double Elimination 9-Ball 2026",       64, "APPROVED"),
				new RegConfig("Vietnam 9-Ball Masters 2026",               32, "APPROVED"),
				new RegConfig("Hanoi 8-Ball Grand Prix 2026",              32, "APPROVED"),
				new RegConfig("Miền Trung 10-Ball Masters 2026",           32, "APPROVED"),
				new RegConfig("FPT 8-Ball Group Stage 2026",               32, "APPROVED"),
				new RegConfig("Vietnam Open 9-Ball Championship 2026",     45, "APPROVED"),
				new RegConfig("Hà Nội 9-Ball Open 2026",                   22, "APPROVED"),
				new RegConfig("FPT Double Elimination Cup 2026",           30, "APPROVED"));

		int totalSeeded = 0;
		for (var cfg : configs) {
			Tournament tournament = findTournamentByName(cfg.name()).orElse(null);
			if (tournament == null) continue;
			java.util.List<User> subset = allPlayers.stream().limit(cfg.count()).toList();
			for (User player : subset) {
				seedRegistrationFromUser(tournament, player, cfg.status(), owner, approvedAt);
				totalSeeded++;
			}
		}
		if (totalSeeded > 0) log.info("Seeded {} registrations for large tournaments", totalSeeded);
	}

	private void seedRegistrationFromUser(Tournament tournament, User user,
			String status, User approvedBy, Instant approvedAt) {
		if (registrationRepository.existsByTournamentIdAndUserId(tournament.getId(), user.getId())) {
			return;
		}
		UserProfile profile = user.getProfile();
		String fullName = (profile != null && profile.getFullName() != null) ? profile.getFullName() : user.getEmail();
		String phone = (user.getPhone() != null) ? user.getPhone() : "0900000000";
		seedRegistration(tournament, user, "SINGLE", fullName, phone, null, status, approvedBy, approvedAt);
	}
}
