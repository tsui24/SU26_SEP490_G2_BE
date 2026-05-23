package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request gửi OTP reset password")
public class ForgotPasswordRequest {

	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	@Schema(example = "user@example.com")
	private String email;
}
