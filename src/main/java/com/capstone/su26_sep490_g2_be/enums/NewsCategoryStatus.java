package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NewsCategoryStatus {

	ACTIVE("Hoạt động"),
	INACTIVE("Không hoạt động");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
