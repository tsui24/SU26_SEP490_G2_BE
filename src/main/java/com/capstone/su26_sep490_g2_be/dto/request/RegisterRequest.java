package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Đăng ký tài khoản Player — chỉ thông tin đăng nhập, profile tạo ở bước sau")
public class RegisterRequest {

	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	@Schema(example = "player@example.com")
	private String email;

	@Schema(example = "0912345678")
	private String phone;

	@NotBlank(message = "Password is required")
	@Size(min = 6, max = 100, message = "Password must be 6-100 characters")
	@Schema(example = "P@ssw0rd")
	private String password;
}
