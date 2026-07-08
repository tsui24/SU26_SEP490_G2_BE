package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;

public interface MailSendService {

	/**
	 * Render nội dung, ghi log trạng thái QUEUED trong transaction hiện tại, rồi giao việc gửi
	 * SMTP thật cho worker async. Nếu {@code idempotencyKey} trùng với 1 log tạo trong 5 phút gần
	 * nhất thì trả về log cũ, không gửi lại.
	 */
	EmailSendLog queueAndSend(MailSendCommand command);
}
