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
	 * <p>Thay cho chế độ cũ đã gỡ {@code ELO} (chưa từng được cài đặt, không có nguồn dữ liệu).
	 */
	RANK("Theo hạng cơ thủ"),
	/**
	 * Xếp theo {@code Participant.seedNo} do BQT tự nhập (1 = hạt giống mạnh nhất).
	 *
	 * <p>Chế độ này từng có trước đây dưới tên {@code MANUAL} rồi bị gỡ vì thiếu ràng buộc duy nhất
	 * (tournament_id, seed_no) khiến dữ liệu seed trùng lộn xộn. Đã khôi phục kèm ràng buộc unique
	 * ở entity {@code Participant} và validate trùng seed ngay khi thêm/import — xem
	 * {@code ParticipantController#addManual} và {@code ParticipantExcelServiceImpl#validateRows}.
	 * Cho phép seed một phần: người chưa có seedNo được xáo ngẫu nhiên và xếp sau nhóm đã seed.
	 */
	SEED("Theo hạt giống");

	private final String displayName;

	public static boolean isValid(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		return Arrays.stream(values()).anyMatch(v -> v.name().equals(value));
	}
}
