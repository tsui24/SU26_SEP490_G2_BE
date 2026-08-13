package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.StartupMailGuard;
import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailSendStatus;
import com.capstone.su26_sep490_g2_be.repository.EmailSendLogRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.EmailQueuedEvent;
import com.capstone.su26_sep490_g2_be.service.MailRenderService;
import com.capstone.su26_sep490_g2_be.service.MailSendCommand;
import com.capstone.su26_sep490_g2_be.service.MailSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailSendServiceImpl implements MailSendService {

	private static final int IDEMPOTENCY_WINDOW_MINUTES = 5;

	private final MailRenderService mailRenderService;
	private final EmailSendLogRepository emailSendLogRepository;
	private final UserRepository userRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final StartupMailGuard startupMailGuard;

	@Override
	@Transactional
	public EmailSendLog queueAndSend(MailSendCommand command) {
		if (startupMailGuard.isSuppressed()) {
			log.debug("Skip queueAndSend during seed — to={}", command.recipientEmail());
			return null;
		}
		if (command.idempotencyKey() != null) {
			Instant window = Instant.now().minus(IDEMPOTENCY_WINDOW_MINUTES, ChronoUnit.MINUTES);
			var existing = emailSendLogRepository
					.findFirstByIdempotencyKeyAndCreatedAtAfter(command.idempotencyKey(), window);
			if (existing.isPresent()) {
				log.info("Skip duplicate send — idempotencyKey={} already queued/sent", command.idempotencyKey());
				return existing.get();
			}
		}

		RenderedEmailResponse rendered = command.renderedOverride() != null
				? command.renderedOverride()
				: mailRenderService.render(command.template(), command.variables());

		User recipientUser = command.recipientUserId() != null
				? userRepository.findById(command.recipientUserId()).orElse(null)
				: null;
		User createdBy = command.createdByUserId() != null
				? userRepository.findById(command.createdByUserId()).orElse(null)
				: null;

		EmailSendLog emailLog = emailSendLogRepository.save(EmailSendLog.builder()
				.template(command.template())
				.rule(command.rule())
				.tournament(command.tournament())
				.triggerType(command.triggerType().getValue())
				.recipientEmail(command.recipientEmail())
				.recipientUser(recipientUser)
				.subjectRendered(rendered.getSubject())
				.bodyRendered(rendered.getBodyHtml())
				.status(EmailSendStatus.QUEUED.getValue())
				.idempotencyKey(command.idempotencyKey())
				.createdBy(createdBy)
				.build());

		eventPublisher.publishEvent(new EmailQueuedEvent(emailLog.getId()));
		return emailLog;
	}
}
