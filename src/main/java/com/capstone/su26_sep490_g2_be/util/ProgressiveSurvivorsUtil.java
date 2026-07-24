package com.capstone.su26_sep490_g2_be.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiện ích parse & validate cấu hình {@code pe_survivors_per_stage} của thể thức
 * PROGRESSIVE_ROUND_ROBIN — danh sách số người còn lại SAU mỗi giai đoạn vòng tròn.
 *
 * <p>Ví dụ 16 người, CSV "10,6,4": GĐ1 (16→10), GĐ2 (10→6), GĐ3 (6→4) rồi 4 người vào Playoff.
 * Số giai đoạn League = số phần tử; phần tử cuối == số người vào Playoff.
 */
public final class ProgressiveSurvivorsUtil {

	private ProgressiveSurvivorsUtil() {}

	/**
	 * Parse CSV thành danh sách số nguyên. Ném {@link IllegalArgumentException} nếu chuỗi rỗng
	 * hoặc chứa phần tử không phải số nguyên.
	 */
	public static List<Integer> parse(String csv) {
		if (csv == null || csv.isBlank()) {
			throw new IllegalArgumentException("Danh sách số người đi tiếp không được để trống");
		}
		List<Integer> result = new ArrayList<>();
		for (String token : csv.split(",")) {
			String trimmed = token.trim();
			if (trimmed.isEmpty()) continue;
			try {
				result.add(Integer.parseInt(trimmed));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Danh sách số người đi tiếp chứa giá trị không phải số nguyên: " + trimmed);
			}
		}
		if (result.isEmpty()) {
			throw new IllegalArgumentException("Danh sách số người đi tiếp không được để trống");
		}
		return result;
	}

	/**
	 * Kiểm tra tính hợp lệ của cấu hình survivors. Trả về danh sách thông báo lỗi tiếng Việt
	 * (rỗng nếu hợp lệ). Quy tắc:
	 * <ul>
	 *   <li>maxParticipants &gt; phần tử đầu tiên (giai đoạn đầu phải loại ít nhất 1 người)</li>
	 *   <li>Giảm dần nghiêm ngặt</li>
	 *   <li>Mọi phần tử là số chẵn và &ge; 4</li>
	 *   <li>Phần tử cuối == playoffSize</li>
	 * </ul>
	 */
	public static List<String> validate(List<Integer> survivors, int maxParticipants, int playoffSize) {
		List<String> errors = new ArrayList<>();

		if (survivors.isEmpty()) {
			errors.add("Danh sách số người đi tiếp không được để trống");
			return errors;
		}

		int first = survivors.get(0);
		if (maxParticipants <= first) {
			errors.add("Số người tham gia (" + maxParticipants + ") phải lớn hơn số người đi tiếp giai đoạn đầu (" + first + ")");
		}

		for (int i = 0; i < survivors.size(); i++) {
			int v = survivors.get(i);
			if (v < 4) {
				errors.add("Mỗi giai đoạn phải giữ lại ít nhất 4 người (phần tử thứ " + (i + 1) + " = " + v + ")");
			}
			if (v % 2 != 0) {
				errors.add("Mọi số người đi tiếp phải là số chẵn (phần tử thứ " + (i + 1) + " = " + v + ")");
			}
			if (i > 0 && v >= survivors.get(i - 1)) {
				errors.add("Danh sách số người đi tiếp phải giảm dần nghiêm ngặt (" + survivors.get(i - 1) + " → " + v + ")");
			}
		}

		int last = survivors.get(survivors.size() - 1);
		if (last != playoffSize) {
			errors.add("Phần tử cuối của danh sách (" + last + ") phải bằng số người vào Playoff (" + playoffSize + ")");
		}

		return errors;
	}
}
