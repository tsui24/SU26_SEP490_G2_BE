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
@Schema(description = "Khung header/footer chung cho email")
public class MailLayoutSettingsResponse {

	private Long id;
	private String headerHtml;
	private String footerHtml;
	private Instant updatedAt;
}
