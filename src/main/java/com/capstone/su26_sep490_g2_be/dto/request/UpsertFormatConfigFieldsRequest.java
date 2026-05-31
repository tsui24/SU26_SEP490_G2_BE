package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Lưu default config fields cho thể thức — Wizard màn 2")
public class UpsertFormatConfigFieldsRequest {

	@NotEmpty
	@Valid
	private List<FormatConfigFieldItemRequest> fields;

	@Getter
	@Setter
	public static class FormatConfigFieldItemRequest {

		@NotBlank
		private String fieldKey;

		@NotBlank
		private String defaultValue;

		private Boolean isRequired;

		private Boolean isVisibleToOwner;
	}
}
