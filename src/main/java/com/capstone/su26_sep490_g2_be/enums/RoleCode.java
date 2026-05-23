package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleCode {

	ADMIN("ADMIN", "Administrator"),
	OWNER("OWNER", "Chain Owner"),
	MANAGER("MANAGER", "Club Manager"),
	STAFF("STAFF", "Staff / Referee"),
	PLAYER("PLAYER", "Player");

	private final String code;
	private final String displayName;
}
