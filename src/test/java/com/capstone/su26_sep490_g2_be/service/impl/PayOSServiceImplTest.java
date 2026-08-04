package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.PayOSProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * L1 unit tests for {@link PayOSServiceImpl}.
 *
 * <p>Mirrors the <b>PayOSService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-54 (payment gateway integration).
 *
 * <p>Only the webhook signature check is reachable at L1: {@code createPaymentLink} and
 * {@code getOrderStatus} build their own {@code RestTemplate} and speak HTTP to PayOS, so they
 * belong to L2 against a stubbed gateway. The signature check is the part that matters most
 * anyway — it is what stops a forged callback from marking an unpaid entry as paid.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · PayOSService — UC-54")
class PayOSServiceImplTest {

	private static final String CHECKSUM_KEY = "test-checksum-key";

	@Mock PayOSProperties payOSProperties;

	@InjectMocks PayOSServiceImpl service;

	/** The same HMAC the gateway computes, so a genuine callback can be built in the test. */
	private static String hmacSha256(String data, String key) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		StringBuilder hex = new StringBuilder();
		for (byte b : hash) hex.append(String.format("%02x", b));
		return hex.toString();
	}

	private void givenChecksumKey() {
		lenient().when(payOSProperties.getChecksumKey()).thenReturn(CHECKSUM_KEY);
	}

	/** Fields are signed in alphabetical order: amount, orderCode, status. */
	private static String signedBody(String signature) {
		return "{\"signature\":\"" + signature + "\",\"data\":{"
				+ "\"orderCode\":77,\"amount\":200000,\"status\":\"PAID\"}}";
	}

	@Test
	@DisplayName("TC-001 · A genuine callback from the gateway is accepted")
	void TC001_verifyWebhookSignature_valid() throws Exception {
		givenChecksumKey();
		String signature = hmacSha256("amount=200000&orderCode=77&status=PAID", CHECKSUM_KEY);

		assertTrue(service.verifyWebhookSignature(signedBody(signature)));
	}

	@Test
	@DisplayName("TC-002 · A callback signed with the wrong key is rejected")
	void TC002_verifyWebhookSignature_wrongKey() throws Exception {
		givenChecksumKey();
		String forged = hmacSha256("amount=200000&orderCode=77&status=PAID", "attacker-key");

		assertFalse(service.verifyWebhookSignature(signedBody(forged)),
				"anybody who could forge this could mark an unpaid entry as paid");
	}

	@Test
	@DisplayName("TC-003 · A callback whose amount was altered in transit is rejected")
	void TC003_verifyWebhookSignature_tamperedAmount() throws Exception {
		givenChecksumKey();
		String signature = hmacSha256("amount=200000&orderCode=77&status=PAID", CHECKSUM_KEY);
		String tampered = "{\"signature\":\"" + signature + "\",\"data\":{"
				+ "\"orderCode\":77,\"amount\":1000,\"status\":\"PAID\"}}";

		assertFalse(service.verifyWebhookSignature(tampered));
	}

	@Test
	@DisplayName("TC-004 · The signature does not depend on the order the fields arrive in")
	void TC004_verifyWebhookSignature_fieldOrderIrrelevant() throws Exception {
		givenChecksumKey();
		String signature = hmacSha256("amount=200000&orderCode=77&status=PAID", CHECKSUM_KEY);
		String reordered = "{\"signature\":\"" + signature + "\",\"data\":{"
				+ "\"status\":\"PAID\",\"amount\":200000,\"orderCode\":77}}";

		assertTrue(service.verifyWebhookSignature(reordered),
				"fields are sorted before signing, so JSON key order must not matter");
	}

	@Test
	@DisplayName("TC-005 · Null fields are left out of the signature")
	void TC005_verifyWebhookSignature_nullFieldsSkipped() throws Exception {
		givenChecksumKey();
		String signature = hmacSha256("amount=200000&orderCode=77&status=PAID", CHECKSUM_KEY);
		String withNull = "{\"signature\":\"" + signature + "\",\"data\":{"
				+ "\"orderCode\":77,\"amount\":200000,\"status\":\"PAID\",\"reference\":null}}";

		assertTrue(service.verifyWebhookSignature(withNull),
				"the gateway omits null fields from its own signature, and so must we");
	}

	@Test
	@DisplayName("TC-006 · A callback carrying no signature at all is rejected")
	void TC006_verifyWebhookSignature_missingSignature() {
		givenChecksumKey();

		assertFalse(service.verifyWebhookSignature(
				"{\"data\":{\"orderCode\":77,\"amount\":200000,\"status\":\"PAID\"}}"));
	}

	@Test
	@DisplayName("TC-007 · A callback that is not valid JSON is rejected rather than throwing")
	void TC007_verifyWebhookSignature_malformedBody() {
		assertFalse(service.verifyWebhookSignature("this is not json"),
				"a malformed callback must be refused quietly — the endpoint is public");
	}

	@Test
	@DisplayName("TC-008 · An empty callback body is rejected")
	void TC008_verifyWebhookSignature_emptyBody() {
		assertFalse(service.verifyWebhookSignature(""));
	}

	@Test
	@DisplayName("TC-009 · Signature comparison ignores letter case")
	void TC009_verifyWebhookSignature_caseInsensitive() throws Exception {
		givenChecksumKey();
		String signature = hmacSha256("amount=200000&orderCode=77&status=PAID", CHECKSUM_KEY)
				.toUpperCase();

		assertTrue(service.verifyWebhookSignature(signedBody(signature)),
				"gateways differ on hex casing, and a case mismatch is not a forgery");
	}
}
