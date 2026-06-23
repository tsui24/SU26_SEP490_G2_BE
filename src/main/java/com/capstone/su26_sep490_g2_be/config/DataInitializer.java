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
		seedSampleTournaments();
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
		if (tournamentRepository.count() > 0)
			return;

		User owner = userRepository.findByEmail("owner@gmail.com")
				.orElse(null);
		if (owner == null)
			return;

		Instant now = Instant.now();

		record TData(String name, String desc, String gameType, String format,
				String status, int max, BigDecimal fee, BigDecimal prize,
				String prizDesc, Instant regDeadline, Instant start, Instant end,
				boolean isRegister) {
		}

		var list = java.util.List.of(
				new TData(
						"FPT 9-Ball Open 2026",
						"Giải đấu 9-Ball mở rộng — dành cho mọi cơ thủ. Alternate break, race-to-7.",
						"9_BALL", "SINGLE_ELIMINATION", "OPEN_FOR_REGISTRATION", 16,
						BigDecimal.valueOf(200000), BigDecimal.valueOf(8000000),
						"Vô địch 4.000.000đ · Á quân 2.400.000đ · Hạng 3-4 800.000đ",
						now.plus(10, ChronoUnit.DAYS),
						now.plus(20, ChronoUnit.DAYS),
						now.plus(22, ChronoUnit.DAYS),
						true),
				new TData(
						"Giải Loại Kép 8-Ball FPT 2026",
						"Double Elimination 8-Ball — cơ hội thua 1 lần vẫn tiếp tục.",
						"8_BALL", "DOUBLE_ELIMINATION", "REGISTRATION_CLOSED", 16,
						BigDecimal.valueOf(150000), BigDecimal.valueOf(6000000),
						"Vô địch 3.000.000đ · Á quân 1.800.000đ · Hạng 3-4 600.000đ",
						now.minus(2, ChronoUnit.DAYS),
						now.plus(5, ChronoUnit.DAYS),
						now.plus(7, ChronoUnit.DAYS),
						false),
				new TData(
						"Vietnam 9-Ball Masters 2026",
						"Giải đấu đẳng cấp với 32 cơ thủ hàng đầu. Single elimination race-to-9.",
						"9_BALL", "SINGLE_ELIMINATION", "IN_PROGRESS", 32,
						BigDecimal.valueOf(500000), BigDecimal.valueOf(20000000),
						"Vô địch 10.000.000đ · Á quân 5.000.000đ · Hạng 3-4 2.000.000đ · Hạng 5-8 750.000đ",
						now.minus(10, ChronoUnit.DAYS),
						now.minus(3, ChronoUnit.DAYS),
						now.plus(2, ChronoUnit.DAYS),
						false),
				new TData(
						"Giải 10-Ball Đà Nẵng Open 2026",
						"Giải đấu 10-Ball đầu tiên tại miền Trung — vòng bảng kết hợp playoff.",
						"10_BALL", "GROUP_PLAYOFF", "COMPLETED", 16,
						BigDecimal.valueOf(100000), BigDecimal.valueOf(4000000),
						"Vô địch 2.000.000đ · Á quân 1.200.000đ · Hạng 3-4 400.000đ",
						now.minus(30, ChronoUnit.DAYS),
						now.minus(20, ChronoUnit.DAYS),
						now.minus(18, ChronoUnit.DAYS),
						false),
				new TData(
						"CLB FPT — Giải Nội Bộ Q3/2026",
						"Giải đấu nội bộ dành cho thành viên CLB — không tính phí.",
						"8_BALL", "SINGLE_ELIMINATION", "OPEN_FOR_REGISTRATION", 8,
						BigDecimal.ZERO, BigDecimal.valueOf(1000000),
						"Vô địch 600.000đ · Á quân 250.000đ · Hạng 3 150.000đ",
						now.plus(7, ChronoUnit.DAYS),
						now.plus(14, ChronoUnit.DAYS),
						now.plus(14, ChronoUnit.DAYS),
						true));

		int count = 0;
		for (var d : list) {
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
					.prizeDescription(d.prizDesc())
					.registrationDeadline(d.regDeadline())
					.startAt(d.start())
					.endAt(d.end())
					.isRegister(d.isRegister())
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
		log.info("Seeded {} sample tournaments", count);
	}
}
