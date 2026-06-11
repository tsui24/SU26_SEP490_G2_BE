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

	@NotBlank(message = "Email không được để trống")
	@Email(message = "Định dạng email không hợp lệ")
	@Schema(example = "user@example.com")
	private String email;

	@NotBlank(message = "Mã OTP không được để trống")
	@Schema(example = "123456")
	private String otp;

	@NotBlank(message = "Mật khẩu mới không được để trống")
	@Size(min = 6, max = 100, message = "Mật khẩu phải từ 6-100 ký tự")
	@Schema(example = "NewP@ssw0rd")
	private String newPassword;
}
