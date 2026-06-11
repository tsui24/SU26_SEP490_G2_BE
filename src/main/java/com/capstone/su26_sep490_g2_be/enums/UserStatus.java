package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {

	ACTIVE("Hoạt động"),
	LOCKED("Đã khóa"),
	DELETED("Đã xóa");

	private final String displayName;
}
