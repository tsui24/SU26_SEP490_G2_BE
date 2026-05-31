package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Lưu default race-to rules cho thể thức — Wizard màn 3")
public class UpsertFormatRaceToRulesRequest {

	@NotEmpty
	@Valid
	private List<FormatRaceToRuleItemRequest> rules;

	@Getter
	@Setter
	public static class FormatRaceToRuleItemRequest {

		@NotBlank
		private String roundKey;

		private String label;

		@NotBlank
		private String bracketPhase;

		@NotNull
		private Integer raceTo;
	}
}
