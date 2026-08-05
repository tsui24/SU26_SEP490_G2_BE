package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;

/**
 * Kỳ thống kê của bảng xếp hạng điểm tích lũy.
 *
 * <p>Khoảng thời gian luôn tính theo lịch Việt Nam ({@link #ZONE}) rồi mới đổi sang
 * {@link Instant} — DB chạy {@code serverTimezone=UTC} nên nếu cắt mốc theo UTC thì "tháng 8"
 * sẽ lệch 7 tiếng so với cảm nhận người dùng.
 */
@Getter
@RequiredArgsConstructor
public enum LeaderboardPeriod {

	MONTH("Theo tháng"),
	QUARTER("Theo quý"),
	YEAR("Theo năm"),
	ALL("Tất cả");

	/** Múi giờ nghiệp vụ — mọi mốc kỳ thống kê cắt theo lịch VN. */
	public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private final String displayName;

	public String getValue() {
		return name();
	}

	/**
	 * Quy đổi kỳ thống kê thành khoảng nửa mở {@code [from, to)}.
	 *
	 * @param year    năm; bỏ qua khi kỳ là {@link #ALL}, mặc định năm hiện tại nếu null
	 * @param quarter quý 1-4, chỉ dùng cho {@link #QUARTER}; mặc định quý hiện tại nếu null
	 * @param month   tháng 1-12, chỉ dùng cho {@link #MONTH}; mặc định tháng hiện tại nếu null
	 */
	public Range resolve(Integer year, Integer quarter, Integer month) {
		LocalDate today = LocalDate.now(ZONE);
		int y = year != null ? year : today.getYear();

		return switch (this) {
			case ALL -> new Range(
					Instant.EPOCH,
					today.plusYears(1).withDayOfYear(1).atStartOfDay(ZONE).toInstant());
			case YEAR -> ofDates(LocalDate.of(y, 1, 1), LocalDate.of(y + 1, 1, 1));
			case QUARTER -> {
				int q = clamp(quarter != null ? quarter : (today.getMonthValue() - 1) / 3 + 1, 1, 4);
				LocalDate start = LocalDate.of(y, (q - 1) * 3 + 1, 1);
				yield ofDates(start, start.plusMonths(3));
			}
			case MONTH -> {
				int m = clamp(month != null ? month : today.getMonthValue(), 1, 12);
				LocalDate start = LocalDate.of(y, m, 1);
				yield ofDates(start, start.plusMonths(1));
			}
		};
	}

	/** Nhãn hiển thị của kỳ đang xem, vd "Quý 3/2026". */
	public String label(Integer year, Integer quarter, Integer month) {
		LocalDate today = LocalDate.now(ZONE);
		int y = year != null ? year : today.getYear();
		return switch (this) {
			case ALL -> "Mọi thời điểm";
			case YEAR -> "Năm " + y;
			case QUARTER -> "Quý " + clamp(quarter != null ? quarter
					: (today.getMonthValue() - 1) / 3 + 1, 1, 4) + "/" + y;
			case MONTH -> "Tháng " + clamp(month != null ? month : today.getMonthValue(), 1, 12) + "/" + y;
		};
	}

	private static Range ofDates(LocalDate fromInclusive, LocalDate toExclusive) {
		return new Range(
				fromInclusive.atStartOfDay(ZONE).toInstant(),
				toExclusive.atStartOfDay(ZONE).toInstant());
	}

	private static int clamp(int value, int min, int max) {
		return Math.min(Math.max(value, min), max);
	}

	/** Khoảng thời gian nửa mở: tính từ {@code from} (bao gồm) đến {@code to} (không bao gồm). */
	public record Range(Instant from, Instant to) {
	}

	/** Parse an toàn từ query param — giá trị lạ/null đều rơi về {@link #ALL}. */
	public static LeaderboardPeriod from(String raw) {
		if (raw == null || raw.isBlank()) {
			return ALL;
		}
		try {
			return valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return ALL;
		}
	}

	/** Năm nhỏ nhất có dữ liệu để FE dựng dropdown — trước đó chắc chắn chưa có giải nào. */
	public static int earliestSelectableYear() {
		return 2024;
	}

	/** Năm hiện tại theo lịch VN. */
	public static int currentYear() {
		return Year.now(ZONE).getValue();
	}
}
