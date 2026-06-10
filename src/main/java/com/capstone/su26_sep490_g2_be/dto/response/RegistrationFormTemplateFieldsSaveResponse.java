package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Kết quả lưu field template đăng ký")
public class RegistrationFormTemplateFieldsSaveResponse {

	private Long templateId;
	private int fieldsSaved;
	private Boolean isReady;
	private String nextStep;
}
