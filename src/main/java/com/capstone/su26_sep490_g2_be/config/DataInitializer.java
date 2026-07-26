package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.config.bootstrap.DatabaseSeedData;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.BranchStatus;
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

import java.util.List;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

	private static final List<String> REMOVED_CONFIG_FIELD_KEYS =
			List.of("is_show_tournament", "is_public_ratio", "is_register");

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
	private final TournamentConfigValueRepository tournamentConfigValueRepository;
	private final BranchRepository branchRepository;
	private final BranchManagerRepository branchManagerRepository;
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
		cleanupRemovedConfigFields();
		ensureDEConfigFieldsExist();
		ensureFormatConfigFieldsForDE();

		seedAccounts();
		seedBranches();
		seedRegistrationFieldCatalog();
		seedRegistrationFormTemplates();

		log.info("DataInitializer completed — roles, catalog, accounts, branches, registration templates");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Catalog: Roles, Config Fields, Game Types, Formats
	// ══════════════════════════════════════════════════════════════════════

	private void seedRoles() {
		for (RoleCode rc : RoleCode.values()) {
			if (!roleRepository.existsByCode(rc.getCode())) {
				roleRepository.save(Role.builder()
						.code(rc.getCode())
						.name(rc.getDisplayName())
						.description("Vai trò hệ thống: " + rc.getDisplayName())
						.isActive(true)
						.build());
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
		if (seeded > 0) log.info("Seeded config_field_definitions: {} rows", seeded);
	}

	private void seedGameTypes() {
		int seeded = 0;
		for (GameTypeDefinition gameType : DatabaseSeedData.gameTypes()) {
			if (!gameTypeRepository.existsById(gameType.getCode())) {
				gameTypeRepository.save(gameType);
				seeded++;
			}
		}
		if (seeded > 0) log.info("Seeded game_type_definitions: {} rows", seeded);
	}

	private void seedTournamentFormats() {
		int seeded = 0;
		for (TournamentFormatDefinition format : DatabaseSeedData.tournamentFormats()) {
			if (!formatRepository.existsById(format.getCode())) {
				formatRepository.save(format);
				seeded++;
			}
		}
		if (seeded > 0) log.info("Seeded tournament_format_definitions: {} rows", seeded);
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
		if (seeded > 0) log.info("Seeded format_config_fields: {} rows", seeded);
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
		if (seeded > 0) log.info("Seeded format_race_to_rules: {} rows", seeded);
	}

	private void cleanupRemovedConfigFields() {
		tournamentConfigValueRepository.deleteByIdFieldKeyIn(REMOVED_CONFIG_FIELD_KEYS);
		formatConfigFieldRepository.deleteByFieldKeyIn(REMOVED_CONFIG_FIELD_KEYS);
		configFieldRepository.deleteByFieldKeyIn(REMOVED_CONFIG_FIELD_KEYS);
	}

	private void ensureDEConfigFieldsExist() {
		if (!configFieldRepository.existsById("de_mode")) {
			configFieldRepository.save(ConfigFieldDefinition.builder()
					.fieldKey("de_mode")
					.label("Chế độ Double Elimination")
					.description("FULL_DE: DE đến vô địch | CUT_TO_SE: DE đến cutoff rồi chuyển sang SE")
					.dataType("ENUM")
					.fieldScope("KNOCKOUT")
					.uiComponent("RADIO_GROUP")
					.isActive(true)
					.build());
		}
		if (!configFieldRepository.existsById("se_phase_size")) {
			configFieldRepository.save(ConfigFieldDefinition.builder()
					.fieldKey("se_phase_size")
					.label("Số người vào Last X (SE phase)")
					.description("Tổng W+L survivors chuyển vào SE bracket. Phải là lũy thừa 2.")
					.dataType("INT")
					.fieldScope("KNOCKOUT")
					.uiComponent("NUMBER_INPUT")
					.minValue(4)
					.maxValue(256)
					.isActive(true)
					.build());
		}
		if (!configFieldRepository.existsById("playoff_size")) {
			configFieldRepository.save(ConfigFieldDefinition.builder()
					.fieldKey("playoff_size")
					.label("Số cơ thủ vào Playoff")
					.description("Số cơ thủ xếp hạng cao nhất được vào vòng playoff. Phải là lũy thừa 2.")
					.dataType("INT")
					.fieldScope("GROUP")
					.uiComponent("NUMBER_INPUT")
					.minValue(2)
					.maxValue(16)
					.isActive(true)
					.build());
		}
	}

	private void ensureFormatConfigFieldsForDE() {
		linkFormatConfigField("DOUBLE_ELIMINATION", "de_mode", "FULL_DE", 90);
		linkFormatConfigField("DOUBLE_ELIMINATION", "se_phase_size", "64", 91);
	}

	private void linkFormatConfigField(String formatCode, String fieldKey,
			String defaultValue, int sortOrder) {
		if (formatConfigFieldRepository.existsByFormatCodeAndFieldKey(formatCode, fieldKey)) return;
		formatConfigFieldRepository.save(FormatConfigField.builder()
				.formatCode(formatCode)
				.fieldKey(fieldKey)
				.defaultValue(defaultValue)
				.isRequired(false)
				.isVisibleToOwner(true)
				.sortOrder(sortOrder)
				.build());
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Accounts: Admin, Owner, Manager, Staff, Players (16)
	// ══════════════════════════════════════════════════════════════════════

	private void seedAccounts() {
		seedUser("admin@gmail.com", "admin1", RoleCode.ADMIN, "System Administrator", null);
		seedUser("owner@gmail.com", "owner123", RoleCode.OWNER, "CLB Bi-a FPT", null);

		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);

		seedUser("manager@gmail.com", "manager123", RoleCode.MANAGER, "Nguyễn Minh Quản", owner);
		seedUser("staff1@gmail.com", "staff123", RoleCode.STAFF, "Trần Văn Trọng", owner);
		seedUser("staff2@gmail.com", "staff123", RoleCode.STAFF, "Lê Thị Mai", owner);

		String[][] players = {
				{"player1@gmail.com", "Nguyễn Văn Hùng", "0912000001"},
				{"player2@gmail.com", "Trần Minh Tuấn", "0912000002"},
				{"player3@gmail.com", "Lê Hoàng Nam", "0912000003"},
				{"player4@gmail.com", "Phạm Đức Anh", "0912000004"},
				{"player5@gmail.com", "Hoàng Quốc Việt", "0912000005"},
				{"player6@gmail.com", "Vũ Thanh Bình", "0912000006"},
				{"player7@gmail.com", "Phan Trọng Khôi", "0912000007"},
				{"player8@gmail.com", "Trương Xuân Long", "0912000008"},
				{"player9@gmail.com", "Bùi Hữu Phúc", "0912000009"},
				{"player10@gmail.com", "Đặng Đình Khoa", "0912000010"},
				{"player11@gmail.com", "Đỗ Công Danh", "0912000011"},
				{"player12@gmail.com", "Hồ Quang Minh", "0912000012"},
				{"player13@gmail.com", "Ngô Thành Trung", "0912000013"},
				{"player14@gmail.com", "Dương Bảo Khánh", "0912000014"},
				{"player15@gmail.com", "Nguyễn Nhật Huy", "0912000015"},
				{"player16@gmail.com", "Trần Tiến Đạt", "0912000016"},
		};
		for (String[] p : players) {
			seedPlayerUser(p[0], "player123", p[1], p[2]);
		}
	}

	private void seedUser(String email, String rawPassword, RoleCode roleCode, String fullName, User owner) {
		if (userRepository.existsByEmail(email)) return;
		Role role = roleRepository.findByCode(roleCode.getCode())
				.orElseThrow(() -> new IllegalStateException("Role not found: " + roleCode));
		User user = User.builder()
				.email(email)
				.passwordHash(passwordEncoder.encode(rawPassword))
				.role(role)
				.status(UserStatus.ACTIVE)
				.owner(owner)
				.build();
		UserProfile profile = UserProfile.builder()
				.user(user)
				.fullName(fullName)
				.build();
		user.setProfile(profile);
		userRepository.save(user);
		log.info("Seeded account: {} / {} ({})", email, rawPassword, roleCode.getCode());
	}

	private void seedPlayerUser(String email, String rawPassword, String fullName, String phone) {
		if (userRepository.existsByEmail(email)) return;
		Role role = roleRepository.findByCode(RoleCode.PLAYER.getCode())
				.orElseThrow(() -> new IllegalStateException("PLAYER role not found"));
		User player = User.builder()
				.email(email)
				.phone(phone)
				.passwordHash(passwordEncoder.encode(rawPassword))
				.role(role)
				.status(UserStatus.ACTIVE)
				.build();
		UserProfile profile = UserProfile.builder()
				.user(player)
				.fullName(fullName)
				.displayName(fullName)
				.build();
		player.setProfile(profile);
		userRepository.save(player);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Branches: 2 chi nhánh cho owner, gán manager + staff
	// ══════════════════════════════════════════════════════════════════════

	private void seedBranches() {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) return;

		Branch branch1 = seedBranch(owner,
				"CLB Bi-a FPT — Cơ sở 1",
				"Lô E2a-7, Đường D1, TP. Thủ Đức, TP.HCM",
				"0901000001",
				"Chi nhánh chính — 10 bàn Pool.");

		Branch branch2 = seedBranch(owner,
				"CLB Bi-a FPT — Cơ sở 2",
				"Phố Trịnh Văn Bô, Nam Từ Liêm, Hà Nội",
				"0901000002",
				"Chi nhánh Hà Nội — 8 bàn Pool.");

		User manager = userRepository.findByEmail("manager@gmail.com").orElse(null);
		if (manager != null) {
			assignManagerToBranch(branch1, manager, owner);
			assignManagerToBranch(branch2, manager, owner);
		}

		User staff1 = userRepository.findByEmail("staff1@gmail.com").orElse(null);
		if (staff1 != null && staff1.getBranch() == null) {
			staff1.setBranch(branch1);
			userRepository.save(staff1);
		}

		User staff2 = userRepository.findByEmail("staff2@gmail.com").orElse(null);
		if (staff2 != null && staff2.getBranch() == null) {
			staff2.setBranch(branch2);
			userRepository.save(staff2);
		}
	}

	private Branch seedBranch(User owner, String name, String address, String phone, String description) {
		List<Branch> existing = branchRepository.findByOwnerId(owner.getId());
		for (Branch b : existing) {
			if (name.equals(b.getName())) return b;
		}
		Branch branch = branchRepository.save(Branch.builder()
				.name(name)
				.address(address)
				.phone(phone)
				.description(description)
				.status(BranchStatus.ACTIVE)
				.owner(owner)
				.build());
		log.info("Seeded branch: {}", name);
		return branch;
	}

	private void assignManagerToBranch(Branch branch, User manager, User owner) {
		if (branch == null) return;
		if (branchManagerRepository.existsByBranchIdAndManagerId(branch.getId(), manager.getId())) return;
		branchManagerRepository.save(BranchManager.builder()
				.branch(branch)
				.manager(manager)
				.assignedBy(owner)
				.build());
		log.info("Assigned manager {} to branch {}", manager.getEmail(), branch.getName());
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Registration Form Templates
	// ══════════════════════════════════════════════════════════════════════

	private void seedRegistrationFieldCatalog() {
		seedRegistrationField("player_full_name", "Họ và tên", "Nhập họ và tên đầy đủ", "STRING", "TEXT");
		seedRegistrationField("player_phone", "Số điện thoại", "Nhập số điện thoại liên hệ", "PHONE", "PHONE_INPUT");
		seedRegistrationField("player2_full_name", "Họ và tên người chơi 2", "Nhập họ và tên đồng đội", "STRING", "TEXT");
		seedRegistrationField("player2_phone", "Số điện thoại người chơi 2", "Nhập số điện thoại đồng đội", "PHONE", "PHONE_INPUT");
	}

	private void seedRegistrationField(String fieldKey, String label, String description,
			String dataType, String uiComponent) {
		if (registrationFieldRepository.existsById(fieldKey)) return;
		registrationFieldRepository.save(RegistrationFieldDefinition.builder()
				.fieldKey(fieldKey)
				.label(label)
				.description(description)
				.dataType(dataType)
				.uiComponent(uiComponent)
				.isActive(true)
				.build());
	}

	private void seedRegistrationFormTemplates() {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) return;

		RegistrationFormTemplate basic = registrationFormTemplateRepository.findByCode("PLAYER_REG_BASIC")
				.orElseGet(() -> registrationFormTemplateRepository.save(RegistrationFormTemplate.builder()
						.code("PLAYER_REG_BASIC")
						.name("Form đăng ký cơ bản")
						.description("Form đăng ký giải đấu đơn")
						.isActive(true)
						.sortOrder(0)
						.createdBy(owner)
						.build()));
		seedTemplateField(basic, "player_full_name", "Họ và tên", "Nhập họ tên cơ thủ", "Nguyễn Văn A", 1);
		seedTemplateField(basic, "player_phone", "Số điện thoại", "Số điện thoại liên hệ", "0900000000", 2);

		RegistrationFormTemplate doubles = registrationFormTemplateRepository.findByCode("PLAYER_REG_DOUBLES")
				.orElseGet(() -> registrationFormTemplateRepository.save(RegistrationFormTemplate.builder()
						.code("PLAYER_REG_DOUBLES")
						.name("Form đăng ký đôi")
						.description("Form đăng ký giải đôi — 1 người điền thông tin cho cả 2")
						.isActive(true)
						.sortOrder(1)
						.createdBy(owner)
						.build()));
		seedTemplateField(doubles, "player_full_name", "Họ tên người chơi 1", "Họ tên của bạn (đội trưởng)", "Nguyễn Văn A", 1);
		seedTemplateField(doubles, "player_phone", "SĐT người chơi 1", "Số điện thoại của bạn", "0900000000", 2);
		seedTemplateField(doubles, "player2_full_name", "Họ tên người chơi 2", "Họ tên đồng đội", "Trần Văn B", 3);
		seedTemplateField(doubles, "player2_phone", "SĐT người chơi 2", "Số điện thoại đồng đội", "0900000001", 4);
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
}
