package com.capstone.su26_sep490_g2_be.system;

import com.capstone.su26_sep490_g2_be.config.PayOSProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared plumbing for BTMS L3 §3a HTTP-flow chains (TC-SYS-BFxx).
 *
 * <p>The real {@code JwtAuthenticationFilter} unconditionally requires an actual
 * {@code Authorization: Bearer <token>} header and re-validates the user against the DB on
 * every request — {@code SecurityMockMvcRequestPostProcessors.user(...)} does NOT satisfy it
 * (that helper injects a SecurityContext directly, which this filter overwrites/rejects since it
 * runs unconditionally for every non-public path). So every authenticated step in these chains
 * logs in for real via {@code /api/v1/auth/login} and attaches the resulting JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
public abstract class SystemTestBase {

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected PayOSProperties payOSProperties;

	/** Short unique suffix for building fresh, non-colliding fixture names/codes/emails per chain. */
	protected static String uniq() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	/** Same as {@link #uniq()} but uppercase — for codes validated as UPPER_SNAKE_CASE
	 * (game-type code, format code, registration-form-template code). */
	protected static String uniqUpper() {
		return uniq().toUpperCase();
	}

	protected String freshEmail(String localPrefix) {
		return localPrefix + "_" + uniq() + "@test-l3.local";
	}

	/** Fresh VN-format mobile number (regex {@code ^(0[3|5|7|8|9])[0-9]{8}$}) — avoids
	 * AUTH_PHONE_ALREADY_EXISTS collisions across repeated runs of non-@Transactional tests, whose
	 * committed data survives between runs. */
	protected String freshPhone() {
		String digits = String.valueOf(System.nanoTime());
		return "09" + digits.substring(digits.length() - 8);
	}

	/** Registers a brand-new Player account and returns its JWT (BF-01 Step 1+2 combined). */
	protected String registerPlayer(String email, String password, String phone) throws Exception {
		String body = """
				{"email":"%s","password":"%s","phone":"%s"}
				""".formatted(email, password, phone);
		String res = mvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return JsonPath.read(res, "$.data.token");
	}

	/** Logs in an existing account (seeded or created earlier in the same chain) and returns its JWT. */
	protected String login(String email, String password) throws Exception {
		String body = """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
		MvcResult result = mvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andReturn();
		String res = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		Object token = JsonPath.read(res, "$.data.token");
		if (token == null) {
			throw new IllegalStateException("Login failed for " + email + ": " + res);
		}
		return (String) token;
	}

	protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
		return builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
	}

	protected String bodyOf(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	protected <T> T read(String json, String path) {
		return JsonPath.read(json, path);
	}

	/**
	 * Builds a signed PayOS webhook payload matching {@code PayOSServiceImpl.verifyWebhookSignature}:
	 * HMAC-SHA256 over the {@code data} object's fields sorted alphabetically by key, joined as
	 * {@code key=value&key=value}, hex-encoded, using the app's own configured checksum key — so
	 * this works against whatever {@code PAYOS_CHECKSUM_KEY} the running test host has configured,
	 * without hardcoding a secret in test source.
	 */
	protected String payOsWebhookPayload(String code, long orderCode, String reference) {
		TreeMap<String, String> data = new TreeMap<>();
		data.put("orderCode", String.valueOf(orderCode));
		if (reference != null) {
			data.put("reference", reference);
		}
		StringBuilder signedData = new StringBuilder();
		for (Map.Entry<String, String> e : data.entrySet()) {
			if (!signedData.isEmpty()) signedData.append("&");
			signedData.append(e.getKey()).append("=").append(e.getValue());
		}
		String signature = hmacSha256(signedData.toString(), payOSProperties.getChecksumKey());

		StringBuilder dataJson = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, String> e : data.entrySet()) {
			if (!first) dataJson.append(",");
			dataJson.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
			first = false;
		}
		dataJson.append("}");

		return """
				{"code":"%s","desc":"test","data":%s,"signature":"%s"}
				""".formatted(code, dataJson, signature);
	}

	private String hmacSha256(String data, String key) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) hex.append(String.format("%02x", b));
			return hex.toString();
		} catch (Exception e) {
			throw new IllegalStateException("HMAC-SHA256 error", e);
		}
	}
}
