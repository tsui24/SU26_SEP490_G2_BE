package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Tạo template form đăng ký")
public class CreateRegistrationFormTemplateRequest {

	@NotBlank(message = "Mã template không được để trống")
	@Size(max = 50, message = "Mã template tối đa 50 ký tự")
	@Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Mã template phải là chữ hoa dạng UPPER_SNAKE_CASE")
	@Schema(example = "SINGLE_PLAYER")
	private String code;

	@NotBlank(message = "Tên template không được để trống")
	@Size(max = 255, message = "Tên template tối đa 255 ký tự")
	private String name;

	private String description;

	private Integer sortOrder;

	private Boolean isActive;
}
