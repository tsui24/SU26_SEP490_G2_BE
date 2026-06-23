package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Player đăng ký giải đấu với form dynamic")
public class SubmitTournamentRegistrationRequest {

	@NotBlank
	@Schema(description = "Loại đăng ký", example = "INDIVIDUAL")
	private String registrationType;

	private String note;

	@Valid
	private List<FieldValueItem> fieldValues;

	@Getter
	@Setter
	public static class FieldValueItem {

		@NotBlank
		private String fieldKey;

		@NotBlank
		private String value;
	}
}
