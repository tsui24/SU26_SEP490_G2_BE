package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailTriggerType {

	MANUAL("Gửi thủ công"),
	AUTOMATION("Tự động theo sự kiện"),
	SYSTEM("Hệ thống / lịch định kỳ");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
