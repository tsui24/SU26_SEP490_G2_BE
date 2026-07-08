package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Quy tắc gửi email tự động")
public class EmailAutomationRuleResponse {

	private Long id;
	private String code;
	private String name;
	private String description;
	private String eventType;
	private String eventTypeDisplayName;
	private Long templateId;
	private String templateCode;
	private String templateName;
	private String scope;
	private Long tournamentId;
	private String recipientType;
	private String recipientTypeDisplayName;
	private Boolean isEnabled;
	private Integer delayMinutes;
	private String conditions;
	private Instant createdAt;
	private Instant updatedAt;
}
