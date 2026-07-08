package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Ảnh chi nhánh — key để cập nhật lại danh sách, url để hiển thị")
public class BranchImageResponse {

	private String key;
	private String url;
}
