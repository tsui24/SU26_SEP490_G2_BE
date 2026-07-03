package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParticipantStatus {

	ACTIVE("Đang tham gia"),
	INACTIVE("Không còn thi đấu"),
	WITHDRAWN("Đã rút lui");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
