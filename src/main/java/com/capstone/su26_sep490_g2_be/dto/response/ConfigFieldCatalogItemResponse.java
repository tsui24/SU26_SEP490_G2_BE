package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Catalog field definition")
public class ConfigFieldCatalogItemResponse {

	private String fieldKey;
	private String label;
	private String description;
	private String dataType;
	private String fieldScope;
	private String uiComponent;
	private List<String> enumOptions;
	private Integer minValue;
	private Integer maxValue;
	private Boolean isActive;
}
