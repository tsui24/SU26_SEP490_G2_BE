package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin tài khoản user")
public class UserResponse {

	@Schema(example = "1")
	private Long id;

	@Schema(example = "user@example.com")
	private String email;

	@Schema(example = "0912345678")
	private String phone;

	@Schema(example = "PLAYER")
	private String role;

	@Schema(example = "ACTIVE")
	private String status;

	@Schema(description = "Đã tạo profile hay chưa (Player cần tạo profile ở bước 2)")
	private boolean profileCompleted;
}
