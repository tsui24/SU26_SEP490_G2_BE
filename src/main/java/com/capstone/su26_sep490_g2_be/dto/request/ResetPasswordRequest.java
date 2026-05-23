package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Reset password sau khi verify OTP")
public class ResetPasswordRequest {

	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	@Schema(example = "user@example.com")
	private String email;

	@NotBlank(message = "OTP is required")
	@Schema(example = "123456")
	private String otp;

	@NotBlank(message = "New password is required")
	@Size(min = 6, max = 100, message = "Password must be 6-100 characters")
	@Schema(example = "NewP@ssw0rd")
	private String newPassword;
}
