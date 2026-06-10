package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Dropdown template form đăng ký cho Owner/Manager")
public class OwnerRegistrationFormTemplateListResponse {

	private List<OwnerRegistrationFormTemplateItemResponse> items;
	private int total;

	@Getter
	@Builder
	public static class OwnerRegistrationFormTemplateItemResponse {
		private Long id;
		private String code;
		private String name;
		private String description;
		private Integer sortOrder;
		private Long fieldCount;
	}
}
