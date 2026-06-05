package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Danh sách thể thức cho Owner/Manager")
public class OwnerFormatListResponse {

	private List<OwnerFormatListItemResponse> items;
	private int total;
}
