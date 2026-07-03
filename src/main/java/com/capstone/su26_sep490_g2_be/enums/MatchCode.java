package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Mã đặc biệt của trận đấu (cột {@code matches.match_code}).
 * Ví dụ trận tranh hạng 3 tách biệt khỏi nhánh chính khi tính placement.
 */
@Getter
@RequiredArgsConstructor
public enum MatchCode {

	THIRD_PLACE("3RD", "Trận tranh hạng 3");

	private final String code;
	private final String displayName;

	public String getValue() {
		return code;
	}
}
