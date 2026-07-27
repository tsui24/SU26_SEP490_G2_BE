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
@Schema(description = "Lưu danh sách field cho template form đăng ký")
public class UpsertRegistrationFormTemplateFieldsRequest {

	@NotEmpty(message = "Danh sách field không được để trống")
	@Valid
	private List<TemplateFieldItemRequest> fields;

	@Getter
	@Setter
	public static class TemplateFieldItemRequest {

		@NotBlank(message = "Field key không được để trống")
		private String fieldKey;

		private String labelOverride;

		private String descriptionOverride;

		private String placeholder;

		private String validationRegex;

		private String defaultValue;

		private Boolean isRequired;

		private Integer sortOrder;
	}
}
