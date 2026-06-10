package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Cập nhật metadata template form đăng ký")
public class UpdateRegistrationFormTemplateRequest {

	@Size(max = 255)
	private String name;

	private String description;

	private Integer sortOrder;
}
