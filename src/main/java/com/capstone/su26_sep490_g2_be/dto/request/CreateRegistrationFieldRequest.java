package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Tạo field catalog cho form đăng ký")
public class CreateRegistrationFieldRequest {

	@NotBlank
	@Size(max = 80)
	@Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "fieldKey must be lowercase snake_case")
	@Schema(example = "player_full_name")
	private String fieldKey;

	@NotBlank
	@Size(max = 255)
	private String label;

	private String description;

	@NotBlank
	@Schema(example = "STRING")
	private String dataType;

	@NotBlank
	@Schema(example = "TEXT")
	private String uiComponent;

	private List<String> enumOptions;

	private Integer minValue;

	private Integer maxValue;

	private Boolean isActive;
}
