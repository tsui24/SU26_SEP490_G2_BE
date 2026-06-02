package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Thể thức giải — dropdown Owner/Manager")
public class OwnerFormatListItemResponse {

	private String code;
	private String name;
	private String description;
	private Integer sortOrder;
	private Boolean isReady;
}
