package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chủ đề và nội dung email đã render")
public class RenderedEmailResponse {

	@Schema(description = "Tiêu đề email đã render")
	private String subject;

	@Schema(description = "Nội dung HTML đã render")
	private String bodyHtml;
}
