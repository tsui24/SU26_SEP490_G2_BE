package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MailProperties;
import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import com.capstone.su26_sep490_g2_be.enums.EmailSendStatus;
import com.capstone.su26_sep490_g2_be.repository.EmailSendLogRepository;
import com.capstone.su26_sep490_g2_be.service.EmailQueuedEvent;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Gửi SMTP thật. Lắng nghe {@link EmailQueuedEvent} thay vì được gọi trực tiếp từ
 * {@link MailSendServiceImpl}, để đảm bảo chỉ dispatch SAU KHI transaction lưu
 * {@code EmailSendLog} đã commit — gọi thẳng {@code @Async} method từ trong 1 method
 * {@code @Transactional} sẽ sinh race condition (thread async đọc DB trước khi commit xong).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailDispatcher {

	private final JavaMailSender mailSender;
	private final MailProperties mailProperties;
	private final EmailSendLogRepository emailSendLogRepository;

	@Async("mailTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onEmailQueued(EmailQueuedEvent event) {
		Long emailLogId = event.emailLogId();
		EmailSendLog emailLog = emailSendLogRepository.findById(emailLogId).orElse(null);
		if (emailLog == null) {
			log.warn("EmailSendLog {} not found — skip dispatch", emailLogId);
			return;
		}

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(emailLog.getRecipientEmail());
			helper.setSubject(emailLog.getSubjectRendered());
			helper.setText(emailLog.getBodyRendered(), true);
			helper.setFrom(mailProperties.getFromAddress(), mailProperties.getFromName());

			mailSender.send(message);
			markResult(emailLog, EmailSendStatus.SENT, null);
			log.info("Email sent to {} (logId={})", emailLog.getRecipientEmail(), emailLog.getId());
		} catch (Exception e) {
			log.error("Send email failed (logId={})", emailLog.getId(), e);
			markResult(emailLog, EmailSendStatus.FAILED, e.getMessage());
		}
	}

	/** Mỗi lần gọi save() đi qua repository proxy nên tự có transaction riêng — không cần bọc thêm. */
	private void markResult(EmailSendLog emailLog, EmailSendStatus status, String errorMessage) {
		emailLog.setStatus(status.getValue());
		emailLog.setErrorMessage(errorMessage);
		emailLog.setSentAt(status == EmailSendStatus.SENT ? Instant.now() : null);
		emailSendLogRepository.save(emailLog);
	}
}
