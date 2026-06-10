package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Form chỉnh sửa field của template đăng ký")
public class RegistrationFormTemplateFieldsFormResponse {

	private Long templateId;
	private String templateCode;
	private String templateName;
	private Boolean isReady;
	private List<TemplateFieldFormItem> fields;
	private List<AvailableFieldItem> availableFields;

	@Getter
	@Builder
	public static class TemplateFieldFormItem {
		private Long id;
		private String fieldKey;
		private String label;
		private String description;
		private String dataType;
		private String uiComponent;
		private List<String> enumOptions;
		private Integer minValue;
		private Integer maxValue;
		private String labelOverride;
		private String descriptionOverride;
		private String placeholder;
		private String validationRegex;
		private String defaultValue;
		private Boolean isRequired;
		private Integer sortOrder;
	}

	@Getter
	@Builder
	public static class AvailableFieldItem {
		private String fieldKey;
		private String label;
		private String dataType;
		private String uiComponent;
		private Integer minValue;
		private Integer maxValue;
	}
}
