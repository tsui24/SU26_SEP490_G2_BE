package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Một placeholder khả dụng cho template email")
public class EmailVariableItemResponse {

	@Schema(description = "Placeholder, ví dụ tournament.name")
	private String key;

	@Schema(description = "Mô tả tiếng Việt")
	private String description;
}
