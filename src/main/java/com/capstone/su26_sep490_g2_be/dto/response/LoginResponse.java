package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login response chứa JWT token")
public class LoginResponse {

	@Schema(description = "JWT access token")
	private String token;

	@Schema(description = "Token expire time (milliseconds)", example = "86400000")
	private long expiresIn;
}
