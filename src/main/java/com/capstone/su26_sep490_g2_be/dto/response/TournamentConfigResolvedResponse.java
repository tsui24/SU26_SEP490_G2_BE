package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@Schema(description = "Config giải đã resolve — preview trước mở đăng ký")
public class TournamentConfigResolvedResponse {

	private Long tournamentId;
	private String formatCode;
	private String formatName;
	private String gameType;
	private String seedingMethod;
	private Integer seedCount;
	private Boolean isConfigComplete;
	private Map<String, Object> fields;
	private Map<String, Integer> raceToRules;
	private List<String> overriddenRounds;
}
