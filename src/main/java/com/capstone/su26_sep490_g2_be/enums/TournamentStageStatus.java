package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TournamentStageStatus {

	PENDING("Chờ"),
	IN_PROGRESS("Đang diễn ra"),
	COMPLETED("Hoàn thành");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
