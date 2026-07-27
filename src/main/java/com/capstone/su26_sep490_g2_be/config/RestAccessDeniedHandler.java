package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Trả 403 theo đúng format {@link ApiResponse} chung — mặc định Spring Security trả body rỗng
 * khi role không đủ quyền (vd. PLAYER gọi endpoint /admin/**), khác với format toàn bộ API còn lại.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		ErrorCode errorCode = ErrorCode.AUTH_ACCESS_DENIED;
		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		OBJECT_MAPPER.writeValue(response.getWriter(), ApiResponse.error(errorCode));
	}
}
