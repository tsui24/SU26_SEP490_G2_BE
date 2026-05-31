package com.capstone.su26_sep490_g2_be.dto.response;

import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Trạng thái setup thể thức")
public class FormatSetupStatusResponse {

	private String formatCode;
	private FormatSetupStatus setupStatus;
	private boolean bootstrapped;
	private long configFieldCount;
	private long raceToRuleCount;
	private Boolean isActive;
	private boolean canActivate;
	private List<String> missingSteps;
}
