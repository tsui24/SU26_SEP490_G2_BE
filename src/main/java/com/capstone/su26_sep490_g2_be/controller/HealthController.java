package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Health", description = "Kiểm tra trạng thái API")
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

	private final JdbcTemplate jdbcTemplate;

	@Operation(summary = "Health check", description = "Trả về trạng thái UP/DEGRADED của service kèm kết nối DB")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Service đang chạy",
					content = @Content(schema = @Schema(implementation = ApiResponse.class)))
	})
	@GetMapping
	public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
		boolean dbConnected = pingDb();
		Map<String, Object> body = Map.of("status", dbConnected ? "UP" : "DEGRADED", "dbConnected", dbConnected);
		HttpStatus status = dbConnected ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
		return ResponseEntity.status(status).body(ApiResponse.success(body));
	}

	private boolean pingDb() {
		try {
			Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			return result != null;
		} catch (DataAccessException ex) {
			return false;
		}
	}
}
