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
@Schema(description = "Kết quả gửi email thủ công")
public class ManualSendResultResponse {

	@Schema(description = "Số email đã được xếp hàng gửi")
	private int queuedCount;
}
