package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TableStatus {

	ACTIVE("Hoạt động"),
	INACTIVE("Ngừng hoạt động");

	private final String displayName;
}
