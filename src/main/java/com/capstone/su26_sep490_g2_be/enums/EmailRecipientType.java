package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailRecipientType {

	PLAYER("Người chơi liên quan"),
	ALL_PARTICIPANTS("Tất cả vận động viên đang thi đấu"),
	MATCH_PLAYERS("2 vận động viên của trận đấu"),
	REGISTRATION_USER("Người đăng ký"),
	CUSTOM_LIST("Danh sách email tự nhập"),
	ROLE_STAFF("Toàn bộ nhân viên của giải");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
