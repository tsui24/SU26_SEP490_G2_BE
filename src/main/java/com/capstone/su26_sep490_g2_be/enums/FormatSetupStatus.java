package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FormatSetupStatus {

	DRAFT("Nháp"),
	INFO_DONE("Đã nhập thông tin"),
	CONFIG_FIELDS_DONE("Đã cấu hình field"),
	RACE_TO_DONE("Đã cấu hình race-to"),
	READY_TO_ACTIVATE("Sẵn sàng kích hoạt"),
	ACTIVE("Đang hoạt động");

	private final String displayName;
}
