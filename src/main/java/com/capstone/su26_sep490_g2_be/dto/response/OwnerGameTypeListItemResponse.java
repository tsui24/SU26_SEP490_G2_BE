package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Loại bi — dropdown Owner/Manager")
public class OwnerGameTypeListItemResponse {

	private String code;
	private String name;
	private Integer defaultRaceTo;
	private Integer sortOrder;
}
