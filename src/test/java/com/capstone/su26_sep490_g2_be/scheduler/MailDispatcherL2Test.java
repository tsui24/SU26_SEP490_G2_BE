package com.capstone.su26_sep490_g2_be.scheduler;

import com.capstone.su26_sep490_g2_be.config.MailProperties;
import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import com.capstone.su26_sep490_g2_be.enums.EmailSendStatus;
import com.capstone.su26_sep490_g2_be.enums.EmailTriggerType;
import com.capstone.su26_sep490_g2_be.repository.EmailSendLogRepository;
import com.capstone.su26_sep490_g2_be.service.EmailQueuedEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * BJ-06 MailDispatcher — không dùng {@code @Transactional} trên class để AFTER_COMMIT thật sự chạy.
 * Dọn log tay trong {@link AfterEach}.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = {
				"spring.task.scheduling.enabled=false",
				"management.health.mail.enabled=false"
		})
class MailDispatcherL2Test {

	@MockitoBean
	private JavaMailSender mailSender;

	@Autowired private EmailSendLogRepository emailSendLogRepository;
	@Autowired private org.springframework.context.ApplicationEventPublisher eventPublisher;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private MailProperties mailProperties;

	private Long createdLogId;
	private String previousFrom;

	@BeforeEach
	void stubMail() {
		previousFrom = mailProperties.getFromAddress();
		mailProperties.setFromAddress("l2-scheduler@btms.test");
		when(mailSender.createMimeMessage())
				.thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
		doNothing().when(mailSender).send(any(MimeMessage.class));
	}

	@AfterEach
	void cleanup() {
		if (createdLogId != null) {
			emailSendLogRepository.deleteById(createdLogId);
			createdLogId = null;
		}
		mailProperties.setFromAddress(previousFrom);
	}

	/** TC-SCH-BJ06-001 */
	@Test
	void onEmailQueued_afterCommit_marksLogSent() throws Exception {
		// Nếu code sai: hệ thống ghi "đã xếp hàng gửi thư" nhưng không bao giờ đánh dấu đã gửi, BTC tưởng thư đã tới.
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		createdLogId = tx.execute(status -> {
			EmailSendLog log = emailSendLogRepository.save(EmailSendLog.builder()
					.triggerType(EmailTriggerType.AUTOMATION.getValue())
					.recipientEmail("bj06-l2@example.com")
					.subjectRendered("L2 BJ-06 subject")
					.bodyRendered("<p>L2 BJ-06 body</p>")
					.status(EmailSendStatus.QUEUED.getValue())
					.build());
			eventPublisher.publishEvent(new EmailQueuedEvent(log.getId()));
			return log.getId();
		});

		EmailSendLog seen = null;
		for (int i = 0; i < 40; i++) {
			seen = emailSendLogRepository.findById(createdLogId).orElseThrow();
			if (EmailSendStatus.SENT.getValue().equals(seen.getStatus())
					|| EmailSendStatus.FAILED.getValue().equals(seen.getStatus())) {
				break;
			}
			Thread.sleep(250);
		}
		assertNotNull(seen);
		assertEquals(EmailSendStatus.SENT.getValue(), seen.getStatus());
		assertNotNull(seen.getSentAt());
	}
}
