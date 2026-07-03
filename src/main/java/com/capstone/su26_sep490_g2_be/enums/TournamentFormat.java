package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Mã thể thức giải đấu (cột {@code tournaments.format}).
 * Dùng để chọn chiến lược tính xếp hạng trong {@code TournamentResultServiceImpl}.
 */
@Getter
@RequiredArgsConstructor
public enum TournamentFormat {

	SINGLE_ELIMINATION("Loại trực tiếp"),
	DOUBLE_ELIMINATION("Loại trực tiếp kép"),
	GROUP_PLAYOFF("Vòng tròn + Playoff");

	private final String displayName;

	/** Giá trị lưu trong DB / so sánh với {@code tournament.getFormat()}. */
	public String getValue() {
		return name();
	}
}
