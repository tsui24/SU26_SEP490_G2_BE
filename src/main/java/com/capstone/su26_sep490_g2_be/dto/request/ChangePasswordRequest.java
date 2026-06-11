package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Đổi mật khẩu (cần đăng nhập)")
public class ChangePasswordRequest {

	@NotBlank(message = "Mật khẩu hiện tại không được để trống")
	@Schema(example = "OldP@ssw0rd")
	private String oldPassword;

	@NotBlank(message = "Mật khẩu mới không được để trống")
	@Size(min = 6, max = 100, message = "Mật khẩu phải từ 6-100 ký tự")
	@Schema(example = "NewP@ssw0rd")
	private String newPassword;
}
