package com.capstone.su26_sep490_g2_be.enums;

import java.util.Arrays;

public enum SeedingMethod {
	RANDOM,
	MANUAL,
	ELO;

	public static boolean isValid(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		return Arrays.stream(values()).anyMatch(v -> v.name().equals(value));
	}
}
