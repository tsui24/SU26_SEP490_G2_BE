package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Cập nhật thông tin chi nhánh")
public class UpdateBranchRequest {

	@Size(max = 255, message = "Tên chi nhánh tối đa 255 ký tự")
	private String name;

	@Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
	private String address;

	@Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
	private String phone;

	private String description;

	@Schema(description = "Danh sách object key ảnh — thay thế toàn bộ danh sách hiện có, null = giữ nguyên")
	private List<String> images;
}
