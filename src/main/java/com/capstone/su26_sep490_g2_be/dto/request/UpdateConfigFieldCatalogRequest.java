package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Admin cập nhật metadata catalog field (không đổi fieldKey, dataType, fieldScope)")
public class UpdateConfigFieldCatalogRequest {

	@NotBlank(message = "Nhãn hiển thị không được để trống")
	@Schema(example = "Hiển thị giải đấu")
	private String label;

	@Schema(example = "Hiển thị giải trên trang công khai")
	private String description;

	@NotBlank(message = "Thành phần UI không được để trống")
	@Schema(description = "NUMBER | SELECT | CHECKBOX | TEXT", example = "CHECKBOX")
	private String uiComponent;

	@Schema(description = "Chỉ khi dataType=ENUM của field")
	private List<String> enumOptions;

	private Integer minValue;

	private Integer maxValue;
}
