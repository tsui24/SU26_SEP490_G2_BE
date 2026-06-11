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
	private final GameTypeDefinitionRepository gameTypeRepository;
	private final TournamentFormatDefinitionRepository formatRepository;
	private final FormatConfigFieldRepository formatConfigFieldRepository;
	private final FormatRaceToRuleRepository formatRaceToRuleRepository;
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
			if (formatRaceToRuleRepository.findByFormatCodeAndRoundKey(seed.formatCode(), seed.roundKey()).isPresent()) {
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
		log.info("Seeded default admin account (email: {} / password: {})", DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASSWORD);
	}
}
