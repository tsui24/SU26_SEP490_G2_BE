package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Kết quả tạo giải đấu")
public class CreateTournamentResponse {

	private Long id;
	private String name;
	private String gameType;
	private String format;
	private String participantType;
	private String status;
	private Integer maxParticipants;
	private Boolean configComplete;
}
