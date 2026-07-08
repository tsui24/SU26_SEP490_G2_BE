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
@Schema(description = "Chi tiết 1 lượt gửi email — bao gồm HTML đã render để xem lại")
public class EmailSendLogDetailResponse {

	private Long id;
	private String templateCode;
	private String templateName;
	private String ruleCode;
	private Long tournamentId;
	private String tournamentName;
	private String triggerType;
	private String recipientEmail;
	private String subjectRendered;
	private String bodyRendered;
	private String status;
	private String statusDisplayName;
	private String errorMessage;
	private Instant sentAt;
	private Instant createdAt;
}
