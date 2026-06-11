package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Kết quả upload ảnh lên MinIO")
public class ImageUploadResponse {

	@Schema(description = "Object key — gửi vào avatarUrl khi POST/PUT profile (lưu DB, không hết hạn)",
			example = "avatars/a1b2c3.jpg")
	private String objectKey;

	@Schema(description = "Presigned URL tạm để preview ngay sau upload (không lưu DB, ~1h hết hạn)")
	private String url;
}
