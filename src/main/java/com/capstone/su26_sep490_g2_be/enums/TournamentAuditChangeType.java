package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TournamentAuditChangeType {

	MANUAL("Thao tác thủ công"),
	AUTO("Hệ thống tự động"),
	WARNING("Cảnh báo");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
