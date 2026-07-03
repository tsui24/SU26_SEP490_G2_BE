package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NewsPostStatus {

	DRAFT("Nháp"),
	PUBLISHED("Đã xuất bản"),
	HIDDEN("Ẩn");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
