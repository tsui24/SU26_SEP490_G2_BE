package com.capstone.su26_sep490_g2_be.util;

import lombok.experimental.UtilityClass;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Toán học thuần cho bracket DOUBLE_ELIMINATION cắt sớm (CUT_TO_SE) — dùng chung giữa
 * {@code BracketGenerationServiceImpl} (sinh bracket thật) và {@code OwnerTournamentServiceImpl}
 * (xem trước danh sách vòng đấu sẽ thật sự diễn ra ở màn cấu hình/chi tiết giải).
 * <p>
 * Tách ra sau khi 2 nơi từng tính key vòng đấu theo 2 cách khác nhau (một bên theo cutoffRound —
 * số vòng SINH RA sau khi cắt sớm, một bên hiển thị NGUYÊN catalog 13 dòng của cả format bất kể
 * giải cắt ở đâu) khiến nhãn hiển thị ("Bán kết" cho vòng 4 trận) và danh sách cấu hình ("13 vòng"
 * cho giải chỉ thật sự đấu 6 vòng) đều sai lệch so với bracket thật.
 */
@UtilityClass
public class DoubleEliminationBracketMath {

	public static int nextPowerOf2(int n) {
		int p = 1;
		while (p < n) p <<= 1;
		return p;
	}

	public static int log2(int n) {
		return 31 - Integer.numberOfLeadingZeros(n);
	}

	/** Nhãn vòng nhánh thắng — TÍNH THEO tổng số vòng tự nhiên của bracketSize, KHÔNG phải cutoffRound. */
	public static String resolveWinnersRoundKey(int wr, int wNaturalTotalRounds) {
		return switch (wNaturalTotalRounds - wr) {
			case 0 -> "winners_final";
			case 1 -> "winners_sf";
			case 2 -> "winners_qf";
			default -> "winners_r1";
		};
	}

	/** Nhãn vòng nhánh thua — TÍNH THEO tổng số vòng tự nhiên (2*(log2(bracketSize)-1)), KHÔNG phải lCutoffRounds. */
	public static String resolveLosersRoundKey(int lr, int lNaturalTotalRounds) {
		if (lr == lNaturalTotalRounds) return "losers_final";
		return "losers_r" + Math.min(lr, 3);
	}

	public static String resolveSeRoundKey(int round, int totalRounds) {
		return switch (totalRounds - round) {
			case 0 -> "se_final";
			case 1 -> "se_semi_final";
			case 2 -> "se_quarter_final";
			default -> "se_round_1";
		};
	}

	/**
	 * Điểm cắt CUT_TO_SE — {@code se_phase_size} kẹp về khoảng hợp lệ [2, bracketSize/2] giống hệt
	 * {@code BracketGenerationServiceImpl#generateCutToSEDE}.
	 */
	public static int clampSeSize(int bracketSize, int requestedSeSize) {
		int seSize = nextPowerOf2(Math.max(2, requestedSeSize));
		int maxValidSeSize = Math.max(2, bracketSize / 2);
		return Math.min(seSize, maxValidSeSize);
	}

	/**
	 * Toàn bộ round-key SẼ THẬT SỰ được sinh ra cho một giải CUT_TO_SE cỡ {@code participantCount}
	 * người với cấu hình {@code sePhaseSize} — dùng để lọc danh sách "Số ván thắng theo vòng đấu"
	 * chỉ còn đúng số vòng tournament này thật sự đấu, thay vì toàn bộ catalog 13 dòng của format
	 * (catalog hỗ trợ mọi cỡ bracket từ 8 tới 32+ người, phần lớn không áp dụng cho một giải cụ thể).
	 */
	public static Set<String> realizedRoundKeys(int participantCount, int sePhaseSize) {
		int bracketSize = nextPowerOf2(Math.max(2, participantCount));
		int seSize = clampSeSize(bracketSize, sePhaseSize);
		int cutoffRound = log2(bracketSize / seSize) + 1;
		int lCutoffRounds = 2 * (cutoffRound - 1);
		int wNaturalRounds = log2(bracketSize);
		int lNaturalRounds = 2 * (wNaturalRounds - 1);
		int seTotalRounds = log2(seSize);

		Set<String> keys = new LinkedHashSet<>();
		for (int wr = 1; wr <= cutoffRound; wr++) {
			keys.add(resolveWinnersRoundKey(wr, wNaturalRounds));
		}
		for (int lr = 1; lr <= lCutoffRounds; lr++) {
			keys.add(resolveLosersRoundKey(lr, lNaturalRounds));
		}
		for (int sr = 1; sr <= seTotalRounds; sr++) {
			keys.add(resolveSeRoundKey(sr, seTotalRounds));
		}
		return keys;
	}
}
