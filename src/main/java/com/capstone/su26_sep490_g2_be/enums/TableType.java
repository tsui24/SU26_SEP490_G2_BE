package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TableType {

	POOL("Pool"),
	CAROM("Carom"),
	SNOOKER("Snooker"),
	OTHER("Khác");

	private final String displayName;
}
