package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Tạo/cập nhật quy tắc gửi email tự động")
public class EmailAutomationRuleRequest {

	@NotBlank(message = "Mã quy tắc không được để trống")
	private String code;

	@NotBlank(message = "Tên quy tắc không được để trống")
	private String name;

	private String description;

	@NotBlank(message = "Loại sự kiện không được để trống")
	@Schema(description = "Giá trị trong EmailEventType, ví dụ REGISTRATION_APPROVED")
	private String eventType;

	@NotNull(message = "Mẫu email không được để trống")
	private Long templateId;

	@NotBlank(message = "Đối tượng nhận không được để trống")
	@Schema(description = "Giá trị trong EmailRecipientType")
	private String recipientType;

	@Schema(description = "Nếu có — rule chỉ áp dụng riêng cho giải đấu này (Owner/Manager override)")
	private Long tournamentId;

	@Min(0)
	private Integer delayMinutes = 0;

	@Schema(description = "Điều kiện lọc bổ sung dạng JSON, ví dụ {\"tournamentFormat\": \"DOUBLE_ELIMINATION\"}")
	private String conditions;
}
