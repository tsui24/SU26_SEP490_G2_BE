package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Kết quả upload ảnh lên MinIO")
public class ImageUploadResponse {

	@Schema(description = "Object key trong bucket — lưu vào DB", example = "images/a1b2c3.jpg")
	private String objectKey;

	@Schema(description = "Presigned URL để xem/tải ảnh (hết hạn theo config)")
	private String url;
}
