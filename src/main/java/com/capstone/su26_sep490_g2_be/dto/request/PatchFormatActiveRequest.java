package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Bật/tắt thể thức")
public class PatchFormatActiveRequest {

	@NotNull(message = "Trạng thái active không được để trống")
	private Boolean isActive;
}
