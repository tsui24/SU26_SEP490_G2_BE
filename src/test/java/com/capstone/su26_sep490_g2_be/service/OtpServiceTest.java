package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L1 unit tests for {@link OtpService}.
 *
 * <p>Mirrors the <b>OtpService</b> sheet in Report 5.1_UnitTests_L1.xlsx one row per test.
 * Spec source: UCS Report 3.1 — UC-04 (BR-03, BR-04).
 *
 * <p>This class keeps its state in an internal map and has nothing to mock, so every test
 * builds a fresh instance — self-contained and independent of execution order.
 */
@DisplayName("L1 · OtpService — UC-04")
class OtpServiceTest {

	private static final String EMAIL = "a@b.com";

	private OtpService otpService;

	@BeforeEach
	void setUp() {
		otpService = new OtpService();
	}

	/** Submits a wrong code {@code times} over, swallowing the exceptions to reach the final check. */
	private void submitWrongCode(int times) {
		for (int i = 0; i < times; i++) {
			try {
				otpService.verifyOtp(EMAIL, "000000");
			} catch (BusinessException ignored) {
				// expected — we are only winding up the failed-attempt counter
			}
		}
	}

	// ═══════════════════════════ generateOtp(String) ═══════════════════════════

	@Test
	@DisplayName("TC-001 · First OTP issued for an email")
	void TC001_generateOtp_firstTime() {
		String otp = otpService.generateOtp(EMAIL);

		assertNotNull(otp);
		assertEquals(6, otp.length());
		assertTrue(otp.matches("\\d{6}"), "OTP must be exactly 6 digits but was: " + otp);
	}

	@Test
	@DisplayName("TC-002 · Resend inside the cooldown window is blocked")
	void TC002_generateOtp_resendWithinCooldown_rejected() {
		String first = otpService.generateOtp(EMAIL);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> otpService.generateOtp(EMAIL));

