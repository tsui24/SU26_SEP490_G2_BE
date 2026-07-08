package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailSendStatus {

	QUEUED("Đang chờ gửi"),
	SENT("Đã gửi"),
	FAILED("Gửi thất bại"),
	CANCELLED("Đã hủy");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
