package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Cập nhật loại bi")
public class UpdateGameTypeRequest {

	@NotBlank(message = "Tên loại bi không được để trống")
	private String name;

	private String description;

	private Integer defaultRaceTo;

	private Boolean isActive;
}
