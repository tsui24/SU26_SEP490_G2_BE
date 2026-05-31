package com.capstone.su26_sep490_g2_be.dto.response;

import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Form config fields — Wizard màn 2")
public class FormatConfigFieldsFormResponse {

	private String formatCode;
	private String formatName;
	private FormatSetupStatus setupStatus;
	private List<FormatConfigFieldFormItemResponse> fields;
	private List<AvailableConfigFieldResponse> availableFields;

	@Getter
	@Builder
	public static class FormatConfigFieldFormItemResponse {
		private String fieldKey;
		private String label;
		private String description;
		private String dataType;
		private String fieldScope;
		private String uiComponent;
		private List<String> enumOptions;
		private Integer minValue;
		private Integer maxValue;
		private String defaultValue;
		private Boolean isRequired;
		private Boolean isVisibleToOwner;
		private Long id;
	}

	@Getter
	@Builder
	public static class AvailableConfigFieldResponse {
		private String fieldKey;
		private String label;
		private String dataType;
		private String fieldScope;
		private String uiComponent;
		private Integer minValue;
		private Integer maxValue;
	}
}
