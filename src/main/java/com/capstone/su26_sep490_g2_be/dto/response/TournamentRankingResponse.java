package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Bảng xếp hạng giải đấu — response cho tab Xếp hạng trên FE")
public class TournamentRankingResponse {

	private Long tournamentId;

	/** Trạng thái giải hiện tại — khớp {@link com.capstone.su26_sep490_g2_be.enums.TournamentStatus}. */
	private String tournamentStatus;

	/**
	 * {@code true} khi giải {@link com.capstone.su26_sep490_g2_be.enums.TournamentStatus#COMPLETED}.
	 * FE hiển thị badge "Kết quả chính thức"; ngược lại là "Xếp hạng tạm thời".
	 */
	@Schema(description = "true khi giải đã COMPLETED — kết quả chính thức")
	private Boolean isOfficial;

	/** Danh sách cơ thủ đã xếp hạng, sắp theo sortOrder tăng dần. */
	private List<TournamentRankingEntryResponse> entries;
}
