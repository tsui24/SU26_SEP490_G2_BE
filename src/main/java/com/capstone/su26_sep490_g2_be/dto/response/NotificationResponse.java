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
@Schema(description = "Một thông báo trong hộp thông báo của người dùng")
public class NotificationResponse {

	@Schema(description = "Id bản ghi log gốc")
	private Long id;

	@Schema(description = "Tiêu đề — lấy từ tiêu đề email đã gửi")
	private String title;

	@Schema(description = "Vài dòng đầu của nội dung, đã bỏ thẻ HTML")
	private String preview;

	@Schema(description = "Loại sự kiện sinh ra thông báo, ví dụ REGISTRATION_APPROVED")
	private String eventType;

	@Schema(description = "Giải đấu liên quan — null nếu thông báo không gắn giải nào")
	private Long tournamentId;

	@Schema(description = "Tên giải đấu liên quan")
	private String tournamentName;

	@Schema(description = "Thời điểm sinh thông báo — mobile so với mốc đã đọc để tính chưa đọc")
	private Instant createdAt;
}
