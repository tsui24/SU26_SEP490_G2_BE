package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Đổi trạng thái giải đấu")
public class PatchTournamentStatusRequest {

	@NotBlank
	@Schema(description = "Trạng thái mới", example = "OPEN_FOR_REGISTRATION")
	private String status;
}
