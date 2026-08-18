package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FinanceEntryType {

	INCOME("Khoản thu"),
	EXPENSE("Khoản chi");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
