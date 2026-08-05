package com.capstone.su26_sep490_g2_be.config;

import org.springframework.util.AntPathMatcher;

/**
 * Danh sách URL public DUY NHẤT — dùng chung cho {@link SecurityConfig} (authorization rule) và
 * {@link JwtAuthenticationFilter} (bỏ qua bước bắt buộc JWT).
 *
 * <p>Trước đây 2 class này khai báo 2 danh sách riêng biệt, lệch nhau — JwtAuthenticationFilter
 * chỉ so khớp chính xác (không hỗ trợ {@code /**}) và thiếu nhiều pattern so với SecurityConfig,
 * khiến các endpoint lẽ ra public (xem giải đấu, chi nhánh, tin tức, webhook PayOS...) bị JWT
 * filter chặn 401 trước khi tới được rule permitAll của Spring Security.
 */
final class PublicEndpoints {

	static final String[] PATTERNS = {
			"/swagger-ui.html",
			"/swagger-ui/**",
			"/v3/api-docs/**",
			"/v3/api-docs.yaml",
			"/api/v1/auth/login",
			"/api/v1/auth/register",
			"/api/v1/auth/forgot-password",
			"/api/v1/auth/verify-otp",
			"/api/v1/auth/reset-password",
			"/api/v1/health",
			"/api/v1/tournaments",
			"/api/v1/tournaments/**",
			// Hồ sơ cơ thủ công khai — PlayerProfilePage (route public /event/players/**) gọi endpoint
			// này, thiếu pattern ở đây thì khách chưa đăng nhập nhận 401 dù controller là "Public".
			"/api/v1/participants/**",
			"/api/v1/leaderboard",
			"/api/v1/leaderboard/**",
			"/api/v1/branches",
			"/api/v1/branches/**",
			"/api/v1/participants/**",
			"/api/v1/news",
			"/api/v1/news/**",
			"/api/v1/matches/**",
			"/ws",
			"/ws/**",
			"/api/v1/payments/payos/webhook"
	};

	private static final AntPathMatcher MATCHER = new AntPathMatcher();

	private PublicEndpoints() {
	}

	static boolean isPublic(String path) {
		for (String pattern : PATTERNS) {
			if (MATCHER.match(pattern, path)) {
				return true;
			}
		}
		return false;
	}
}
