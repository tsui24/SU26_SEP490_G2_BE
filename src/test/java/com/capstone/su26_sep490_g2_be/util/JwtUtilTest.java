package com.capstone.su26_sep490_g2_be.util;

import com.capstone.su26_sep490_g2_be.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link JwtUtil}.
 *
 * <p>Mirrors the <b>JwtUtil</b> sheet in Report 5.1_UnitTests_L1.xlsx one row per test.
 * Spec source: UCS Report 3.1 — UC-02 (BR-03), UC-03 (BR-02).
 *
 * <p>This is where "the token carries the user's identity and role and has a fixed expiry"
 * (UC-02 BR-03) and "a token stays technically valid until its expiry" (UC-03 BR-02) are
 * actually proven — {@code AuthServiceImpl} only delegates here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · JwtUtil — UC-02, UC-03")
class JwtUtilTest {

	/** 32-byte keys, Base64 encoded — the minimum length HMAC-SHA256 accepts. */
	private static final String SECRET_A =
			Base64.getEncoder().encodeToString("btms-test-signing-key-number-one".getBytes());
	private static final String SECRET_B =
			Base64.getEncoder().encodeToString("btms-test-signing-key-number-two".getBytes());

	private static final long ONE_HOUR_MS = 3_600_000L;

	@Mock JwtProperties jwtProperties;

	private JwtUtil jwtUtil;

	@BeforeEach
	void setUp() {
		jwtUtil = new JwtUtil(jwtProperties);
	}

	/** Configures the component under test with the given secret and token lifetime. */
	private void configure(String secret, long expirationMs) {
		lenient().when(jwtProperties.getSecret()).thenReturn(secret);
		lenient().when(jwtProperties.getExpirationMs()).thenReturn(expirationMs);
	}

	/** Builds a token with a foreign key so the component under test cannot verify it. */
	private String tokenSignedWithOtherKey() {
		JwtProperties otherProps = org.mockito.Mockito.mock(JwtProperties.class);
		when(otherProps.getSecret()).thenReturn(SECRET_B);
		when(otherProps.getExpirationMs()).thenReturn(ONE_HOUR_MS);
		return new JwtUtil(otherProps).generateToken(1L, "a@b.com", "ADMIN");
	}

	// ═══════════════════ generateToken(userId, email, roleCode) ═══════════════════

	@Test
	@DisplayName("TC-001 · Issued token carries identity and role")
	void TC001_generateToken_carriesIdentityAndRole() {
		configure(SECRET_A, ONE_HOUR_MS);

		String token = jwtUtil.generateToken(1L, "a@b.com", "OWNER");

		Claims claims = jwtUtil.extractClaims(token);
		assertEquals("a@b.com", claims.getSubject());
		assertEquals(1, claims.get("userId", Integer.class));
		assertEquals("OWNER", claims.get("role", String.class));
	}

	@Test
	@DisplayName("TC-002 · Expiry is issue time plus the configured lifetime")
	void TC002_generateToken_expiryMatchesConfiguredLifetime() {
		configure(SECRET_A, ONE_HOUR_MS);

		Claims claims = jwtUtil.extractClaims(jwtUtil.generateToken(1L, "a@b.com", "PLAYER"));

		long lifetime = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
		// JWT timestamps have second granularity, so allow a one-second tolerance
		assertTrue(Math.abs(lifetime - ONE_HOUR_MS) <= 1000,
				"Expected a lifetime of about " + ONE_HOUR_MS + " ms but measured " + lifetime);
	}

	@Test
	@DisplayName("TC-003 · Different accounts receive different tokens")
	void TC003_generateToken_distinctPerAccount() {
		configure(SECRET_A, ONE_HOUR_MS);

		String first = jwtUtil.generateToken(1L, "a@b.com", "PLAYER");
		String second = jwtUtil.generateToken(2L, "b@b.com", "ADMIN");

		assertNotEquals(first, second);
		assertEquals("a@b.com", jwtUtil.extractEmail(first));
		assertEquals("b@b.com", jwtUtil.extractEmail(second));
		assertEquals("ADMIN", jwtUtil.extractRole(second));
	}

	// ═══════════════════ extractEmail / extractRole / extractUserId / extractClaims ═══════════════════

