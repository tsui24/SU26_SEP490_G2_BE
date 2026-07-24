package com.capstone.su26_sep490_g2_be.service;

/**
 * Tự động gán bàn (1..tableCount) và ước lượng giờ thi đấu cho toàn bộ trận của giải.
 * Chạy lúc sinh bracket và mỗi khi có trận kết thúc (reactive). Giờ chỉ được LÙI, không nhảy sớm;
 * trận đã chỉnh tay ({@code scheduleLocked}) được giữ nguyên.
 */
public interface MatchSchedulingService {

	/** Tính lại bàn + giờ dự kiến cho tất cả trận chưa khóa lịch của giải. Không ném lỗi ra ngoài. */
	void reschedule(Long tournamentId);

	/** Ước lượng thời lượng (phút) của 1 trận theo race-to + loại bi — dùng khi gán tay để tính giờ kết thúc. */
	long estimateDurationMinutes(com.capstone.su26_sep490_g2_be.entity.Match match);
}
