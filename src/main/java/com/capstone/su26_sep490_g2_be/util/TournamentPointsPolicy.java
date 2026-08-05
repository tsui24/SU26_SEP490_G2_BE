package com.capstone.su26_sep490_g2_be.util;

import lombok.experimental.UtilityClass;

/**
 * Công thức quy đổi thứ hạng chung cuộc → điểm tích lũy của cơ thủ.
 *
 * <p>Nguồn duy nhất cho cả luồng thật ({@code TournamentResultServiceImpl}) và data seed demo
 * ({@code TournamentSeedInitializer}) — trước đây công thức nằm private trong seed nên luồng
 * thật luôn ghi 0 điểm.
 *
 * <p>Điểm = {@link #basePoints(int)} × {@link #sizeMultiplier(int)}. Hệ số quy mô để vô địch một
 * giải 64 người có giá trị hơn vô địch giải 8 người khi gom nhiều giải vào cùng bảng xếp hạng.
 *
 * <p>Lưu ý về mốc bậc điểm (4, 8, 16): trùng khít ranh giới nhóm đồng hạng của bracket
 * (#3-4, #5-8, #9-16). Nhờ vậy việc rải hạng cho hết trùng (2 người thua bán kết thành 3 và 4 thay
 * vì cùng 3) không bao giờ đẩy ai sang bậc điểm khác — truyền {@code finalRank} đã rải vào đây
 * vẫn cho kết quả đúng như truyền hạng gốc của nhóm.
 */
@UtilityClass
public class TournamentPointsPolicy {

	/** Điểm cơ bản theo thứ hạng chung cuộc, chưa nhân hệ số quy mô. */
	public static int basePoints(int finalRank) {
		if (finalRank == 1) return 100;
		if (finalRank == 2) return 70;
		if (finalRank <= 4) return 50;
		if (finalRank <= 8) return 30;
		if (finalRank <= 16) return 15;
		return 3;
	}

	/** Hệ số theo số cơ thủ tham gia — giải càng đông càng giá trị. */
	public static double sizeMultiplier(int participantCount) {
		if (participantCount >= 64) return 1.5;
		if (participantCount >= 32) return 1.2;
		if (participantCount >= 16) return 1.0;
		return 0.8;
	}

	/**
	 * Điểm cuối cùng cơ thủ nhận được ở một giải.
	 *
	 * @param finalRank        thứ hạng chung cuộc (1 = vô địch)
	 * @param participantCount số cơ thủ tham gia giải, dùng cho hệ số quy mô
	 */
	public static int compute(int finalRank, int participantCount) {
		return (int) Math.round(basePoints(finalRank) * sizeMultiplier(participantCount));
	}
}
