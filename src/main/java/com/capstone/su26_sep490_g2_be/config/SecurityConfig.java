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

	private static final String[] PUBLIC_URLS = {
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
			"/api/v1/news",
			"/api/v1/news/**",
			"/api/v1/matches/**",
			"/ws",
			"/ws/**",
			"/api/v1/payments/payos/webhook"
	};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_URLS).permitAll()
						.requestMatchers("/api/vi/profile/**").authenticated()

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
