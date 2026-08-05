package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum SeedingMethod {

	/** Bốc thăm thuần — xáo toàn bộ, không ưu tiên ai. */
	RANDOM("Ngẫu nhiên"),
	/**
	 * Xếp theo {@link BilliardRank} của cơ thủ.
	 *
	 * <p>Thay cho hai chế độ cũ đã gỡ: {@code ELO} (chưa từng được cài đặt, không có nguồn dữ liệu)
	 * và {@code MANUAL} (Owner tự gõ {@code seedNo} — chưa giải nào dùng, và dữ liệu để lại bị hỏng
	 * hàng loạt do thiếu ràng buộc duy nhất). Muốn can thiệp tay thì dùng chức năng đổi chỗ ở màn
	 * Bốc thăm sau khi đã sinh bracket.
	 */
	RANK("Theo hạng cơ thủ");

	private final String displayName;

	public static boolean isValid(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		return Arrays.stream(values()).anyMatch(v -> v.name().equals(value));
	}
}
