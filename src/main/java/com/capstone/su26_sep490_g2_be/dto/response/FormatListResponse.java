package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Danh sách thể thức")
public class FormatListResponse {

	private List<FormatListItemResponse> items;
	private int total;
}
