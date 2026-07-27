package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final JwtUtil jwtUtil;
	private final UserRepository userRepository;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return PublicEndpoints.isPublic(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
									HttpServletResponse response,
									FilterChain filterChain)
			throws ServletException, IOException {

		String header = request.getHeader(AUTHORIZATION_HEADER);

		if (header == null || header.isBlank() || !header.startsWith(BEARER_PREFIX)) {
			writeError(response, ErrorCode.AUTH_MISSING_TOKEN);
			return;
		}

		String token = header.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) {
			writeError(response, ErrorCode.AUTH_MISSING_TOKEN);
			return;
		}

		if (!jwtUtil.isTokenValid(token)) {
			writeError(response, ErrorCode.AUTH_INVALID_TOKEN);
			return;
		}

		String email = jwtUtil.extractEmail(token);
		String role = jwtUtil.extractRole(token);
		Long userId = jwtUtil.extractUserId(token);

		// Token còn hạn nhưng tài khoản có thể vừa bị khoá/xoá sau khi token được cấp — JWT tự thân
		// không mang trạng thái mới nhất, nên phải xác nhận lại với DB trên MỌI request thay vì chỉ
		// ở vài endpoint riêng lẻ (login/getMe), nếu không tài khoản LOCKED vẫn dùng token cũ được
		// tới khi token hết hạn (mặc định 24h).
		if (userId == null || userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE).isEmpty()) {
			writeError(response, ErrorCode.AUTH_ACCOUNT_LOCKED);
			return;
		}

		var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
		var authentication = new UsernamePasswordAuthenticationToken(email, userId, authorities);
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

		SecurityContextHolder.getContext().setAuthentication(authentication);
		filterChain.doFilter(request, response);
	}

	private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		OBJECT_MAPPER.writeValue(response.getWriter(), ApiResponse.error(errorCode));
	}
}
