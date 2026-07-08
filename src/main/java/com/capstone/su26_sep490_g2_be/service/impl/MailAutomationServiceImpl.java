package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.EmailAutomationRuleRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmailAutomationRuleResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailAutomationRule;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.EmailRecipientType;
import com.capstone.su26_sep490_g2_be.enums.EmailScope;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.EmailAutomationRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.EmailTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MailAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MailAutomationServiceImpl implements MailAutomationService {

	private final EmailAutomationRuleRepository ruleRepository;
	private final EmailTemplateRepository templateRepository;
	private final TournamentRepository tournamentRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<EmailAutomationRuleResponse> listGlobalRules(int page, int size) {
		List<EmailAutomationRule> all = ruleRepository.findByScope(EmailScope.GLOBAL.getValue());
		List<EmailAutomationRuleResponse> mapped = all.stream().map(this::toResponse).toList();
		return PageResponse.of(mapped, page, size);
	}

	@Override
	@Transactional(readOnly = true)
	public List<EmailAutomationRuleResponse> listRulesForTournament(Long tournamentId) {
		List<EmailAutomationRule> rules = ruleRepository.findByTournamentId(tournamentId);
		List<EmailAutomationRuleResponse> result = new java.util.ArrayList<>(
				rules.stream().map(this::toResponse).toList());
		result.addAll(ruleRepository.findByScope(EmailScope.GLOBAL.getValue()).stream()
				.map(this::toResponse)
				.toList());
		return result;
	}

	@Override
	@Transactional
	public EmailAutomationRuleResponse createRule(Long userId, EmailAutomationRuleRequest request) {
		if (ruleRepository.existsByCode(request.getCode())) {
			throw new BusinessException(ErrorCode.EMAIL_RULE_CODE_EXISTS);
		}
		EmailTemplate template = templateRepository.findById(request.getTemplateId())
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));
		Tournament tournament = request.getTournamentId() != null
				? tournamentRepository.findById(request.getTournamentId()).orElse(null)
				: null;
		User createdBy = userId != null ? userRepository.findById(userId).orElse(null) : null;

		EmailAutomationRule rule = ruleRepository.save(EmailAutomationRule.builder()
				.code(request.getCode())
				.name(request.getName())
				.description(request.getDescription())
				.eventType(request.getEventType())
				.template(template)
				.scope(tournament != null ? EmailScope.TOURNAMENT.getValue() : EmailScope.GLOBAL.getValue())
				.tournament(tournament)
				.recipientType(request.getRecipientType())
				.isEnabled(true)
				.delayMinutes(request.getDelayMinutes() != null ? request.getDelayMinutes() : 0)
				.conditions(request.getConditions())
				.createdBy(createdBy)
				.build());
		return toResponse(rule);
	}

	@Override
	@Transactional
	public EmailAutomationRuleResponse updateRule(Long id, EmailAutomationRuleRequest request) {
		EmailAutomationRule rule = findOrThrow(id);
		EmailTemplate template = templateRepository.findById(request.getTemplateId())
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));

		rule.setName(request.getName());
		rule.setDescription(request.getDescription());
		rule.setEventType(request.getEventType());
		rule.setTemplate(template);
		rule.setRecipientType(request.getRecipientType());
		rule.setDelayMinutes(request.getDelayMinutes() != null ? request.getDelayMinutes() : 0);
		rule.setConditions(request.getConditions());
		return toResponse(ruleRepository.save(rule));
	}

	@Override
	@Transactional
	public EmailAutomationRuleResponse setEnabled(Long id, boolean enabled) {
		EmailAutomationRule rule = findOrThrow(id);
		rule.setIsEnabled(enabled);
		return toResponse(ruleRepository.save(rule));
	}

	@Override
	@Transactional(readOnly = true)
	public List<EmailAutomationRule> resolveActiveRulesForEvent(EmailEventType eventType, Long tournamentId) {
		if (tournamentId != null) {
			List<EmailAutomationRule> scoped = ruleRepository
					.findByEventTypeAndIsEnabledTrueAndTournamentId(eventType.getValue(), tournamentId);
			if (!scoped.isEmpty()) {
				return scoped;
			}
		}
		return ruleRepository.findByEventTypeAndIsEnabledTrueAndTournamentIsNull(eventType.getValue());
	}

	private EmailAutomationRule findOrThrow(Long id) {
		return ruleRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_RULE_NOT_FOUND));
	}

	private EmailAutomationRuleResponse toResponse(EmailAutomationRule r) {
		return EmailAutomationRuleResponse.builder()
				.id(r.getId())
				.code(r.getCode())
				.name(r.getName())
				.description(r.getDescription())
				.eventType(r.getEventType())
				.eventTypeDisplayName(EmailEventType.valueOf(r.getEventType()).getDisplayName())
				.templateId(r.getTemplate().getId())
				.templateCode(r.getTemplate().getCode())
				.templateName(r.getTemplate().getName())
				.scope(r.getScope())
				.tournamentId(r.getTournament() != null ? r.getTournament().getId() : null)
				.recipientType(r.getRecipientType())
				.recipientTypeDisplayName(EmailRecipientType.valueOf(r.getRecipientType()).getDisplayName())
				.isEnabled(r.getIsEnabled())
				.delayMinutes(r.getDelayMinutes())
				.conditions(r.getConditions())
				.createdAt(r.getCreatedAt())
				.updatedAt(r.getUpdatedAt())
				.build();
	}
}
