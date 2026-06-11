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

	@NotEmpty(message = "Danh sách quy tắc race-to không được để trống")
	@Valid
	private List<FormatRaceToRuleItemRequest> rules;

	@Getter
	@Setter
	public static class FormatRaceToRuleItemRequest {

		@NotBlank(message = "Mã vòng đấu không được để trống")
		private String roundKey;

		private String label;

		@NotBlank(message = "Giai đoạn bracket không được để trống")
		private String bracketPhase;

		@NotNull(message = "Race-to không được để trống")
		private Integer raceTo;
	}
}
