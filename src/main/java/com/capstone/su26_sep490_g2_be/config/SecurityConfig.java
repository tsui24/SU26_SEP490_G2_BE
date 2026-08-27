package com.capstone.su26_sep490_g2_be.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final CorsConfigurationSource corsConfigurationSource;
	private final RestAccessDeniedHandler restAccessDeniedHandler;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(handling -> handling
						.accessDeniedHandler(restAccessDeniedHandler)
						.authenticationEntryPoint(restAuthenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PublicEndpoints.PATTERNS).permitAll()
						// Actuator chỉ dùng nội bộ để đọc MeterRegistry qua AdminDashboardController —
						// không lộ endpoint /actuator/** ra cho bất kỳ role nào, kể cả ADMIN.
						.requestMatchers("/actuator/**").denyAll()
						.requestMatchers("/api/v1/profile/**").authenticated()

						// Shared endpoints (Owner + Manager)
						// Token của Facebook Page dùng chung cho toàn chuỗi — Manager sửa được thì có
						// thể phá vỡ/chiếm quyền đăng bài của mọi chi nhánh khác, nên khoá riêng về OWNER,
						// phải khai TRƯỚC rule /shared/facebook/** rộng hơn để có hiệu lực (match đầu tiên thắng).
						.requestMatchers("/api/v1/shared/facebook/token/**").hasRole("OWNER")
						.requestMatchers("/api/v1/shared/facebook/**").hasAnyRole("OWNER", "MANAGER")
						.requestMatchers("/api/v1/shared/news/**").hasAnyRole("OWNER", "MANAGER")

						// Role-based URL authorization
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/owner/**").hasRole("OWNER")
						.requestMatchers("/api/v1/manager/**").hasRole("MANAGER")
						.requestMatchers("/api/v1/staff/**").hasRole("STAFF")
						.requestMatchers("/api/v1/player/**").hasRole("PLAYER")
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
