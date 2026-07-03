package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Một dòng xếp hạng cơ thủ trong giải")
public class TournamentRankingEntryResponse {

	/** Thứ tự sắp xếp trên UI — 1 = vô địch, số nhỏ hơn = hạng cao hơn. */
	@Schema(description = "Thứ tự sắp xếp (1 = vô địch)")
	private Integer sortOrder;

	/** Nhãn hiển thị kiểu WNT — xem {@link com.capstone.su26_sep490_g2_be.enums.RankingLabelFormat}. */
	@Schema(description = "Nhãn hiển thị: #1, #2, #3-4, #5-8...")
	private String rankLabel;

	/** Hạng thấp nhất trong nhóm (vd. 5 trong #5-8). */
	@Schema(description = "Hạng thấp nhất trong nhóm (vd. 5 trong #5-8)")
	private Integer rankFrom;

	/** Hạng cao nhất trong nhóm (vd. 8 trong #5-8). */
	@Schema(description = "Hạng cao nhất trong nhóm (vd. 8 trong #5-8)")
	private Integer rankTo;

	private Long participantId;

	private String displayName;

	/** Ghi chú tiếng Việt — xem {@link com.capstone.su26_sep490_g2_be.enums.RankingPlacementNote}. */
	@Schema(description = "Ghi chú ngắn: Vô địch, Á quân, Bán kết...")
	private String note;
}
