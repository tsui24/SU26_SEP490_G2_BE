package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Cập nhật thông tin bàn")
public class UpdateBilliardTableRequest {

	@Size(max = 100, message = "Tên bàn tối đa 100 ký tự")
	private String name;

	private Integer tableNumber;

	@Schema(example = "POOL", description = "POOL / CAROM / SNOOKER / OTHER")
	private String tableType;

	@Schema(description = "Chi nhánh gợi ý — truyền null để bỏ gán chi nhánh")
	private Long branchId;

	@Schema(description = "true nếu muốn xoá gán chi nhánh (branchId=null không phân biệt được với 'giữ nguyên')")
	private Boolean clearBranch;
}
