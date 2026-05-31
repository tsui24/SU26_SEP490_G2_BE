package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Kết quả bật/tắt thể thức")
public class FormatActivePatchResponse {

	private String code;
	private Boolean isActive;
}
