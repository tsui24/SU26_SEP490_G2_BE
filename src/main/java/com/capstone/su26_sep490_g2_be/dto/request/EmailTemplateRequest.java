package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Tạo/cập nhật mẫu email")
public class EmailTemplateRequest {

	@NotBlank
	@Schema(description = "Mã mẫu email, duy nhất, ví dụ REGISTRATION_APPROVED")
	private String code;

	@NotBlank
	private String name;

	private String description;

	@NotBlank
	@Schema(description = "SYSTEM | TOURNAMENT | MARKETING | TRANSACTIONAL")
	private String category;

	@NotBlank
	@Schema(description = "Tiêu đề email, có thể chứa placeholder {{...}}")
	private String subjectTemplate;

	@NotBlank
	@Schema(description = "Nội dung HTML, có thể chứa placeholder {{...}}")
	private String bodyHtmlTemplate;

	@Schema(description = "Danh sách placeholder được hỗ trợ, ví dụ [\"tournament.name\"]")
	private List<String> availableVariables;
}
