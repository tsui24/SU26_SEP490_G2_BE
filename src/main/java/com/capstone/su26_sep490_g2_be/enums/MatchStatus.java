package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MatchStatus {

	PENDING("Chờ đấu"),
	IN_PROGRESS("Đang đấu"),
	COMPLETED("Hoàn thành"),
	BYE("BYE"),
	WALKOVER("Walkover");

	private final String displayName;

	public String getValue() {
		return name();
	}

	public boolean isResolved() {
		return this == COMPLETED || this == BYE || this == WALKOVER;
	}
}
