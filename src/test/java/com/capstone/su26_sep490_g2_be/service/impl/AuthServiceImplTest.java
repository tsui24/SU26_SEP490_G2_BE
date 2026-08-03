package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.ChangePasswordRequest;
import com.capstone.su26_sep490_g2_be.dto.request.ForgotPasswordRequest;
import com.capstone.su26_sep490_g2_be.dto.request.LoginRequest;
import com.capstone.su26_sep490_g2_be.dto.request.RegisterRequest;
import com.capstone.su26_sep490_g2_be.dto.request.ResetPasswordRequest;
import com.capstone.su26_sep490_g2_be.dto.request.VerifyOtpRequest;
import com.capstone.su26_sep490_g2_be.dto.response.LoginResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegisterResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.RoleRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.EmailService;
import com.capstone.su26_sep490_g2_be.service.OtpService;
import com.capstone.su26_sep490_g2_be.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link AuthServiceImpl}.
 *
 * <p>Mirrors the <b>AuthService</b> sheet in Report 5.1_UnitTests_L1.xlsx one row per test;
 * the case number is embedded in each method name so the two can be traced either way.
 *
 * <p>Spec source: UCS Report 3.1 — UC-01, UC-02, UC-04, UC-06.
 * Then is written against the SPEC, not against what the code happens to do today.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · AuthService — UC-01, UC-02, UC-04, UC-06")
class AuthServiceImplTest {

	@Mock UserRepository userRepository;
	@Mock RoleRepository roleRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock JwtUtil jwtUtil;
	@Mock OtpService otpService;
	@Mock EmailService emailService;

	@InjectMocks AuthServiceImpl authService;

	private static final String EMAIL = "player@example.com";
	private static final String PHONE = "0901234567";
	private static final String RAW_PASSWORD = "Secret@123";
	private static final String HASHED = "$2a$10$hashedvalue";
	private static final String TOKEN = "jwt-token";

	private Role playerRole;

	@BeforeEach
	void setUp() {
		playerRole = Role.builder().id(5L).code("PLAYER").name("Cơ thủ").build();
	}

	private User activeUser() {
		return User.builder()
				.id(1L).email(EMAIL).phone(PHONE)
				.passwordHash(HASHED)
				.role(playerRole)
				.status(UserStatus.ACTIVE)
				.build();
	}

