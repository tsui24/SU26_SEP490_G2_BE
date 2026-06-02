package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Kết quả cập nhật giải đấu")
public class UpdateTournamentResponse {

	private Long id;
	private String status;
	private String format;
	private Boolean configComplete;
}
