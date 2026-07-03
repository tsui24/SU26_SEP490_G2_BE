package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TournamentStatus {

	DRAFT("Nháp"),
	OPEN_FOR_REGISTRATION("Mở đăng ký"),
	REGISTRATION_CLOSED("Đóng đăng ký"),
	DRAW_PREVIEW("Xem trước bốc thăm"),
	DRAW_DONE("Đã bốc thăm"),
	FINAL_BRACKET_READY("Sẵn sàng chung kết"),
	IN_PROGRESS("Đang diễn ra"),
	COMPLETED("Hoàn thành"),
	CANCELLED("Đã hủy");

	private final String displayName;

	public String getValue() {
		return name();
	}
}
