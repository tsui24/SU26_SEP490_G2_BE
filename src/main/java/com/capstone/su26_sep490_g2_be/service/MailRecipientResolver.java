package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.enums.EmailRecipientType;

import java.util.List;

public interface MailRecipientResolver {

	/**
	 * Resolve danh sách người nhận theo recipient type cho 1 giải đấu (dùng cho gửi thủ công và
	 * các rule tự động dạng "broadcast": {@code ALL_PARTICIPANTS}, {@code REGISTRATION_USER},
	 * {@code ROLE_STAFF}, {@code CUSTOM_LIST}). Với {@code PLAYER}/{@code MATCH_PLAYERS} — người
	 * nhận đã xác định rõ ràng ngay tại nơi publish sự kiện, không cần resolve ở đây.
	 */
	List<MailRecipient> resolve(EmailRecipientType type, Long tournamentId, List<String> customEmails);
}
