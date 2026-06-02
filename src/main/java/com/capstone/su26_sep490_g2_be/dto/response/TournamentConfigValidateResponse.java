package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Kết quả validate config giải")
public class TournamentConfigValidateResponse {

	private Long tournamentId;
	private Boolean isValid;
	private Boolean isConfigComplete;
	private List<ConfigValidationDetailResponse> errors;
	private List<ConfigValidationDetailResponse> warnings;
}
