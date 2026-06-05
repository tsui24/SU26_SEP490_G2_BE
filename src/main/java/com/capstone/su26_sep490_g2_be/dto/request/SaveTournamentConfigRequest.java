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
@Schema(description = "Lưu config giải — wizard bước 2")
public class SaveTournamentConfigRequest {

	@NotBlank
	@Schema(description = "Phương thức xếp hạt giống", example = "ELO")
	private String seedingMethod;

	@NotEmpty
	@Valid
	private List<ConfigFieldValueItem> fields;

	@Valid
	private List<RaceToOverrideItem> raceToOverrides;

	@Getter
	@Setter
	public static class ConfigFieldValueItem {

		@NotBlank
		private String fieldKey;

		@NotBlank
		private String value;
	}

	@Getter
	@Setter
	public static class RaceToOverrideItem {

		@NotBlank
		private String roundKey;

		@NotNull
		private Integer raceTo;
	}
}
