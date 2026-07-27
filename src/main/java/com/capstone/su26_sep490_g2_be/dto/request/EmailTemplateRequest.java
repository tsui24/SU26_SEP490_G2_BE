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

	@NotBlank(message = "Mã mẫu email không được để trống")
	@Schema(description = "Mã mẫu email, duy nhất, ví dụ REGISTRATION_APPROVED")
	private String code;

	@NotBlank(message = "Tên mẫu email không được để trống")
	private String name;

	private String description;

	@NotBlank(message = "Danh mục không được để trống")
	@Schema(description = "SYSTEM | TOURNAMENT | MARKETING | TRANSACTIONAL")
	private String category;

	@NotBlank(message = "Tiêu đề email không được để trống")
	@Schema(description = "Tiêu đề email, có thể chứa placeholder {{...}}")
	private String subjectTemplate;

	@NotBlank(message = "Nội dung email không được để trống")
	@Schema(description = "Nội dung HTML, có thể chứa placeholder {{...}}")
	private String bodyHtmlTemplate;

	@Schema(description = "Danh sách placeholder được hỗ trợ, ví dụ [\"tournament.name\"]")
	private List<String> availableVariables;
}
