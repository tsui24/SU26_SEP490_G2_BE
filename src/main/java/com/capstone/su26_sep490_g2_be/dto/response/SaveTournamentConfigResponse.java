package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Kết quả lưu config giải")
public class SaveTournamentConfigResponse {

	private Long tournamentId;
	private String formatCode;
	private String seedingMethod;
	private Integer seedCount;
	private Boolean isConfigComplete;
	private List<ConfigValidationDetailResponse> validationErrors;
}
