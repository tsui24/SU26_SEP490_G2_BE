package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Yêu cầu đăng nhập")
public class LoginRequest {

	@NotBlank(message = "Email không được để trống")
	@Email(message = "Định dạng email không hợp lệ")
	@Schema(description = "Email đăng nhập", example = "user@example.com")
	private String email;

	@NotBlank(message = "Mật khẩu không được để trống")
	@Schema(description = "Mật khẩu", example = "P@ssw0rd")
	private String password;
}