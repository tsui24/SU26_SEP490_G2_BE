package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.EmailAutomationRule;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.EmailRecipientType;
import com.capstone.su26_sep490_g2_be.enums.EmailTriggerType;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.MailAutomationService;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.MailRecipientResolver;
import com.capstone.su26_sep490_g2_be.service.MailSendCommand;
import com.capstone.su26_sep490_g2_be.service.MailSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Lắng nghe {@link MailDomainEvent} sau khi transaction gốc commit, resolve rule + recipient rồi
 * giao cho {@link MailSendService}. Thêm event type mới chỉ cần thêm rule trong DB — không cần
 * sửa file này.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailAutomationEventListener {

	private final MailAutomationService mailAutomationService;
	private final MailRecipientResolver mailRecipientResolver;
	private final MailSendService mailSendService;
	private final TournamentRepository tournamentRepository;

	@Async("mailTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMailDomainEvent(MailDomainEvent event) {
		List<EmailAutomationRule> rules = mailAutomationService
				.resolveActiveRulesForEvent(event.eventType(), event.tournamentId());

		if (rules.isEmpty()) {
			log.debug("No active automation rule for event {} (tournamentId={})",
					event.eventType(), event.tournamentId());
			return;
		}

		for (EmailAutomationRule rule : rules) {
			dispatchForRule(rule, event);
		}
	}

	private void dispatchForRule(EmailAutomationRule rule, MailDomainEvent event) {
		List<MailRecipient> recipients = resolveRecipients(rule, event);
		if (recipients.isEmpty()) {
			log.info("Rule {} matched event {} but has no recipient — skip", rule.getCode(), event.eventType());
			return;
		}

		Tournament tournament = rule.getTournament() != null
				? rule.getTournament()
				: event.tournamentId() != null ? tournamentRepository.findById(event.tournamentId()).orElse(null) : null;

		for (MailRecipient recipient : recipients) {
			String idempotencyKey = event.entityKey() != null
					? rule.getCode() + ":" + event.entityKey() + ":" + recipient.email()
					: null;

			mailSendService.queueAndSend(MailSendCommand.builder()
					.template(rule.getTemplate())
					.recipientEmail(recipient.email())
					.recipientUserId(recipient.userId())
					.variables(event.variables())
					.triggerType(EmailTriggerType.AUTOMATION)
					.rule(rule)
					.tournament(tournament)
					.idempotencyKey(idempotencyKey)
					.build());
		}
	}

	private List<MailRecipient> resolveRecipients(EmailAutomationRule rule, MailDomainEvent event) {
		EmailRecipientType type = EmailRecipientType.valueOf(rule.getRecipientType());
		if ((type == EmailRecipientType.PLAYER || type == EmailRecipientType.MATCH_PLAYERS)
				&& event.explicitRecipients() != null) {
			return event.explicitRecipients();
		}
		if (event.explicitRecipients() != null && !event.explicitRecipients().isEmpty()) {
			return event.explicitRecipients();
		}
		return mailRecipientResolver.resolve(type, event.tournamentId(), null);
	}
}