	/** Stubs the dependencies needed for one registration to run all the way through. */
	private void stubSuccessfulRegistration() {
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(roleRepository.findByCode("PLAYER")).thenReturn(Optional.of(playerRole));
		when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED);
		when(userRepository.save(any(User.class))).thenAnswer(inv -> {
			User u = inv.getArgument(0);
			u.setId(1L);
			return u;
		});
		when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn(TOKEN);
	}

	private RegisterRequest registerRequest(String email, String phone, String password) {
		RegisterRequest r = new RegisterRequest();
		r.setEmail(email);
		r.setPhone(phone);
		r.setPassword(password);
		return r;
	}

	private LoginRequest loginRequest(String email, String password) {
		LoginRequest r = new LoginRequest();
		r.setEmail(email);
		r.setPassword(password);
		return r;
	}

	// ══════════════════════ register(RegisterRequest) — UC-01 ══════════════════════

	@Test
	@DisplayName("TC-001 · Valid registration creates an active PLAYER account")
	void TC001_register_happyPath() {
		stubSuccessfulRegistration();

		RegisterResponse response = authService.register(registerRequest(EMAIL, PHONE, RAW_PASSWORD));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository, times(1)).save(saved.capture());
		assertEquals("PLAYER", saved.getValue().getRole().getCode());
		assertEquals(UserStatus.ACTIVE, saved.getValue().getStatus());
		assertEquals(TOKEN, response.getToken());
		assertEquals(EMAIL, response.getUser().getEmail());
	}

	@Test
	@DisplayName("TC-002 · Email already registered is rejected")
	void TC002_register_duplicateEmail_rejected() {
		when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.register(registerRequest(EMAIL, PHONE, RAW_PASSWORD)));

		assertEquals(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS, ex.getErrorCode());
		verify(userRepository, never()).save(any());
		verifyNoInteractions(roleRepository);
	}

	@Test
	@DisplayName("TC-003 · Phone number already registered is rejected")
	void TC003_register_duplicatePhone_rejected() {
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(userRepository.existsByPhone(PHONE)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.register(registerRequest(EMAIL, PHONE, RAW_PASSWORD)));

		assertEquals(ErrorCode.AUTH_PHONE_ALREADY_EXISTS, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-004 · Omitting the phone number skips the uniqueness check")
	void TC004_register_nullPhone_skipsPhoneCheck() {
		stubSuccessfulRegistration();

		authService.register(registerRequest(EMAIL, null, RAW_PASSWORD));

		verify(userRepository, never()).existsByPhone(any());
		verify(userRepository).save(any(User.class));
	}

	@Test
	@DisplayName("TC-005 · Blank phone number skips the uniqueness check")
	void TC005_register_blankPhone_skipsPhoneCheck() {
		stubSuccessfulRegistration();

		authService.register(registerRequest(EMAIL, "   ", RAW_PASSWORD));

		verify(userRepository, never()).existsByPhone(any());
		verify(userRepository).save(any(User.class));
	}

	@Test
	@DisplayName("TC-006 · Missing PLAYER role surfaces as a configuration error")
	void TC006_register_missingPlayerRole_rejected() {
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(userRepository.existsByPhone(PHONE)).thenReturn(false);
		when(roleRepository.findByCode("PLAYER")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.register(registerRequest(EMAIL, PHONE, RAW_PASSWORD)));

		assertEquals(ErrorCode.AUTH_ROLE_NOT_FOUND, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-007 · Password is stored hashed, never in plain text")
	void TC007_register_storesHashedPasswordOnly() {
		stubSuccessfulRegistration();

		authService.register(registerRequest(EMAIL, PHONE, RAW_PASSWORD));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertEquals(HASHED, saved.getValue().getPasswordHash());
		assertNotEquals(RAW_PASSWORD, saved.getValue().getPasswordHash());
		verify(passwordEncoder, times(1)).encode(RAW_PASSWORD);
	}

	@Test
	@DisplayName("TC-008 · Registration creates the account only, not the profile")
	void TC008_register_profileNotCompleted() {
		stubSuccessfulRegistration();

		RegisterResponse response = authService.register(registerRequest(EMAIL, PHONE, RAW_PASSWORD));

		assertFalse(response.getUser().isProfileCompleted());
	}

	// ══════════════════════ login(LoginRequest) — UC-02 ══════════════════════

	@Test
	@DisplayName("TC-009 · Valid credentials issue a token")
	void TC009_login_happyPath() {
		User user = activeUser();
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PASSWORD, HASHED)).thenReturn(true);
		when(jwtUtil.generateToken(1L, EMAIL, "PLAYER")).thenReturn(TOKEN);
		when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

		LoginResponse response = authService.login(loginRequest(EMAIL, RAW_PASSWORD));

		assertEquals(TOKEN, response.getToken());
		assertEquals(3600000L, response.getExpiresIn());
		verify(jwtUtil).generateToken(1L, EMAIL, "PLAYER");
	}

	@Test
	@DisplayName("TC-010 · Unregistered email yields the generic error")
	void TC010_login_unknownEmail_genericError() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.login(loginRequest(EMAIL, RAW_PASSWORD)));

		assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, ex.getErrorCode());
		verifyNoInteractions(jwtUtil);
	}

	@Test
	@DisplayName("TC-011 · Wrong password yields the same generic error, revealing nothing")
	void TC011_login_wrongPassword_sameGenericError() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser()));
		when(passwordEncoder.matches(RAW_PASSWORD, HASHED)).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.login(loginRequest(EMAIL, RAW_PASSWORD)));

		// The same error code as TC-010 — the caller never learns which half failed
		assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, ex.getErrorCode());
		verifyNoInteractions(jwtUtil);
	}

	@Test
	@DisplayName("TC-012 · Locked account reports the locked state explicitly")
	void TC012_login_lockedAccount_rejected() {
		User locked = activeUser();
		locked.setStatus(UserStatus.LOCKED);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(locked));
		when(passwordEncoder.matches(RAW_PASSWORD, HASHED)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.login(loginRequest(EMAIL, RAW_PASSWORD)));

		assertEquals(ErrorCode.AUTH_ACCOUNT_LOCKED, ex.getErrorCode());
		verifyNoInteractions(jwtUtil);
	}

	@Test
	@DisplayName("TC-013 · Deleted account yields the generic error, hiding the deletion")
	void TC013_login_deletedAccount_genericError() {
		User deleted = activeUser();
		deleted.setStatus(UserStatus.DELETED);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(deleted));
		when(passwordEncoder.matches(RAW_PASSWORD, HASHED)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.login(loginRequest(EMAIL, RAW_PASSWORD)));

		// A deliberate contrast with TC-012: DELETED must fall back to the generic code
		assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, ex.getErrorCode());
		assertNotEquals(ErrorCode.AUTH_ACCOUNT_LOCKED, ex.getErrorCode());
	}

	// ══════════════════════ getMe(Long) — UC-02 ══════════════════════

	@Test
	@DisplayName("TC-014 · Returns the current session identity")
	void TC014_getMe_happyPath() {
		User user = activeUser();
		user.setProfile(UserProfile.builder().userId(1L).fullName("Nguyễn Văn A").build());
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		UserResponse response = authService.getMe(1L);

		assertEquals(1L, response.getId());
		assertEquals(EMAIL, response.getEmail());
		assertEquals("PLAYER", response.getRole());
		assertEquals("ACTIVE", response.getStatus());
		assertTrue(response.isProfileCompleted());
	}

	@Test
	@DisplayName("TC-015 · User no longer exists")
	void TC015_getMe_userNotFound() {
		when(userRepository.findById(999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.getMe(999L));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-016 · Account locked mid-session")
	void TC016_getMe_lockedAccount() {
		User locked = activeUser();
		locked.setStatus(UserStatus.LOCKED);
		when(userRepository.findById(1L)).thenReturn(Optional.of(locked));

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.getMe(1L));

		assertEquals(ErrorCode.AUTH_ACCOUNT_LOCKED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-017 · Deleted account is treated as an invalid token")
	void TC017_getMe_deletedAccount() {
		User deleted = activeUser();
		deleted.setStatus(UserStatus.DELETED);
		when(userRepository.findById(1L)).thenReturn(Optional.of(deleted));

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.getMe(1L));

		assertEquals(ErrorCode.AUTH_INVALID_TOKEN, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-018 · Profile not completed yet")
	void TC018_getMe_noProfile() {
		User user = activeUser();
		user.setProfile(null);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		UserResponse response = authService.getMe(1L);

		assertFalse(response.isProfileCompleted());
	}

	// ══════════════════════ forgotPassword — UC-04 ══════════════════════

	@Test
	@DisplayName("TC-019 · Active account receives a generated OTP by email")
	void TC019_forgotPassword_activeAccount_sendsOtp() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser()));
		when(otpService.generateOtp(EMAIL)).thenReturn("123456");

		ForgotPasswordRequest request = new ForgotPasswordRequest();
		request.setEmail(EMAIL);
		authService.forgotPassword(request);

		verify(otpService, times(1)).generateOtp(EMAIL);
		verify(emailService, times(1)).sendOtpEmail(EMAIL, "123456");
	}

	/**
	 * UC-04 AF-01 was reworded on 2026-08-03 (DEF-W0-01 closed by a spec change): instead of a
	 * user-not-found error, the system now returns the same uniform response it gives on the
	 * success path. That keeps an attacker from probing which addresses are registered.
	 */
	@Test
	@DisplayName("TC-020 · Unknown email returns the same uniform response")
	void TC020_forgotPassword_unknownEmail_uniformResponse() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		ForgotPasswordRequest request = new ForgotPasswordRequest();
		request.setEmail(EMAIL);

		assertDoesNotThrow(() -> authService.forgotPassword(request),
				"UC-04 AF-01 requires a uniform response so the caller cannot enumerate accounts");
		verify(otpService, never()).generateOtp(anyString());
		verify(emailService, never()).sendOtpEmail(anyString(), anyString());
	}

	/** UC-04 AF-02, reworded alongside AF-01. See the note on TC-020. */
	@Test
	@DisplayName("TC-021 · Non-active account returns the same uniform response")
	void TC021_forgotPassword_inactiveAccount_uniformResponse() {
		User locked = activeUser();
		locked.setStatus(UserStatus.LOCKED);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(locked));

		ForgotPasswordRequest request = new ForgotPasswordRequest();
		request.setEmail(EMAIL);

		assertDoesNotThrow(() -> authService.forgotPassword(request));
		// No OTP reaches a suspended account, and the response leaks nothing about its state
		verify(otpService, never()).generateOtp(anyString());
		verify(emailService, never()).sendOtpEmail(anyString(), anyString());
	}

	@Test
	@DisplayName("TC-022 · Resend requested too soon does not break the flow")
	void TC022_forgotPassword_resendTooSoon_swallowed() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser()));
		when(otpService.generateOtp(EMAIL))
				.thenThrow(new BusinessException(ErrorCode.AUTH_OTP_RESEND_TOO_SOON));

		ForgotPasswordRequest request = new ForgotPasswordRequest();
		request.setEmail(EMAIL);

		assertDoesNotThrow(() -> authService.forgotPassword(request));
		verify(emailService, never()).sendOtpEmail(anyString(), anyString());
	}

	// ══════════════════════ verifyOtp — UC-04 ══════════════════════

	@Test
	@DisplayName("TC-023 · Valid OTP passes verification")
	void TC023_verifyOtp_validCode() {
		VerifyOtpRequest request = new VerifyOtpRequest();
		request.setEmail(EMAIL);
		request.setOtp("123456");

		assertDoesNotThrow(() -> authService.verifyOtp(request));
		verify(otpService, times(1)).verifyOtp(EMAIL, "123456");
	}

	@Test
	@DisplayName("TC-024 · Wrong or expired OTP is rejected")
	void TC024_verifyOtp_invalidCode() {
		VerifyOtpRequest request = new VerifyOtpRequest();
		request.setEmail(EMAIL);
		request.setOtp("000000");
		org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.AUTH_INVALID_OTP))
				.when(otpService).verifyOtp(EMAIL, "000000");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.verifyOtp(request));

		assertEquals(ErrorCode.AUTH_INVALID_OTP, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-025 · Verification step does NOT consume the OTP")
	void TC025_verifyOtp_doesNotConsumeCode() {
		VerifyOtpRequest request = new VerifyOtpRequest();
		request.setEmail(EMAIL);
		request.setOtp("123456");

		authService.verifyOtp(request);

		verify(otpService).verifyOtp(EMAIL, "123456");
		// Consuming it here would break the resetPassword step that follows
		verify(otpService, never()).verifyAndConsume(anyString(), anyString());
	}

	// ══════════════════════ resetPassword — UC-04 ══════════════════════

	private ResetPasswordRequest resetRequest(String otp, String newPassword) {
		ResetPasswordRequest r = new ResetPasswordRequest();
		r.setEmail(EMAIL);
		r.setOtp(otp);
		r.setNewPassword(newPassword);
		return r;
	}

	@Test
	@DisplayName("TC-026 · Password reset succeeds")
	void TC026_resetPassword_happyPath() {
		User user = activeUser();
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.encode("NewPass@123")).thenReturn("$2a$new");

		authService.resetPassword(resetRequest("123456", "NewPass@123"));

		assertEquals("$2a$new", user.getPasswordHash());
		verify(userRepository, times(1)).save(user);
	}

	@Test
	@DisplayName("TC-027 · Invalid OTP at the reset step changes nothing")
	void TC027_resetPassword_invalidOtp_noChange() {
		org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.AUTH_INVALID_OTP))
				.when(otpService).verifyAndConsume(EMAIL, "000000");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.resetPassword(resetRequest("000000", "NewPass@123")));

		assertEquals(ErrorCode.AUTH_INVALID_OTP, ex.getErrorCode());
		// Ordering matters: the OTP is checked BEFORE the user is loaded
		verify(userRepository, never()).findByEmail(anyString());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-028 · Valid OTP but the account has since been deleted")
	void TC028_resetPassword_userNotFound() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.resetPassword(resetRequest("123456", "NewPass@123")));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-029 · OTP is invalidated on use and cannot be replayed")
	void TC029_resetPassword_consumesOtpAtomically() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser()));
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$new");

		authService.resetPassword(resetRequest("123456", "NewPass@123"));

		// verifyAndConsume checks and deletes in one step, closing the TOCTOU window
		verify(otpService, times(1)).verifyAndConsume(EMAIL, "123456");
		verify(otpService, never()).verifyOtp(anyString(), anyString());
	}

	// ══════════════════════ changePassword — UC-06 ══════════════════════

	private ChangePasswordRequest changeRequest(String oldPassword, String newPassword) {
		ChangePasswordRequest r = new ChangePasswordRequest();
		r.setOldPassword(oldPassword);
		r.setNewPassword(newPassword);
		return r;
	}

	@Test
	@DisplayName("TC-030 · Password change succeeds")
	void TC030_changePassword_happyPath() {
		User user = activeUser();
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PASSWORD, HASHED)).thenReturn(true);
		when(passwordEncoder.encode("NewPass@123")).thenReturn("$2a$new");

		authService.changePassword(EMAIL, changeRequest(RAW_PASSWORD, "NewPass@123"));

		assertEquals("$2a$new", user.getPasswordHash());
		verify(userRepository, times(1)).save(user);
	}

	@Test
	@DisplayName("TC-031 · Wrong current password is rejected")
	void TC031_changePassword_wrongOldPassword_rejected() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser()));
		when(passwordEncoder.matches("SaiRoi@123", HASHED)).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.changePassword(EMAIL, changeRequest("SaiRoi@123", "NewPass@123")));

		assertEquals(ErrorCode.AUTH_WRONG_OLD_PASSWORD, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-032 · No account matches the session identity")
	void TC032_changePassword_userNotFound() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.changePassword(EMAIL, changeRequest(RAW_PASSWORD, "NewPass@123")));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
		verify(passwordEncoder, never()).matches(anyString(), anyString());
	}

	@Test
	@DisplayName("TC-033 · Reusing the current password is permitted")
	void TC033_changePassword_sameAsOld_allowed() {
		User user = activeUser();
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PASSWORD, HASHED)).thenReturn(true);
		when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("$2a$samebutrehashed");

		// BR-06 states plainly that the new password need NOT differ from the old one
		assertDoesNotThrow(() -> authService.changePassword(EMAIL, changeRequest(RAW_PASSWORD, RAW_PASSWORD)));
		verify(userRepository).save(user);
	}

	@Test
	@DisplayName("TC-034 · Changing a password while signed in requires no OTP")
	void TC034_changePassword_doesNotUseOtp() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser()));
		when(passwordEncoder.matches(RAW_PASSWORD, HASHED)).thenReturn(true);
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$new");

		authService.changePassword(EMAIL, changeRequest(RAW_PASSWORD, "NewPass@123"));

		verifyNoInteractions(otpService);
		verifyNoInteractions(emailService);
	}
}
