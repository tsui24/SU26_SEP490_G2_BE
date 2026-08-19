package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.ChangePasswordRequest;
import com.capstone.su26_sep490_g2_be.dto.request.ForgotPasswordRequest;
import com.capstone.su26_sep490_g2_be.dto.request.LoginRequest;
import com.capstone.su26_sep490_g2_be.dto.request.RegisterRequest;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.repository.RoleRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — UC-02 (Login), UC-01/03 tương ứng của UCS Report 3, và GB-01/GB-02 của gbrs_nfse.md.
 *
 * <p>{@code /api/v1/auth/**} (trừ {@code /me} và {@code /change-password}) nằm trong
 * {@code PublicEndpoints}, nên JwtAuthenticationFilter không chặn — request không header
 * Authorization vẫn tới được Controller/Service như user thật chưa đăng nhập.
 */
class AuthControllerL2Test extends AbstractControllerIntegrationTest {

	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private RoleRepository roleRepository;

	// ── POST /auth/login ──────────────────────────────────────────────────

	@Test
	void login_validCredentials_ok() throws Exception {
		LoginRequest req = new LoginRequest();
		req.setEmail(TestAccounts.PLAYER1_EMAIL);
		req.setPassword(TestAccounts.PLAYER1_PASSWORD);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.token").isNotEmpty())
				.andExpect(jsonPath("$.data.expiresIn").value(86400000));
	}

	@Test
	void login_wrongPassword_and_unknownEmail_returnSameGenericMessage() throws Exception {
		// GB-02: thông báo lỗi PHẢI giống hệt nhau giữa "sai email" và "sai mật khẩu" — chống dò email.
		LoginRequest wrongPassword = new LoginRequest();
		wrongPassword.setEmail(TestAccounts.PLAYER1_EMAIL);
		wrongPassword.setPassword(TestAccounts.COMMON_PASSWORD_WRONG);

		LoginRequest unknownEmail = new LoginRequest();
		unknownEmail.setEmail("khong-ton-tai-" + System.nanoTime() + "@gmail.com");
		unknownEmail.setPassword(TestAccounts.COMMON_PASSWORD_WRONG);

		String bodyWrongPassword = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(wrongPassword)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_001"))
				.andReturn().getResponse().getContentAsString();

		String bodyUnknownEmail = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(unknownEmail)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_001"))
				.andReturn().getResponse().getContentAsString();

		JsonNode a = objectMapper.readTree(bodyWrongPassword);
		JsonNode b = objectMapper.readTree(bodyUnknownEmail);
		assertEquals(a.get("message").asText(), b.get("message").asText(),
				"GB-02: message phải giống nhau giữa sai mật khẩu và email không tồn tại");
	}

	@Test
	void login_lockedAccount_rejected403() throws Exception {
		// GB-01: LOCKED không được đăng nhập dù mật khẩu đúng. DataInitializer chỉ seed ACTIVE nên
		// dựng riêng 1 tài khoản LOCKED trong test — @Transactional tự rollback sau khi chạy xong.
		Role playerRole = roleRepository.findByCode("PLAYER").orElseThrow();
		String email = "locked-" + System.nanoTime() + "@gmail.com";
		userRepository.save(User.builder()
				.email(email)
				.phone(null)
				.passwordHash(passwordEncoder.encode("Locked@123"))
				.role(playerRole)
				.status(UserStatus.LOCKED)
				.build());

		LoginRequest req = new LoginRequest();
		req.setEmail(email);
		req.setPassword("Locked@123");

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_008"));
	}

	@Test
	void login_blankEmail_validationError400() throws Exception {
		LoginRequest req = new LoginRequest();
		req.setEmail("");
		req.setPassword("whatever");

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void login_malformedEmail_validationError400() throws Exception {
		LoginRequest req = new LoginRequest();
		req.setEmail("not-an-email");
		req.setPassword("whatever");

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	// ── GET /auth/me ───────────────────────────────────────────────────────

	@Test
	void me_withValidToken_returnsOwnAccount() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(TestAccounts.OWNER_EMAIL))
				.andExpect(jsonPath("$.data.role").value("OWNER"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}

	@Test
	void me_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_013"));
	}

	@Test
	void me_withGarbageToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer this-is-not-a-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_007"));
	}

	// ── POST /auth/register ──────────────────────────────────────────────

	@Test
	void register_newEmail_created201_asPlayerRole() throws Exception {
		RegisterRequest req = new RegisterRequest();
		String email = "new-player-" + System.nanoTime() + "@gmail.com";
		req.setEmail(email);
		req.setPhone("0912345678");
		req.setPassword("Passw0rd");

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.user.email").value(email))
				.andExpect(jsonPath("$.data.user.role").value("PLAYER"))
				.andExpect(jsonPath("$.data.user.profileCompleted").value(false))
				.andExpect(jsonPath("$.data.token").isNotEmpty());

		assertTrue(userRepository.existsByEmail(email));
	}

	@Test
	void register_duplicateEmail_rejected409() throws Exception {
		RegisterRequest req = new RegisterRequest();
		req.setEmail(TestAccounts.PLAYER1_EMAIL); // đã seed sẵn
		req.setPhone("0912345699");
		req.setPassword("Passw0rd");

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AUTH_002"));
	}

	@Test
	void register_invalidPhoneFormat_rejected400() throws Exception {
		RegisterRequest req = new RegisterRequest();
		req.setEmail("phone-invalid-" + System.nanoTime() + "@gmail.com");
		req.setPhone("123"); // không khớp regex 0[3579]xxxxxxxx
		req.setPassword("Passw0rd");

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void register_passwordTooShort_rejected400() throws Exception {
		RegisterRequest req = new RegisterRequest();
		req.setEmail("short-pwd-" + System.nanoTime() + "@gmail.com");
		req.setPhone("0912345678");
		req.setPassword("123"); // min=6

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	// ── POST /auth/forgot-password ──────────────────────────────────────
	// GB-02-style: không lộ email có tồn tại hay không -> luôn 200 dù email có thật hay không.

	@Test
	void forgotPassword_unknownEmail_stillReturnsOk_noEnumeration() throws Exception {
		ForgotPasswordRequest req = new ForgotPasswordRequest();
		req.setEmail("khong-ton-tai-" + System.nanoTime() + "@gmail.com");

		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void forgotPassword_invalidEmailFormat_rejected400() throws Exception {
		ForgotPasswordRequest req = new ForgotPasswordRequest();
		req.setEmail("not-an-email");

		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	// ── POST /auth/change-password (yêu cầu đăng nhập) ────────────────────

	@Test
	void changePassword_withoutToken_rejected401() throws Exception {
		ChangePasswordRequest req = new ChangePasswordRequest();
		req.setOldPassword("whatever");
		req.setNewPassword("NewPassw0rd");

		mockMvc.perform(post("/api/v1/auth/change-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void changePassword_wrongOldPassword_rejected400() throws Exception {
		ChangePasswordRequest req = new ChangePasswordRequest();
		req.setOldPassword(TestAccounts.COMMON_PASSWORD_WRONG);
		req.setNewPassword("NewPassw0rd");

		mockMvc.perform(post("/api/v1/auth/change-password")
						.header("Authorization", bearerToken(TestAccounts.PLAYER2_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AUTH_003"));
	}

	@Test
	void changePassword_correctOldPassword_updatesHash() throws Exception {
		ChangePasswordRequest req = new ChangePasswordRequest();
		req.setOldPassword("player123");
		req.setNewPassword("NewPassw0rd1");

		mockMvc.perform(post("/api/v1/auth/change-password")
						.header("Authorization", bearerToken(TestAccounts.PLAYER3_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		User reloaded = userRepository.findByEmail(TestAccounts.PLAYER3_EMAIL).orElseThrow();
		assertTrue(passwordEncoder.matches("NewPassw0rd1", reloaded.getPasswordHash()));
		assertFalse(passwordEncoder.matches("player123", reloaded.getPasswordHash()));
	}
}
