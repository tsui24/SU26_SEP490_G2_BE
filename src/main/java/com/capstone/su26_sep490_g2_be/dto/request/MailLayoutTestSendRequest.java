package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Gửi email thử để xem khung header/footer hiển thị thế nào trên hộp thư thật")
public class MailLayoutTestSendRequest {

	@NotBlank(message = "Vui lòng nhập email nhận thử")
	@Email(message = "Email không hợp lệ")
	@Schema(description = "Địa chỉ email nhận thử")
	private String email;

	@Schema(description = "HTML header đang soạn (chưa lưu) — để trống thì dùng bản đã lưu trong hệ thống")
	private String headerHtml;

	@Schema(description = "HTML footer đang soạn (chưa lưu) — để trống thì dùng bản đã lưu trong hệ thống")
	private String footerHtml;
}
