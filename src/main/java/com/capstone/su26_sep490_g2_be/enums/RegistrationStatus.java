package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RegistrationStatus {

	PENDING_PAYMENT("Chờ thanh toán"),
	PAID("Đã thanh toán"),
	APPROVED("Tham gia chính thức"),
	REJECTED("Không được tham dự"),
	CANCELLED("Đã hủy");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
