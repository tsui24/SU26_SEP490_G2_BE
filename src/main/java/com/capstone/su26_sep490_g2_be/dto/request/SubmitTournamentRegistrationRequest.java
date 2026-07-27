package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Player đăng ký giải đấu với form dynamic")
public class SubmitTournamentRegistrationRequest {

	@NotBlank(message = "Loại đăng ký không được để trống")
	@Pattern(regexp = "SINGLE|DOUBLE", message = "Loại đăng ký không hợp lệ (chỉ nhận SINGLE hoặc DOUBLE)")
	@Schema(description = "Loại đăng ký — phải khớp participantType của giải", example = "SINGLE")
	private String registrationType;

	private String note;

	@Valid
	private List<FieldValueItem> fieldValues;

	@Getter
	@Setter
	public static class FieldValueItem {

		@NotBlank(message = "Field key không được để trống")
		private String fieldKey;

		@NotBlank(message = "Giá trị field không được để trống")
		private String value;
	}
}
