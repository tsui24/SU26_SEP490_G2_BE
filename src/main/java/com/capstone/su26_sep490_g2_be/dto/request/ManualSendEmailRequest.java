package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Schema(description = "Gửi email thủ công tới người tham gia một giải đấu")
public class ManualSendEmailRequest {

	@NotNull
	private Long templateId;

	@Schema(description = "Nếu có — override tiêu đề mẫu cho lần gửi này")
	private String subjectOverride;

	@Schema(description = "Nếu có — override nội dung HTML mẫu cho lần gửi này (sẽ được sanitize)")
	private String bodyOverride;

	@NotBlank
	@Schema(description = "ALL_PARTICIPANTS | REGISTRATION_USER | CUSTOM_LIST")
	private String recipientType;

	@Size(max = 500, message = "Tối đa 500 email cho mỗi lần gửi")
	@Schema(description = "Bắt buộc khi recipientType = CUSTOM_LIST")
	private List<String> recipientEmails;

	@Schema(description = "Biến tự nhập bổ sung, ví dụ {\"custom\": {\"message\": \"...\"}}")
	private Map<String, Object> variables;
}
