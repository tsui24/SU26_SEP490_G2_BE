package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum SeedingMethod {

	RANDOM("Ngẫu nhiên"),
	MANUAL("Thủ công"),
	ELO("Theo ELO");

	private final String displayName;

	public static boolean isValid(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		return Arrays.stream(values()).anyMatch(v -> v.name().equals(value));
	}
}
