package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "Chi tiết template form đăng ký")
public class RegistrationFormTemplateDetailResponse {

	private Long id;
	private String code;
	private String name;
	private String description;
	private Boolean isActive;
	private Integer sortOrder;
	private Long fieldCount;
	private Boolean isReady;
	private Long createdById;
	private Instant createdAt;
	private Instant updatedAt;
}
