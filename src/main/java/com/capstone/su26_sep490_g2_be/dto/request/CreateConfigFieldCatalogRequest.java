package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Admin thêm field mới vào catalog cấu hình giải")
public class CreateConfigFieldCatalogRequest {

	@NotBlank(message = "Field key không được để trống")
	@Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "Field key phải dạng snake_case")
	@Schema(example = "is_show_tournament")
	private String fieldKey;

	@NotBlank(message = "Nhãn hiển thị không được để trống")
	@Schema(example = "Hiển thị giải đấu")
	private String label;

	@Schema(example = "Hiển thị giải trên trang công khai")
	private String description;

	@NotBlank(message = "Kiểu dữ liệu không được để trống")
	@Schema(description = "INT | BOOLEAN | ENUM | STRING", example = "BOOLEAN")
	private String dataType;

	@NotBlank(message = "Phạm vi field không được để trống")
	@Schema(description = "COMMON | KNOCKOUT | GROUP | DOUBLE_ELIM | PLAYOFF", example = "COMMON")
	private String fieldScope;

	@NotBlank(message = "Thành phần UI không được để trống")
	@Schema(description = "NUMBER | SELECT | CHECKBOX | TEXT", example = "CHECKBOX")
	private String uiComponent;

	@Schema(description = "Bắt buộc khi dataType=ENUM", example = "[\"ALTERNATE_BREAK\",\"WINNER_BREAK\"]")
	private List<String> enumOptions;

	private Integer minValue;

	private Integer maxValue;

	@Schema(description = "Mặc định true", example = "true")
	private Boolean isActive;
}