	@Test
	@DisplayName("TC-004 · Reads the email back from a token")
	void TC004_extractEmail_roundTrip() {
		configure(SECRET_A, ONE_HOUR_MS);

		String token = jwtUtil.generateToken(1L, "a@b.com", "PLAYER");

		assertEquals("a@b.com", jwtUtil.extractEmail(token));
	}

	@Test
	@DisplayName("TC-005 · Reads the role back from a token")
	void TC005_extractRole_roundTrip() {
		configure(SECRET_A, ONE_HOUR_MS);

		String token = jwtUtil.generateToken(1L, "a@b.com", "MANAGER");

		// This claim decides which area of the system the caller reaches
		assertEquals("MANAGER", jwtUtil.extractRole(token));
	}

	@Test
	@DisplayName("TC-006 · Reads the user id back from a token")
	void TC006_extractUserId_roundTrip() {
		configure(SECRET_A, ONE_HOUR_MS);

		String token = jwtUtil.generateToken(42L, "a@b.com", "STAFF");

		assertEquals(42L, jwtUtil.extractUserId(token));
	}

	@Test
	@DisplayName("TC-007 · A token signed with a different key is refused")
	void TC007_extractClaims_foreignSignature_rejected() {
		String foreign = tokenSignedWithOtherKey();
		configure(SECRET_A, ONE_HOUR_MS);

		// Without this check anyone could mint themselves an ADMIN token
		assertThrows(JwtException.class, () -> jwtUtil.extractClaims(foreign));
	}

	// ═══════════════════════════ isTokenValid(String) ═══════════════════════════

	@Test
	@DisplayName("TC-008 · A freshly issued token is valid")
	void TC008_isTokenValid_freshToken() {
		configure(SECRET_A, ONE_HOUR_MS);

		String token = jwtUtil.generateToken(1L, "a@b.com", "PLAYER");

		assertTrue(jwtUtil.isTokenValid(token));
	}

	@Test
	@DisplayName("TC-009 · An expired token is rejected")
	void TC009_isTokenValid_expiredToken() {
		// A negative lifetime yields a token that is already past its expiry the moment it is
		// minted — the only way to reach this branch without waiting in real time.
		configure(SECRET_A, -1000L);

		String token = jwtUtil.generateToken(1L, "a@b.com", "PLAYER");

		assertFalse(jwtUtil.isTokenValid(token));
	}

	@Test
	@DisplayName("TC-010 · A token signed with the wrong key is rejected")
	void TC010_isTokenValid_foreignSignature() {
		String foreign = tokenSignedWithOtherKey();
		configure(SECRET_A, ONE_HOUR_MS);

		assertFalse(jwtUtil.isTokenValid(foreign));
	}

	@Test
	@DisplayName("TC-011 · Malformed input is rejected without throwing")
	void TC011_isTokenValid_malformedInput() {
		configure(SECRET_A, ONE_HOUR_MS);

		// A hand-edited Authorization header must not crash the filter chain
		assertFalse(jwtUtil.isTokenValid("not-a-jwt"));
		assertFalse(jwtUtil.isTokenValid(""));
		assertFalse(jwtUtil.isTokenValid(null));
	}

	@Test
	@DisplayName("TC-012 · A token with a tampered payload is rejected")
	void TC012_isTokenValid_tamperedPayload() {
		configure(SECRET_A, ONE_HOUR_MS);
		String token = jwtUtil.generateToken(1L, "a@b.com", "PLAYER");

		// The attack: rewrite "role":"PLAYER" as "role":"ADMIN" and replay the token
		String[] parts = token.split("\\.");
		String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
				"{\"sub\":\"a@b.com\",\"userId\":1,\"role\":\"ADMIN\"}".getBytes());
		String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

		assertFalse(jwtUtil.isTokenValid(tampered));
	}

	// ═══════════════════════════ getExpirationMs() ═══════════════════════════

	@Test
	@DisplayName("TC-013 · Reports the configured token lifetime")
	void TC013_getExpirationMs_returnsConfiguredValue() {
		configure(SECRET_A, 86_400_000L);

		// This is the value LoginResponse.expiresIn hands to the client
		assertEquals(86_400_000L, jwtUtil.getExpirationMs());
	}
}
