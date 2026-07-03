package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParticipantType {

	SINGLE("Đơn"),
	DOUBLE("Đôi"),
	TEAM("Đội");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
