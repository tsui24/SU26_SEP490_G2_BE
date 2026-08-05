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

	/**
	 * Chuỗi lưu DB / gửi xuống FE trong field {@code note} — dùng {@code name()} (English,
	 * ALL_CAPS) để khớp quy ước chung của các cột status/type khác trong dự án. FE tự dịch sang
	 * tiếng Việt qua bảng label riêng (xem {@code RANKING_NOTE_LABELS} ở FE), có fallback về
	 * nguyên văn nếu không khớp key — nên các dòng {@code tournament_results.note} cũ (đã lưu sẵn
	 * text tiếng Việt trước khi đổi) vẫn hiển thị đúng mà không cần migrate dữ liệu.
	 */
	public String getValue() {
		return name();
	}
}
