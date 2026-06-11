package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Cập nhật metadata thể thức")
public class UpdateFormatRequest {

	@NotBlank(message = "Tên thể thức không được để trống")
	private String name;

	@NotBlank(message = "Mô tả không được để trống")
	private String description;

	@NotBlank(message = "Handler key không được để trống")
	private String handlerKey;

	private String schemaVersion;
}
