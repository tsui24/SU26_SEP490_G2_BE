package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Đăng ký thiết bị nhận thông báo đẩy")
public class RegisterDeviceTokenRequest {

	@NotBlank(message = "Thiếu push token của thiết bị")
	@Size(max = 255, message = "Push token quá dài")
	@Schema(description = "Chuỗi ExponentPushToken[...] do Expo cấp", example = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]")
	private String expoToken;

	@NotBlank(message = "Thiếu nền tảng thiết bị")
	@Size(max = 20)
	@Schema(description = "android hoặc ios", example = "android")
	private String platform;
}
