package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

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

	/** Bracket chỉ chưa sinh ở các trạng thái này — sau đó danh sách người tham gia bị khóa cứng. */
	private static final Set<String> ROSTER_EDITABLE_STATUSES = Set.of(
			DRAFT.name(), OPEN_FOR_REGISTRATION.name(), REGISTRATION_CLOSED.name());

	public static boolean isRosterEditable(String status) {
		return ROSTER_EDITABLE_STATUSES.contains(status);
	}
}
