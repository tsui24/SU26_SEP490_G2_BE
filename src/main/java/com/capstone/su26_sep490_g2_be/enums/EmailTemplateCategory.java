package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailTemplateCategory {

	SYSTEM("Hệ thống"),
	TOURNAMENT("Giải đấu"),
	MARKETING("Marketing"),
	TRANSACTIONAL("Giao dịch");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
