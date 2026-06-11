package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleCode {

	ADMIN("ADMIN", "Quản trị viên"),
	OWNER("OWNER", "Chủ chuỗi quán"),
	MANAGER("MANAGER", "Quản lý cơ sở"),
	STAFF("STAFF", "Nhân viên / Trọng tài"),
	PLAYER("PLAYER", "Cơ thủ");

	private final String code;
	private final String displayName;
}
