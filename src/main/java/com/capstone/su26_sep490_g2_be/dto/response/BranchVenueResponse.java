package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Địa điểm tổ chức giải đấu — snapshot chi nhánh lúc tạo/publish")
public class BranchVenueResponse {

	private Long branchId;
	private String name;
	private String address;
	private String phone;
	private List<BranchImageResponse> images;
}
