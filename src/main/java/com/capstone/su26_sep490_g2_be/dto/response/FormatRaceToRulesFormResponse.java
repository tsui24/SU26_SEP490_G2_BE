package com.capstone.su26_sep490_g2_be.dto.response;

import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Race-to rules form — Wizard màn 3")
public class FormatRaceToRulesFormResponse {

	private String formatCode;
	private FormatSetupStatus setupStatus;
	private List<FormatRaceToRuleItemResponse> rules;

	@Getter
	@Builder
	public static class FormatRaceToRuleItemResponse {
		private Long id;
		private String roundKey;
		private String label;
		private String bracketPhase;
		private Integer raceTo;
	}
}
