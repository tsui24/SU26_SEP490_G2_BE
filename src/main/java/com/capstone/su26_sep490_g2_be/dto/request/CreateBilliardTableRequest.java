package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Owner tạo bàn mới trong pool bàn của chuỗi")
public class CreateBilliardTableRequest {

	@NotBlank(message = "Tên bàn không được để trống")
	@Size(max = 100, message = "Tên bàn tối đa 100 ký tự")
	@Schema(example = "Bàn VIP A")
	private String name;

	@Schema(example = "1", description = "Số hiển thị — dùng để đồng bộ với tableNo cũ trên các màn hình Staff/Live/Public")
	private Integer tableNumber;

	@Schema(example = "POOL", description = "POOL / CAROM / SNOOKER / OTHER — có thể bỏ trống")
	private String tableType;

	@Schema(description = "Chi nhánh gợi ý — bỏ trống = dùng chung cả chuỗi")
	private Long branchId;
}
