package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Dùng chung cho phạm vi của cả template lẫn automation rule. */
@Getter
@RequiredArgsConstructor
public enum EmailScope {

	GLOBAL("Toàn hệ thống"),
	OWNER("Riêng của chủ giải"),
	TOURNAMENT("Riêng giải đấu");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
