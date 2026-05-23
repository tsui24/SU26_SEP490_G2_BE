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

	@NotBlank(message = "Current password is required")
	@Schema(example = "OldP@ssw0rd")
	private String oldPassword;

	@NotBlank(message = "New password is required")
	@Size(min = 6, max = 100, message = "Password must be 6-100 characters")
	@Schema(example = "NewP@ssw0rd")
	private String newPassword;
}
