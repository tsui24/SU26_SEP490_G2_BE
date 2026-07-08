package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Owner tạo chi nhánh mới")
public class CreateBranchRequest {

	@NotBlank(message = "Tên chi nhánh không được để trống")
	@Size(max = 255, message = "Tên chi nhánh tối đa 255 ký tự")
	@Schema(example = "CLB Bi-a FPT Quận 9")
	private String name;

	@NotBlank(message = "Địa chỉ không được để trống")
	@Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
	@Schema(example = "Lô E2a-7, Đường D1, Long Thạnh Mỹ, TP. Thủ Đức")
	private String address;

	@Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
	@Schema(example = "0912345678")
	private String phone;

	@Schema(example = "Chi nhánh chính, có 10 bàn Pool")
	private String description;

	@Schema(description = "Danh sách object key ảnh (lấy từ POST /storage/images)")
	private List<String> images;
}
