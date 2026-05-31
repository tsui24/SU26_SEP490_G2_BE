package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Tạo thể thức giải đấu — Wizard màn 1")
public class CreateFormatRequest {

	@NotBlank
	@Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "code must be UPPER_SNAKE_CASE")
	@Schema(example = "SINGLE_ELIMINATION")
	private String code;

	@NotBlank
	private String name;

	@NotBlank
	private String description;

	@NotBlank
	@Schema(example = "pool_single_elimination_handler")
	private String handlerKey;

	@Schema(example = "1.0")
	private String schemaVersion;

	@Schema(description = "Wizard: false cho đến khi activate", example = "false")
	private Boolean isActive;
}
