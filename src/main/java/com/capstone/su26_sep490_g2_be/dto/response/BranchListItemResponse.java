package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Chi nhánh — mục trong danh sách")
public class BranchListItemResponse {

	private Long id;
	private String name;
	private String address;
	private String phone;
	private String description;
	private String status;
	private String thumbnailUrl;
}
