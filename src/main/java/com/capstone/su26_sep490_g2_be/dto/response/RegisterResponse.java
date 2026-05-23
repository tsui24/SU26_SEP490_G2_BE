package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả đăng ký — có token để chuyển sang màn tạo profile")
public class RegisterResponse {

	@Schema(description = "JWT access token")
	private String token;

	@Schema(description = "Token expire time (milliseconds)")
	private long expiresIn;

	@Schema(description = "Thông tin tài khoản vừa tạo")
	private UserResponse user;
}
