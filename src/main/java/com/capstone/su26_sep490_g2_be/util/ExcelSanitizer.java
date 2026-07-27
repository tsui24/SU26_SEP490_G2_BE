package com.capstone.su26_sep490_g2_be.util;

/**
 * Chống CSV/Excel-formula injection: Excel/LibreOffice/Sheets tự động thực thi giá trị cell bắt đầu
 * bằng {@code =}, {@code +}, {@code -}, {@code @} như công thức. Tournament name, branch name, player
 * name... đều do Owner/Manager/Player nhập (không đáng tin tuyệt đối) rồi được xuất thẳng ra Excel —
 * phải escape trước khi ghi vào cell string.
 */
public final class ExcelSanitizer {

	private ExcelSanitizer() {}

	public static String sanitize(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		char first = value.charAt(0);
		if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
			return "'" + value;
		}
		return value;
	}
}
