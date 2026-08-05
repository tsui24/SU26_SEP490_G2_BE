package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Loại giai đoạn thi đấu (cột {@code tournament_stages.stage_type}).
 * Dùng khi lọc trận knockout khỏi vòng bảng trong tính xếp hạng loại trực tiếp.
 */
@Getter
@RequiredArgsConstructor
public enum TournamentStageType {

	KNOCKOUT("Knockout"),
	WINNERS("Nhánh thắng"),
	LOSERS("Nhánh thua"),
	GRAND_FINAL("Chung kết lớn"),
	PROGRESSIVE_ROUND("Vòng tròn loại dần"),
	PROGRESSIVE_PLAYOFF("Playoff");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
