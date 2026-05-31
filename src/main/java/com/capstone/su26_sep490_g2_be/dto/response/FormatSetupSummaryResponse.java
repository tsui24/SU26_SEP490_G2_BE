package com.capstone.su26_sep490_g2_be.dto.response;

import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Preview setup trước khi activate — Wizard màn 4")
public class FormatSetupSummaryResponse {

	private FormatSummaryItem format;
	private List<ConfigFieldSummaryItem> configFields;
	private List<RaceToRuleSummaryItem> raceToRules;
	private FormatSetupStatus setupStatus;
	private boolean canActivate;
	private List<String> validationErrors;

	@Getter
	@Builder
	public static class FormatSummaryItem {
		private String code;
		private String name;
		private String description;
		private String handlerKey;
		private String schemaVersion;
		private Boolean isActive;
	}

	@Getter
	@Builder
	public static class ConfigFieldSummaryItem {
		private String fieldKey;
		private String label;
		private String defaultValue;
		private Boolean isRequired;
		private Boolean isVisibleToOwner;
	}

	@Getter
	@Builder
	public static class RaceToRuleSummaryItem {
		private String roundKey;
		private String label;
		private String bracketPhase;
		private Integer raceTo;
	}
}
