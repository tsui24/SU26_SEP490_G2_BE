package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@Schema(description = "Preview form đăng ký đã resolve")
public class RegistrationFormPreviewResponse {

	private Long templateId;
	private String templateCode;
	private String templateName;
	private String templateDescription;
	private Long tournamentId;
	private String tournamentName;
	private String participantType;
	private BigDecimal entryFee;
	private Boolean isReady;
	private List<FormFieldItem> fields;

	@Getter
	@Builder
	public static class FormFieldItem {
		private String fieldKey;
		private String label;
		private String description;
		private String dataType;
		private String uiComponent;
		private List<String> enumOptions;
		private Integer minValue;
		private Integer maxValue;
		private String placeholder;
		private String validationRegex;
		private String defaultValue;
		private Boolean isRequired;
		private Integer sortOrder;
	}
}
