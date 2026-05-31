package com.capstone.su26_sep490_g2_be.dto.response;

import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Thể thức giải đấu — item trong danh sách")
public class FormatListItemResponse {

	private String code;
	private String name;
	private String description;
	private String handlerKey;
	private String schemaVersion;
	private Boolean isActive;
	private FormatSetupStatus setupStatus;
	private long configFieldCount;
	private long raceToRuleCount;
}
