package com.capstone.su26_sep490_g2_be.dto.response;

import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Kết quả tạo thể thức")
public class FormatCreateResponse {

	private String code;
	private String name;
	private FormatSetupStatus setupStatus;
	private String nextStep;
}
