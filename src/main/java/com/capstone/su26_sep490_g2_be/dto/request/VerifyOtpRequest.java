package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Verify OTP request")
public class VerifyOtpRequest {

	@NotBlank(message = "Email không được để trống")
	@Email(message = "Định dạng email không hợp lệ")
	@Schema(example = "user@example.com")
	private String email;

	@NotBlank(message = "Mã OTP không được để trống")
	@Schema(example = "123456")
	private String otp;
}
