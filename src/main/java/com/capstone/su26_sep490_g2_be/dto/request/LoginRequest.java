package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Login request")
public class LoginRequest {

	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	@Schema(description = "Email đăng nhập", example = "user@example.com")
	private String email;

	@NotBlank(message = "Password is required")
	@Schema(description = "Mật khẩu", example = "P@ssw0rd")
	private String password;
}
