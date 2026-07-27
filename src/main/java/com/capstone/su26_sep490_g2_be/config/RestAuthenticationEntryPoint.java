package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Trả 401 theo đúng format {@link ApiResponse} chung cho các trường hợp Spring Security tự chặn
 * (không đi qua {@link JwtAuthenticationFilter#writeError}) — vd. request không có Authentication
 * trong SecurityContext khi tới rule .authenticated().
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		ErrorCode errorCode = ErrorCode.AUTH_MISSING_TOKEN;
		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		OBJECT_MAPPER.writeValue(response.getWriter(), ApiResponse.error(errorCode));
	}
}
