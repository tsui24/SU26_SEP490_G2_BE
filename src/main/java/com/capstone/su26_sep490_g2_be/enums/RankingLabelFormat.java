package com.capstone.su26_sep490_g2_be.enums;

/**
 * Định dạng nhãn hạng hiển thị kiểu WNT: {@code #1}, {@code #2}, {@code #3-4}, {@code #5-8}...
 * Tách riêng để tránh hard-code ký tự {@code #} rải rác trong service và FE.
 */
public final class RankingLabelFormat {

	public static final String PREFIX = "#";
	public static final String CHAMPION_LABEL = PREFIX + "1";

	private RankingLabelFormat() {
	}

	/** Một hạng cố định, ví dụ hạng 1 → {@code #1}. */
	public static String single(int rank) {
		return PREFIX + rank;
	}

	/**
	 * Một hạng hoặc khoảng hạng khi nhiều người cùng vòng bị loại.
	 * Ví dụ: from=3, to=4 → {@code #3-4}; from=5, to=5 → {@code #5}.
	 */
	public static String range(int from, int to) {
		if (from == to) {
			return single(from);
		}
		return PREFIX + from + "-" + to;
	}
}
