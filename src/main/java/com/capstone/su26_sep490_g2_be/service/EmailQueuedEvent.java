package com.capstone.su26_sep490_g2_be.service;

/**
 * Publish sau khi {@code EmailSendLog} đã được lưu, để việc gửi SMTP thật chỉ chạy sau khi
 * transaction lưu log đã commit (tránh race: async đọc log trước khi transaction ghi xong).
 */
public record EmailQueuedEvent(Long emailLogId) {
}
