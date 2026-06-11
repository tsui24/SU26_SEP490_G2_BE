package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Admin tạo loại bi mới")
public class CreateGameTypeRequest {

	@NotBlank(message = "Mã loại bi không được để trống")
	@Pattern(regexp = "^[A-Z0-9]+(_[A-Z0-9]+)*$",
			message = "Mã loại bi phải dạng UPPER_SNAKE_CASE (vd. 9_BALL, 10_BALL, CAROM_3C)")
	@Schema(example = "9_BALL", description = "Chữ hoa + số, phân cách _. Cho phép bắt đầu bằng số: 5_BALL, 9_BALL")
	private String code;

	@NotBlank(message = "Tên loại bi không được để trống")
	@Schema(example = "9-Ball (Bida lỗ 9 bi)")
	private String name;

	@Schema(example = "Race-to, alternate break.")
	private String description;

	@Min(value = 1, message = "Race-to mặc định phải lớn hơn 0")
	@Schema(example = "7")
	private Integer defaultRaceTo;

	@Schema(description = "Loại bàn tương thích", example = "[\"POOL\"]")
	private List<String> compatibleTableTypes;

	@Schema(description = "Mặc định true", example = "true")
	private Boolean isActive;
}
