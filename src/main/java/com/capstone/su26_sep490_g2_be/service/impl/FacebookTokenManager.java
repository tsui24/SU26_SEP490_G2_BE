package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.FacebookProperties;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Quản lý Page Access Token cho Facebook Graph API.
 *
 * Hỗ trợ 2 mode:
 *
 * MODE 1 — System User (khuyên dùng, production):
 *   FB_PAGE_ACCESS_TOKEN = System User Token (không hết hạn)
 *   FB_USE_SYSTEM_USER = true
 *   → Service gọi GET /me/accounts để lấy Page Token động, cache lại.
 *
 * MODE 2 — Page Token trực tiếp (test nhanh):
 *   FB_PAGE_ACCESS_TOKEN = Page Access Token (có thể hết hạn)
 *   FB_USE_SYSTEM_USER = false
 *   → Dùng trực tiếp token trong config. Nếu có App ID + Secret, hỗ trợ exchange.
 */
@Slf4j
@Component
public class FacebookTokenManager {

	private final FacebookProperties fbProps;
	private final WebClient webClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final AtomicReference<String> cachedPageToken = new AtomicReference<>();
	private final AtomicReference<Instant> cachedAt = new AtomicReference<>();

	private static final long CACHE_TTL_SECONDS = 3600;

	public FacebookTokenManager(FacebookProperties fbProps, WebClient.Builder webClientBuilder) {
		this.fbProps = fbProps;
		this.webClient = webClientBuilder.build();
	}

	@PostConstruct
	void init() {
		if (!fbProps.isUseSystemUser()) {
			cachedPageToken.set(fbProps.getPageAccessToken());
			log.info("Facebook: sử dụng Page Access Token trực tiếp từ config");
		} else {
			log.info("Facebook: sử dụng System User Token — sẽ lấy Page Token động qua /me/accounts");
		}
	}

	/**
	 * Lấy Page Access Token hợp lệ. Tự refresh nếu cần.
	 * Ưu tiên token đã exchange runtime (cache) trước config .env.
	 */
	public String getPageAccessToken() {
		String cached = cachedPageToken.get();
		if (cached != null && !cached.isBlank()) {
			if (!fbProps.isUseSystemUser()) {
				return cached;
			}
			Instant lastFetch = cachedAt.get();
			if (lastFetch != null
					&& Instant.now().isBefore(lastFetch.plusSeconds(CACHE_TTL_SECONDS))) {
				return cached;
			}
			return refreshPageToken();
		}

		if (!fbProps.isUseSystemUser()) {
			return fbProps.getPageAccessToken();
		}

		return refreshPageToken();
	}

	/**
	 * Áp dụng Page Token mới vào runtime ngay (sau khi exchange),
	 * không cần restart app / sửa .env ngay lập tức.
	 */
	public void applyPageToken(String pageToken) {
		cachedPageToken.set(pageToken);
		cachedAt.set(Instant.now());
		log.info("Facebook: Page Token đã được áp dụng vào runtime cache");
	}

	/**
	 * Gọi khi nhận lỗi token từ Facebook (error code 190 = expired/invalid).
	 * Xóa cache → lần gọi tiếp sẽ refresh.
	 */
	public void invalidateCache() {
		log.warn("Facebook Page Token cache invalidated — sẽ refresh lần tiếp theo");
		cachedPageToken.set(null);
		cachedAt.set(null);
	}

	/**
	 * Exchange short-lived User Token → long-lived User Token (60 ngày).
	 * Cần App ID + App Secret.
	 */
	public String exchangeForLongLivedToken(String shortLivedToken) {
		validateAppCredentials();

		String url = fbProps.getGraphBaseUrl() + "/oauth/access_token"
				+ "?grant_type=fb_exchange_token"
				+ "&client_id=" + fbProps.getAppId()
				+ "&client_secret=" + fbProps.getAppSecret()
				+ "&fb_exchange_token=" + shortLivedToken;

		try {
			JsonNode response = fetchJson(url);
			String longLivedToken = response.path("access_token").asText();
			long expiresIn = response.path("expires_in").asLong(0);
			log.info("Facebook: exchanged for long-lived token, expires_in={}s (~{}d)",
					expiresIn, expiresIn / 86400);
			return longLivedToken;
		} catch (BusinessException ex) {
			throw ex;
		} catch (Exception ex) {
			log.error("Facebook token exchange failed", ex);
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
					"Facebook token exchange failed: " + ex.getMessage());
		}
	}

	/**
	 * Lấy Page Token không hết hạn từ long-lived User Token.
	 * Dùng sau khi exchange thành công.
	 */
	public String getPageTokenFromUserToken(String longLivedUserToken) {
		String url = fbProps.getGraphBaseUrl() + "/me/accounts"
				+ "?access_token=" + longLivedUserToken;

		JsonNode pages = fetchJson(url).path("data");
		for (JsonNode page : pages) {
			if (fbProps.getPageId().equals(page.path("id").asText())) {
				String pageToken = page.path("access_token").asText();
				log.info("Facebook: got never-expiring Page Token for page '{}'", page.path("name").asText());
				return pageToken;
			}
		}

		throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
				"Không tìm thấy Page ID " + fbProps.getPageId() + " trong danh sách pages");
	}

	/**
	 * Một bước: short-lived token → long-lived → Page Token (không hết hạn).
	 * Trả về Page Token — lưu vào env/DB để dùng lâu dài.
	 */
	public String exchangeToNeverExpiringPageToken(String shortLivedUserToken) {
		String longLived = exchangeForLongLivedToken(shortLivedUserToken);
		String pageToken = getPageTokenFromUserToken(longLived);
		applyPageToken(pageToken);
		return pageToken;
	}

	// ─── internal ────────────────────────────────────────────────────────

	private synchronized String refreshPageToken() {
		String cached = cachedPageToken.get();
		Instant lastFetch = cachedAt.get();
		if (cached != null && lastFetch != null
				&& Instant.now().isBefore(lastFetch.plusSeconds(CACHE_TTL_SECONDS))) {
			return cached;
		}

		log.info("Facebook: refreshing Page Token via /me/accounts...");
		String systemToken = fbProps.getPageAccessToken();
		String url = fbProps.getGraphBaseUrl() + "/me/accounts"
				+ "?access_token=" + systemToken;

		JsonNode pages = fetchJson(url).path("data");
		for (JsonNode page : pages) {
			if (fbProps.getPageId().equals(page.path("id").asText())) {
				String pageToken = page.path("access_token").asText();
				cachedPageToken.set(pageToken);
				cachedAt.set(Instant.now());
				log.info("Facebook: Page Token refreshed for '{}'", page.path("name").asText());
				return pageToken;
			}
		}

		throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
				"System User không có quyền Page ID " + fbProps.getPageId());
	}

	private JsonNode fetchJson(String url) {
		try {
			String body = webClient.get()
					.uri(url)
					.retrieve()
					.bodyToMono(String.class)
					.block();
			return objectMapper.readTree(body);
		} catch (WebClientResponseException ex) {
			log.error("Facebook API error: {} — {}", ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
					"Facebook API error: " + ex.getResponseBodyAsString());
		} catch (Exception ex) {
			log.error("Failed to parse Facebook response", ex);
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
					"Không parse được response từ Facebook");
		}
	}

	private void validateAppCredentials() {
		if (fbProps.getAppId() == null || fbProps.getAppId().isBlank()
				|| fbProps.getAppSecret() == null || fbProps.getAppSecret().isBlank()) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST,
					"Cần cấu hình FB_APP_ID và FB_APP_SECRET để exchange token");
		}
	}
}
