package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * Hạng cơ thủ theo hệ phân hạng bi-a Việt Nam.
 *
 * <p><b>Thứ tự khai báo = thứ tự mạnh → yếu</b>, nên {@link #ordinal()} dùng trực tiếp làm bậc khi
 * xếp hạt giống ({@code SeedingMethod.RANK}). Không đổi thứ tự các hằng số nếu chưa rà lại
 * {@code BracketGenerationServiceImpl.resolveRankOrder()}.
 *
 * <p>Giá trị lưu trong DB là {@code name()} ở hai nơi: {@code user_profiles.billiard_rank}
 * (cơ thủ tự khai trên hồ sơ) và {@code participants.billiard_rank} (ảnh chụp lúc tạo participant).
 *
 * <p>Nhóm I→L là hạng nội bộ CLB — sinh ra do CLB quá đông cần chia thêm giải cho người mới,
 * ranh giới giữa chúng mang tính địa phương chứ không phải mức trình độ chuẩn hoá.
 *
 * <p>Trùng hạng giữa nhiều cơ thủ là chuyện bình thường, không phải lỗi.
 */
@Getter
@RequiredArgsConstructor
public enum BilliardRank {

	CHAMPION("CN", "Tuyển quốc gia/tỉnh, cơ thủ chuyên nghiệp, thi đấu WNT"),
	A("A", "Bán chuyên — top CLB, top tỉnh; break-run cao, chiến thuật tốt"),
	B("B", "Chạy bàn tương đối, điều bi khá, tâm lý chưa ổn định"),
	C("C", "Có kinh nghiệm, đánh đúng kỹ thuật, điều bi cơ bản"),
	D("D", "Phong trào — mới lên trình"),
	E("E", "Phong trào — đánh được cơ bản"),
	F("F", "Phong trào — chơi khoảng vài tháng"),
	G("G", "Phong trào — người mới"),
	H("H", "Phong trào"),
	I("I", "Hạng nội bộ CLB"),
	J("J", "Hạng nội bộ CLB"),
	K("K", "Hạng nội bộ CLB"),
	L("L", "Hạng nội bộ CLB"),
	UNKNOWN("Chưa xếp hạng", "Chưa khai hạng hoặc không rõ trình độ");

	private final String displayName;
	private final String description;

	public String getValue() {
		return name();
	}

	/** Đã khai hạng thật hay chưa — dùng để tách nhóm khi xếp hạt giống. */
	public boolean isRanked() {
		return this != UNKNOWN;
	}

	/**
	 * Khớp theo {@code name()} HOẶC mã hiển thị — người dùng gõ "CN" trong Excel/form phải nhận
	 * ra là {@link #CHAMPION}, vì toàn bộ giao diện dùng mã "CN" chứ không dùng "CHAMPION".
	 */
	private boolean matches(String raw) {
		return name().equalsIgnoreCase(raw) || displayName.equalsIgnoreCase(raw);
	}

	public static boolean isValid(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		String raw = value.trim();
		return Arrays.stream(values()).anyMatch(v -> v.matches(raw));
	}

	/** Chuỗi rỗng / null / giá trị lạ đều quy về {@link #UNKNOWN} để không làm vỡ luồng bốc thăm. */
	public static BilliardRank fromNullable(String value) {
		if (value == null || value.isBlank()) {
			return UNKNOWN;
		}
		String raw = value.trim();
		return Arrays.stream(values())
				.filter(v -> v.matches(raw))
				.findFirst()
				.orElse(UNKNOWN);
	}
}
