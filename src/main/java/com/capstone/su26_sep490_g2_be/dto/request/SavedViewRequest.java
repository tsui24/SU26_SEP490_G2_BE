package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SavedViewRequest {

	@NotBlank
	private String name;

	@NotNull
	@Valid
	private AnalyticsQueryRequest config;
}
