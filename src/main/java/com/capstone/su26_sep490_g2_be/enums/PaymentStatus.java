package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

	PENDING("Chờ thanh toán"),
	SUCCESS("Thành công"),
	FAILED("Thất bại"),
	CANCELLED("Đã hủy");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
