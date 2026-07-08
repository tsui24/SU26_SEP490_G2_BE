package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Schema(description = "Preview render mẫu email với dữ liệu mẫu hoặc dữ liệu thật")
public class EmailTemplatePreviewRequest {

	@Schema(description = "ID mẫu email cần preview (bắt buộc nếu không truyền templateCode)")
	private Long templateId;

	@Schema(description = "Code mẫu email cần preview (bắt buộc nếu không truyền templateId)")
	private String templateCode;

	@Schema(description = "Nếu có — dùng dữ liệu giải đấu thật để render {{tournament.*}}")
	private Long tournamentId;

	@Schema(description = "Nếu có — dùng dữ liệu đăng ký thật để render {{registration.*}}")
	private Long sampleRegistrationId;

	@Schema(description = "Override/bổ sung biến, ví dụ {\"custom\": {\"message\": \"...\"}}")
	private Map<String, Object> variables;
}
