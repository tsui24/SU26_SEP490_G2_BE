package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Health", description = "Kiểm tra trạng thái API")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	@Operation(summary = "Health check", description = "Trả về trạng thái UP của service")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Service đang chạy",
					content = @Content(schema = @Schema(implementation = ApiResponse.class)))
	})
	@GetMapping
	public ResponseEntity<ApiResponse<Map<String, String>>> health() {
		return ResponseEntity.ok(ApiResponse.success(Map.of("status", "UP")));
	}
}
