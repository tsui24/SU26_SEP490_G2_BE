package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Tạo field catalog cho form đăng ký")
public class CreateRegistrationFieldRequest {

	@NotBlank(message = "Field key không được để trống")
	@Size(max = 80, message = "Field key tối đa 80 ký tự")
	@Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "Field key phải là chữ thường dạng snake_case")
	@Schema(example = "player_full_name")
	private String fieldKey;

	@NotBlank(message = "Nhãn hiển thị không được để trống")
	@Size(max = 255, message = "Nhãn hiển thị tối đa 255 ký tự")
	private String label;

	private String description;

	@NotBlank(message = "Kiểu dữ liệu không được để trống")
	@Schema(example = "STRING")
	private String dataType;

	@NotBlank(message = "UI component không được để trống")
	@Schema(example = "TEXT")
	private String uiComponent;

	private List<String> enumOptions;

	private Integer minValue;

	private Integer maxValue;

	private Boolean isActive;
}
