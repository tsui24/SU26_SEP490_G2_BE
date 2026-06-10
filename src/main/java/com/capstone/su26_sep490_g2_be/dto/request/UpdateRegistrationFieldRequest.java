package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Cập nhật field catalog")
public class UpdateRegistrationFieldRequest {

	@Size(max = 255)
	private String label;

	private String description;

	private String dataType;

	private String uiComponent;

	private List<String> enumOptions;

	private Integer minValue;

	private Integer maxValue;
}
