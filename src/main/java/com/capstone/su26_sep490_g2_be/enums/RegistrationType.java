package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RegistrationType {

	SINGLE("Đơn"),
	DOUBLE("Đôi"),
	TEAM("Đội"),
	MANUAL("Thêm thủ công"),
	ONLINE_REGISTRATION("Đăng ký online");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
