package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Một dòng trên bảng xếp hạng điểm tích lũy cơ thủ")
public class LeaderboardEntryResponse {

	@Schema(description = "Thứ hạng trên bảng xếp hạng (1 = dẫn đầu)")
	private Integer rank;

	@Schema(description = "ID tài khoản cơ thủ — dùng mở hồ sơ công khai")
	private Long userId;

	@Schema(description = "Tên hiển thị")
	private String playerName;

	@Schema(description = "Ảnh đại diện (presigned URL), null nếu chưa có")
	private String avatarUrl;

	@Schema(description = "Tổng điểm tích lũy trong kỳ")
	private Long totalPoints;

	@Schema(description = "Số giải đã thi đấu trong kỳ")
	private Long tournamentsPlayed;

	@Schema(description = "Số lần vô địch trong kỳ")
	private Long championCount;

	@Schema(description = "Số lần vào top 3 trong kỳ")
	private Long top3Count;

	@Schema(description = "Tổng tiền thưởng trong kỳ")
	private BigDecimal totalPrizeAmount;
}
