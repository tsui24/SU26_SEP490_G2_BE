package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Bật/tắt hiển thị công khai của giải đấu")
public class PatchTournamentVisibilityRequest {

	@NotNull(message = "isShowTournament không được để trống")
	@Schema(description = "Có hiển thị công khai hay không", example = "true")
	private Boolean isShowTournament;
}
