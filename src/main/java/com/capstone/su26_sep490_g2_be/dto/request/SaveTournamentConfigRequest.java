package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

	@NotBlank(message = "Phương thức xếp hạt giống không được để trống")
	@Schema(description = "Phương thức xếp hạt giống", example = "RANK",
			allowableValues = {"RANDOM", "RANK", "SEED"})
	private String seedingMethod;

	@NotEmpty(message = "Danh sách field cấu hình không được để trống")
	@Valid
	private List<ConfigFieldValueItem> fields;

	@Valid
	private List<RaceToOverrideItem> raceToOverrides;

	@Getter
	@Setter
	public static class ConfigFieldValueItem {

		@NotBlank(message = "Field key không được để trống")
		private String fieldKey;

		@NotBlank(message = "Giá trị field không được để trống")
		private String value;
	}

	@Getter
	@Setter
	public static class RaceToOverrideItem {

		@NotBlank(message = "Mã vòng đấu không được để trống")
		private String roundKey;

		@NotNull(message = "Race-to không được để trống")
		@Min(value = 1, message = "Race-to phải lớn hơn 0")
		@Max(value = 99, message = "Race-to không được vượt quá 99")
		private Integer raceTo;
	}
}
