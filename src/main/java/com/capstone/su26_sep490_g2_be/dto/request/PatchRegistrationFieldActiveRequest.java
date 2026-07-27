package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Bật/tắt field catalog")
public class PatchRegistrationFieldActiveRequest {

	@NotNull(message = "Trạng thái kích hoạt không được để trống")
	private Boolean isActive;
}
