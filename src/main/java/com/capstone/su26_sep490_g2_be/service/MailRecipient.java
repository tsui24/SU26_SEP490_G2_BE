package com.capstone.su26_sep490_g2_be.service;

/**
 * userId có thể null khi email không gắn với tài khoản nào (vd. custom list nhập tay không khớp user có sẵn).
 * registrationId có thể null khi người nhận chưa đăng ký giải đấu này (vd. nhân viên, hoặc email tự nhập
 * không khớp registration nào) — dùng để điền các biến {{registration.*}} / {{payment.*}} khi gửi thủ công.
 */
public record MailRecipient(Long userId, String email, Long registrationId) {

	public MailRecipient(Long userId, String email) {
		this(userId, email, null);
	}
}
