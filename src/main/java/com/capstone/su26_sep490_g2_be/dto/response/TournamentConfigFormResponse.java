package com.capstone.su26_sep490_g2_be.dto.response;

import com.capstone.su26_sep490_g2_be.enums.FieldSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Form config giải — pre-fill từ Admin default")
public class TournamentConfigFormResponse {

	private Long tournamentId;
	private String tournamentName;
	private String formatCode;
	private String formatName;
	private String formatDescription;
	private String gameType;
	private String seedingMethod;
	private Integer seedCount;
	private Boolean isConfigComplete;
	private List<ConfigFieldItem> fields;
	private List<RaceToRuleItem> raceToRules;
	private List<String> seedingOptions;

	@Getter
	@Builder
	public static class ConfigFieldItem {

		private String fieldKey;
		private String label;
		private String description;
		private String dataType;
		private String uiComponent;
		private List<String> enumOptions;
		private Integer minValue;
		private Integer maxValue;
		private String value;
		private String defaultValue;
		private Boolean isRequired;
		private FieldSource source;
	}

	@Getter
	@Builder
	public static class RaceToRuleItem {

		private String roundKey;
		private String label;
		private String bracketPhase;
		private Integer raceTo;
		private Integer defaultRaceTo;
		private Boolean isOverridden;
		private FieldSource source;
	}
}
