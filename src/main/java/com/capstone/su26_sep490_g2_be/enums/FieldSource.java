package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FieldSource {

	ADMIN_DEFAULT("Mặc định admin"),
	TOURNAMENT("Theo giải đấu");

	private final String displayName;
}
