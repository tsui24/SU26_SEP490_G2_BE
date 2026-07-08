package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailAutomationRule;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.EmailTriggerType;
import lombok.Builder;

import java.util.Map;

/**
 * {@code template} luôn phải là entity đã persist (dùng làm FK audit trong log). Khi nội dung
 * gửi khác với template gốc (Owner/Manager override subject/body khi gửi thủ công), truyền sẵn
 * {@code renderedOverride} — {@link com.capstone.su26_sep490_g2_be.service.MailSendService} sẽ
 * dùng nội dung này thay vì tự render lại từ {@code template}.
 */
@Builder
public record MailSendCommand(
		EmailTemplate template,
		String recipientEmail,
		Long recipientUserId,
		Map<String, Object> variables,
		EmailTriggerType triggerType,
		EmailAutomationRule rule,
		Tournament tournament,
		String idempotencyKey,
		Long createdByUserId,
		RenderedEmailResponse renderedOverride) {
}
