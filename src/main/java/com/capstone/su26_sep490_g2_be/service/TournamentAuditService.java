package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.TournamentStatusHistoryResponse;
import com.capstone.su26_sep490_g2_be.entity.Tournament;

import java.util.List;

public interface TournamentAuditService {

	/**
	 * Ghi nhận một lần đổi trạng thái giải đấu.
	 * changedByUserId != null → MANUAL (do người dùng thao tác); null → AUTO (hệ thống tự động).
	 */
	void recordChange(Tournament tournament, String fromStatus, String toStatus, Long changedByUserId, String note);

	/** Ghi nhận cảnh báo (không đổi trạng thái) — vd. quá hạn bắt đầu nhưng bracket chưa sẵn sàng. */
	void recordWarning(Tournament tournament, String note);

	/** True nếu giải đấu đã có ít nhất 1 cảnh báo được ghi nhận (tránh spam lặp lại). */
	boolean hasExistingWarning(Long tournamentId);

	List<TournamentStatusHistoryResponse> getHistory(Long tournamentId);
}
