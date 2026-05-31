package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Bootstrap default config mẫu cho thể thức")
public class BootstrapDefaultsRequest {

	@Schema(description = "Ghi đè nếu đã có default", example = "false")
	private Boolean overwrite;
}
