package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Chi tiết lỗi validation config giải")
public class ConfigValidationDetailResponse {

	private String fieldKey;
	private String field;
	private String message;
}