		assertEquals(ErrorCode.AUTH_OTP_RESEND_TOO_SOON, ex.getErrorCode());
		// The earlier code is not overwritten — the one already delivered by email still works
		assertDoesNotThrow(() -> otpService.verifyOtp(EMAIL, first));
	}

	@Test
	@DisplayName("TC-003 · Email casing variants map to one entry")
	void TC003_generateOtp_emailCaseInsensitive() {
		otpService.generateOtp("a@b.com");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> otpService.generateOtp("A@B.COM"));

		// Without normalisation an attacker flips the casing to sidestep the cooldown
		assertEquals(ErrorCode.AUTH_OTP_RESEND_TOO_SOON, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-004 · Code is always 6 digits even for small random values")
	void TC004_generateOtp_alwaysSixDigits() {
		Set<Integer> lengths = new HashSet<>();
		for (int i = 0; i < 50; i++) {
			String otp = otpService.generateOtp("user" + i + "@b.com");
			lengths.add(otp.length());
			assertTrue(otp.matches("\\d{6}"), "OTP is not in the expected format: " + otp);
		}
		// Classic defect: Integer.toString would render a small value as "42" instead of "000042"
		assertEquals(Set.of(6), lengths);
	}

	// ═══════════════════════════ verifyOtp(String, String) ═══════════════════════════

	@Test
	@DisplayName("TC-005 · Verifying a correct code does NOT consume it")
	void TC005_verifyOtp_doesNotConsume() {
		String otp = otpService.generateOtp(EMAIL);

		assertDoesNotThrow(() -> otpService.verifyOtp(EMAIL, otp));
		// The second call must pass too — the password step that follows still needs this code
		assertDoesNotThrow(() -> otpService.verifyOtp(EMAIL, otp));
	}

	@Test
	@DisplayName("TC-006 · No code was ever requested")
	void TC006_verifyOtp_noCodeIssued() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> otpService.verifyOtp("chuadangky@b.com", "123456"));

		assertEquals(ErrorCode.AUTH_INVALID_OTP, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-007 · Wrong code entered")
	void TC007_verifyOtp_wrongCode() {
		String otp = otpService.generateOtp(EMAIL);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> otpService.verifyOtp(EMAIL, "000000"));

		assertEquals(ErrorCode.AUTH_INVALID_OTP, ex.getErrorCode());
		// The entry survives so the user can retry
		assertDoesNotThrow(() -> otpService.verifyOtp(EMAIL, otp));
	}

	@Test
	@DisplayName("TC-008 · Four wrong attempts stay below the lockout threshold")
	void TC008_verifyOtp_fourWrongAttempts_notLockedYet() {
		String otp = otpService.generateOtp(EMAIL);

		for (int i = 0; i < 4; i++) {
			BusinessException ex = assertThrows(BusinessException.class,
					() -> otpService.verifyOtp(EMAIL, "000000"));
			assertEquals(ErrorCode.AUTH_INVALID_OTP, ex.getErrorCode(),
					"Attempt " + (i + 1) + " must not yet count as exceeding the threshold");
		}
		assertDoesNotThrow(() -> otpService.verifyOtp(EMAIL, otp));
	}

	@Test
	@DisplayName("TC-009 · Fifth wrong attempt hits the threshold and burns the code")
	void TC009_verifyOtp_fifthWrongAttempt_locksCode() {
		otpService.generateOtp(EMAIL);
		submitWrongCode(4);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> otpService.verifyOtp(EMAIL, "000000"));

		assertEquals(ErrorCode.AUTH_OTP_TOO_MANY_ATTEMPTS, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-010 · After lockout even the correct code is refused")
	void TC010_verifyOtp_afterLock_correctCodeAlsoRejected() {
		String otp = otpService.generateOtp(EMAIL);
		submitWrongCode(5);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> otpService.verifyOtp(EMAIL, otp));

		// The entry is gone — the user has to request a fresh code
		assertEquals(ErrorCode.AUTH_INVALID_OTP, ex.getErrorCode());
	}

	// ═══════════════════════ verifyAndConsume(String, String) ═══════════════════════

	@Test
	@DisplayName("TC-011 · Code is consumed on first use")
	void TC011_verifyAndConsume_singleUse() {
		String otp = otpService.generateOtp(EMAIL);

		assertDoesNotThrow(() -> otpService.verifyAndConsume(EMAIL, otp));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> otpService.verifyAndConsume(EMAIL, otp));
		assertEquals(ErrorCode.AUTH_INVALID_OTP, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-012 · Consuming works across email casing variants")
	void TC012_verifyAndConsume_emailCaseInsensitive() {
		String otp = otpService.generateOtp("a@b.com");

		assertDoesNotThrow(() -> otpService.verifyAndConsume("A@B.COM", otp));
	}

	@Test
	@DisplayName("TC-013 · A wrong code is not consumed by mistake")
	void TC013_verifyAndConsume_wrongCodeDoesNotConsume() {
		String otp = otpService.generateOtp(EMAIL);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> otpService.verifyAndConsume(EMAIL, "000000"));
		assertEquals(ErrorCode.AUTH_INVALID_OTP, ex.getErrorCode());

		// A single typo must not destroy a valid code
		assertDoesNotThrow(() -> otpService.verifyAndConsume(EMAIL, otp));
	}

	// ═══════════════════════════ invalidate(String) ═══════════════════════════

	@Test
	@DisplayName("TC-014 · Manual invalidation drops the code")
	void TC014_invalidate_removesCode() {
		String otp = otpService.generateOtp(EMAIL);

		otpService.invalidate(EMAIL);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> otpService.verifyOtp(EMAIL, otp));
		assertEquals(ErrorCode.AUTH_INVALID_OTP, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-015 · Invalidating an email with no code is harmless")
	void TC015_invalidate_unknownEmail_noError() {
		assertDoesNotThrow(() -> otpService.invalidate("khongton@tai.com"));
	}

	// ═══════════════════════ Hết hạn theo thời gian ═══════════════════════

	/**
	 * TC-016 — not runnable at L1.
	 *
	 * <p>{@code OTP_TTL_MS} is a private constant and the code calls {@code Instant.now()} directly,
	 * so the clock cannot be advanced without editing production code or waiting a real 5 minutes.
	 * Verified manually instead: the OTP arrives by email and expires as specified. To automate
	 * this, inject {@link java.time.Clock} into {@link OtpService}. Re-check at L2.
	 */
	@Test
	@Disabled("TC-016: injecting java.time.Clock into OtpService is required to simulate the 5-minute expiry")
	@DisplayName("TC-016 · Code expires after 5 minutes")
	void TC016_verifyOtp_expiredCode() {
		String otp = otpService.generateOtp(EMAIL);
		// The clock would need winding past 5 minutes here

		BusinessException ex = assertThrows(BusinessException.class,
				() -> otpService.verifyOtp(EMAIL, otp));
		assertEquals(ErrorCode.AUTH_OTP_EXPIRED, ex.getErrorCode());
	}
}
