package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Kết quả tạo template form đăng ký")
public class RegistrationFormTemplateCreateResponse {

	private Long id;
	private String code;
	private String name;
	private String nextStep;
}
