package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplatePreviewRequest;
import com.capstone.su26_sep490_g2_be.dto.request.ManualSendEmailRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmailAutomationRuleResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailSendLogResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ManualSendResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailAutomationRule;
import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.EmailRecipientType;
import com.capstone.su26_sep490_g2_be.enums.EmailSendStatus;
import com.capstone.su26_sep490_g2_be.enums.EmailTriggerType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.EmailAutomationRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.EmailSendLogRepository;
import com.capstone.su26_sep490_g2_be.repository.EmailTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.MailAutomationService;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.MailRecipientResolver;
import com.capstone.su26_sep490_g2_be.service.MailRenderService;
import com.capstone.su26_sep490_g2_be.service.MailSendCommand;
import com.capstone.su26_sep490_g2_be.service.MailSendService;
import com.capstone.su26_sep490_g2_be.service.TournamentEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TournamentEmailServiceImpl implements TournamentEmailService {

	private final TournamentRepository tournamentRepository;
	private final EmailTemplateRepository emailTemplateRepository;
	private final EmailAutomationRuleRepository emailAutomationRuleRepository;
	private final EmailSendLogRepository emailSendLogRepository;
	private final RegistrationRepository registrationRepository;
	private final PaymentRepository paymentRepository;
	private final MailRecipientResolver mailRecipientResolver;
	private final MailRenderService mailRenderService;
	private final MailSendService mailSendService;
	private final MailAutomationService mailAutomationService;
	private final MailContextBuilder mailContextBuilder;

	@Override
	@Transactional(readOnly = true)
	public RenderedEmailResponse preview(Long userId, Long tournamentId, boolean enforceOwnership,
			EmailTemplatePreviewRequest request) {
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		request.setTournamentId(tournament.getId());
		Map<String, Object> context = new HashMap<>(
				mailContextBuilder.previewContext(tournament.getId(), request.getSampleRegistrationId()));
		if (request.getVariables() != null) {
			context.putAll(request.getVariables());
		}
		EmailTemplate template = request.getTemplateId() != null
				? emailTemplateRepository.findById(request.getTemplateId())
						.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND))
				: emailTemplateRepository.findByCode(request.getTemplateCode())
						.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));
		return mailRenderService.render(template, context);
	}

	@Override
	@Transactional
	public ManualSendResultResponse sendManual(Long userId, Long tournamentId, boolean enforceOwnership,
			ManualSendEmailRequest request) {
		Tournament tournament = loadTournament(userId, tournamentId, enforceOwnership);
		EmailTemplate template = emailTemplateRepository.findById(request.getTemplateId())
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));
		if (!Boolean.TRUE.equals(template.getIsActive())) {
			throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_INACTIVE);
		}

		EmailRecipientType recipientType = EmailRecipientType.valueOf(request.getRecipientType());
		List<MailRecipient> recipients = mailRecipientResolver
				.resolve(recipientType, tournament.getId(), request.getRecipientEmails());
		if (recipients.isEmpty()) {
			throw new BusinessException(ErrorCode.EMAIL_RECIPIENT_EMPTY);
		}

		Map<String, Object> baseVariables = new HashMap<>(mailContextBuilder.systemContext());
		mailContextBuilder.putTournament(baseVariables, tournament);
		if (request.getVariables() != null) {
			baseVariables.putAll(request.getVariables());
		}

		// Override tạm thời chỉ dùng để render — FK log vẫn trỏ về template thật (đã persist).
		boolean hasOverride = request.getSubjectOverride() != null || request.getBodyOverride() != null;
		EmailTemplate renderTemplate = hasOverride
				? EmailTemplate.builder()
						.code(template.getCode())
						.subjectTemplate(request.getSubjectOverride() != null
								? request.getSubjectOverride() : template.getSubjectTemplate())
						.bodyHtmlTemplate(request.getBodyOverride() != null
								? request.getBodyOverride() : template.getBodyHtmlTemplate())
						.build()
				: null;

		for (MailRecipient recipient : recipients) {
			Map<String, Object> variables = new HashMap<>(baseVariables);
			enrichWithRegistration(variables, recipient.registrationId());

			mailSendService.queueAndSend(MailSendCommand.builder()
					.template(template)
					.recipientEmail(recipient.email())
					.recipientUserId(recipient.userId())
					.variables(variables)
					.renderedOverride(hasOverride ? mailRenderService.render(renderTemplate, variables) : null)
					.triggerType(EmailTriggerType.MANUAL)
					.tournament(tournament)
					.createdByUserId(userId)
					.build());
		}

		return ManualSendResultResponse.builder().queuedCount(recipients.size()).build();
	}

	/** Điền {{registration.*}}/{{user.*}}/{{payment.*}} khi người nhận có gắn 1 registration thật trong giải này. */
	private void enrichWithRegistration(Map<String, Object> variables, Long registrationId) {
		if (registrationId == null) return;
		Registration registration = registrationRepository.findById(registrationId).orElse(null);
		if (registration == null) return;
		mailContextBuilder.putRegistration(variables, registration);
		paymentRepository
				.findFirstByRegistrationIdAndStatusOrderByPaidAtDesc(registrationId, PaymentStatus.SUCCESS.getValue())
				.ifPresent(payment -> mailContextBuilder.putPayment(variables, payment));
	}

	@Override
	@Transactional(readOnly = true)
	public List<EmailAutomationRuleResponse> listAutomationRules(Long userId, Long tournamentId,
			boolean enforceOwnership) {
		loadTournament(userId, tournamentId, enforceOwnership);
		return mailAutomationService.listRulesForTournament(tournamentId);
	}

	@Override
	@Transactional
	public EmailAutomationRuleResponse setRuleEnabled(Long userId, Long tournamentId, boolean enforceOwnership,
			Long ruleId, boolean enabled) {
		loadTournament(userId, tournamentId, enforceOwnership);
		EmailAutomationRule rule = emailAutomationRuleRepository.findById(ruleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_RULE_NOT_FOUND));
		if (rule.getTournament() != null && !rule.getTournament().getId().equals(tournamentId)) {
			throw new BusinessException(ErrorCode.EMAIL_RULE_NOT_FOUND);
		}
		return mailAutomationService.setEnabled(ruleId, enabled);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<EmailSendLogResponse> listLogs(Long userId, Long tournamentId, boolean enforceOwnership,
			String status, int page, int size) {
		loadTournament(userId, tournamentId, enforceOwnership);
		var result = (status == null || status.isBlank())
				? emailSendLogRepository.findByTournamentId(tournamentId, PageRequest.of(page, size))
				: emailSendLogRepository.findByTournamentIdAndStatus(tournamentId, status, PageRequest.of(page, size));
		return PageResponse.of(result, this::toResponse);
	}

	private Tournament loadTournament(Long userId, Long tournamentId, boolean enforceOwnership) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (enforceOwnership && !tournament.getCreatedBy().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		return tournament;
	}

	private EmailSendLogResponse toResponse(EmailSendLog log) {
		return EmailSendLogResponse.builder()
				.id(log.getId())
				.templateCode(log.getTemplate() != null ? log.getTemplate().getCode() : null)
				.templateName(log.getTemplate() != null ? log.getTemplate().getName() : null)
				.ruleCode(log.getRule() != null ? log.getRule().getCode() : null)
				.tournamentId(log.getTournament() != null ? log.getTournament().getId() : null)
				.tournamentName(log.getTournament() != null ? log.getTournament().getName() : null)
				.triggerType(log.getTriggerType())
				.recipientEmail(log.getRecipientEmail())
				.subjectRendered(log.getSubjectRendered())
				.status(log.getStatus())
				.statusDisplayName(EmailSendStatus.valueOf(log.getStatus()).getDisplayName())
				.errorMessage(log.getErrorMessage())
				.sentAt(log.getSentAt())
				.createdAt(log.getCreatedAt())
				.build();
	}
}
