package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Sự kiện nghiệp vụ dùng chung cho toàn bộ automation email — service nghiệp vụ chỉ cần publish
 * event này qua {@link org.springframework.context.ApplicationEventPublisher}, không phụ thuộc
 * trực tiếp vào mail module. Thêm {@link EmailEventType} mới không cần sửa listener.
 */
@Builder
public record MailDomainEvent(
		EmailEventType eventType,
		Long tournamentId,
		Map<String, Object> variables,
		/** Null/rỗng để listener tự resolve theo recipientType của rule (broadcast). */
		List<MailRecipient> explicitRecipients,
		/** Định danh entity gây ra sự kiện (registration id, match id, ...) — dùng để chống gửi trùng. */
		String entityKey) {
}
