package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mẫu email")
public class EmailTemplateResponse {

	private Long id;
	private String code;
	private String name;
	private String description;
	private String category;
	private String categoryDisplayName;
	private String scope;
	private Long ownerId;
	private String subjectTemplate;
	private String bodyHtmlTemplate;
	private List<String> availableVariables;
	private Boolean isActive;
	private Instant createdAt;
	private Instant updatedAt;
}
