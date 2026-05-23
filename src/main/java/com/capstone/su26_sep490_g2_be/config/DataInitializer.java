package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.repository.RoleRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
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
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(String... args) {
		seedRoles();
		seedAdminAccount();
	}

	private void seedRoles() {
		for (RoleCode rc : RoleCode.values()) {
			if (!roleRepository.existsByCode(rc.getCode())) {
				Role role = Role.builder()
						.code(rc.getCode())
						.name(rc.getDisplayName())
						.description("System role: " + rc.getDisplayName())
						.isActive(true)
						.build();
				roleRepository.save(role);
				log.info("Seeded role: {}", rc.getCode());
			}
		}
	}

	private void seedAdminAccount() {
		if (userRepository.existsByEmail(DEFAULT_ADMIN_EMAIL)) {
			return;
		}

		Role adminRole = roleRepository.findByCode(RoleCode.ADMIN.getCode())
				.orElseThrow(() -> new IllegalStateException("ADMIN role not found after seeding"));

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
		log.info("Seeded default admin account (username: admin / password: admin)");
	}
}
