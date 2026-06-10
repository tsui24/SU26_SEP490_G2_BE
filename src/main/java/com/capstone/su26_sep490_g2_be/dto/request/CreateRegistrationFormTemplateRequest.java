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

	@NotBlank
	@Size(max = 50)
	@Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "code must be UPPER_SNAKE_CASE")
	@Schema(example = "SINGLE_PLAYER")
	private String code;

	@NotBlank
	@Size(max = 255)
	private String name;

	private String description;

	private Integer sortOrder;

	private Boolean isActive;
}
