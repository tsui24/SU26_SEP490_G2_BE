package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class PlayerAnalyticsDetailResponse {
	private Long userId;
	private String playerName;
	private String email;

	/** Số liệu tổng hợp cả đời với owner này (không giới hạn kỳ lọc trang tổng). */
	private PlayerLeaderboardItem summary;

	private List<TournamentHistoryItem> history;

	@Getter
	@Builder
	public static class TournamentHistoryItem {
		private Long tournamentId;
		private String tournamentName;
		private String branchName;
		private Instant registeredAt;
		private String registrationStatus;
		private String registrationStatusLabel;
		private BigDecimal amountPaid;
		private Integer finalRank;
		private BigDecimal prizeAmount;
		private Integer pointsEarned;
	}
}
