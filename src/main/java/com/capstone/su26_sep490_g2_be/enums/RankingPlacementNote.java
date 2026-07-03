package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Ghi chú ngắn hiển thị cạnh tên cơ thủ trong bảng xếp hạng (field {@code note} của ranking entry).
 * Khác với {@code rankLabel} (#1, #3-4...) — đây là mô tả tiếng Việt cho người xem.
 */
@Getter
@RequiredArgsConstructor
public enum RankingPlacementNote {

	CHAMPION("Vô địch"),
	RUNNER_UP("Á quân"),
	THIRD_PLACE("Hạng 3"),
	FOURTH_PLACE("Hạng 4"),
	SEMI_FINAL("Bán kết"),
	GROUP_LEADER("Dẫn đầu bảng");

	private final String displayName;

	/** Chuỗi gửi xuống FE trong field {@code note}. */
	public String getValue() {
		return displayName;
	}
}
