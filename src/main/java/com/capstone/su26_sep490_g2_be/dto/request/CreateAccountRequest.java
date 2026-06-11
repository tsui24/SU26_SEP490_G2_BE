package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Tạo tài khoản bởi Admin/Owner — chỉ thông tin đăng nhập, không tạo profile")
public class CreateAccountRequest {

	@NotBlank(message = "Email không được để trống")
	@Email(message = "Định dạng email không hợp lệ")
	@Schema(example = "newuser@example.com")
	private String email;

	@Schema(example = "0912345678")
	private String phone;

	@NotBlank(message = "Mật khẩu không được để trống")
	@Size(min = 6, max = 100, message = "Mật khẩu phải từ 6-100 ký tự")
	@Schema(example = "P@ssw0rd")
	private String password;
}
