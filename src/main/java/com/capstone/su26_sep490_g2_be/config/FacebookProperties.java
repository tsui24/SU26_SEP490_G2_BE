package com.capstone.su26_sep490_g2_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.facebook")
public class FacebookProperties {

	private String pageId;

	/**
	 * Nếu dùng System User: đây là System User Token (không hết hạn).
	 * Service sẽ gọi /me/accounts để lấy Page Token động.
	 *
	 * Nếu dùng trực tiếp Page Token (đã exchange): set useSystemUser = false.
	 */
	private String pageAccessToken;

	/**
	 * App ID và App Secret — cần cho exchange short-lived → long-lived token.
	 * Bỏ trống nếu dùng System User.
	 */
	private String appId;

	private String appSecret;

	/**
	 * true  = pageAccessToken là System User Token → tự lấy Page Token qua /me/accounts
	 * false = pageAccessToken là Page Token trực tiếp (manual hoặc đã exchange)
	 */
	private boolean useSystemUser = false;

	private String apiVersion = "v21.0";

	public String getGraphBaseUrl() {
		return "https://graph.facebook.com/" + apiVersion;
	}
}
