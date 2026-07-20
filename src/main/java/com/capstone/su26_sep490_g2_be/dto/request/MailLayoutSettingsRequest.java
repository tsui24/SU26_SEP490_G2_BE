package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Cập nhật khung header/footer chung cho email")
public class MailLayoutSettingsRequest {

	@NotBlank
	@Schema(description = "HTML phần đầu email (banner thương hiệu), có thể chứa placeholder {{system.*}}")
	private String headerHtml;

	@NotBlank
	@Schema(description = "HTML phần chân email (footer), có thể chứa placeholder {{system.*}}")
	private String footerHtml;
}
