package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Đổi trạng thái chi nhánh")
public class BranchStatusUpdateRequest {

	@NotNull(message = "Trạng thái không được để trống")
	@Schema(example = "INACTIVE")
	private String status;
}
