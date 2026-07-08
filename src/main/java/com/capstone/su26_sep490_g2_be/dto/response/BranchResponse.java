package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@Schema(description = "Chi tiết chi nhánh")
public class BranchResponse {

	private Long id;
	private String name;
	private String address;
	private String phone;
	private String description;
	private String status;
	private List<BranchImageResponse> images;
	private Instant createdAt;
	private Instant updatedAt;
}
