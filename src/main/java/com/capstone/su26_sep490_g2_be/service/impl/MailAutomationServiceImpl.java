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
		List<EmailAutomationRule> tournamentRules = ruleRepository.findByTournamentId(tournamentId);

		// Rule GLOBAL nhưng gắn sự kiện tài khoản (USER_REGISTERED, STAFF/MANAGER_ACCOUNT_CREATED)
		// không liên quan tới 1 giải đấu cụ thể — chỉ Admin thấy ở trang quản trị toàn hệ thống.
		// Rule GLOBAL đã có override riêng cho giải này thì bỏ qua bản GLOBAL — chỉ hiện đúng 1 dòng
		// (bản override), tránh Owner/Manager thấy trùng lặp 2 dòng cho cùng 1 loại email.
		java.util.Set<String> overriddenBaseCodes = tournamentRules.stream()
				.map(this::baseCodeOf)
				.collect(java.util.stream.Collectors.toSet());

		List<EmailAutomationRule> result = new java.util.ArrayList<>(tournamentRules);
		ruleRepository.findByScope(EmailScope.GLOBAL.getValue()).stream()
				.filter(r -> EmailEventType.valueOf(r.getEventType()).isTournamentScoped())
				.filter(r -> !overriddenBaseCodes.contains(r.getCode()))
				.forEach(result::add);

		return result.stream().map(this::toResponse).toList();
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

	/** Nối vào code gốc để sinh code duy nhất cho bản override — {@code code} có ràng buộc UNIQUE. */
	private static final String OVERRIDE_CODE_SEPARATOR = "::T";

	@Override
	@Transactional
	public EmailAutomationRuleResponse setEnabledForTournament(Long tournamentId, Long ruleId, boolean enabled) {
		EmailAutomationRule rule = findOrThrow(ruleId);

		if (rule.getTournament() != null) {
			// Đã là rule riêng của giải (chắc chắn đúng giải này — caller đã kiểm tra trước khi gọi
			// vào đây) — bật/tắt thẳng, không cần tạo thêm bản sao nào nữa.
			rule.setIsEnabled(enabled);
			return toResponse(ruleRepository.save(rule));
		}

		// rule đang trỏ tới là bản GLOBAL dùng chung cho mọi giải — KHÔNG được sửa thẳng vào đây,
		// nếu không mọi giải khác đang dùng chung rule này cũng bị ảnh hưởng theo. Tìm (hoặc tạo mới)
		// 1 bản sao TOURNAMENT riêng cho đúng giải này, và chỉ bật/tắt bản sao đó.
		String overrideCode = rule.getCode() + OVERRIDE_CODE_SEPARATOR + tournamentId;
		EmailAutomationRule override = ruleRepository.findByCodeAndTournamentId(overrideCode, tournamentId)
				.orElseGet(() -> EmailAutomationRule.builder()
						.code(overrideCode)
						.name(rule.getName())
						.description(rule.getDescription())
						.eventType(rule.getEventType())
						.template(rule.getTemplate())
						.scope(EmailScope.TOURNAMENT.getValue())
						.tournament(tournamentRepository.getReferenceById(tournamentId))
						.recipientType(rule.getRecipientType())
						.delayMinutes(rule.getDelayMinutes())
						.conditions(rule.getConditions())
						.isEnabled(rule.getIsEnabled())
						.createdBy(rule.getCreatedBy())
						.build());
		override.setIsEnabled(enabled);
		return toResponse(ruleRepository.save(override));
	}

	@Override
	@Transactional(readOnly = true)
	public List<EmailAutomationRule> resolveActiveRulesForEvent(EmailEventType eventType, Long tournamentId) {
		if (tournamentId != null) {
			// Không lọc isEnabled ở query — giải có override riêng cho sự kiện này thì override đó
			// quyết định HOÀN TOÀN (kể cả khi đang tắt), không rơi về rule GLOBAL nữa. Trước đây lọc
			// isEnabled=true ngay trong query khiến 1 override đang TẮT trả về rỗng, code lại tưởng
			// giải này "chưa có override" rồi âm thầm rơi về rule GLOBAL và gửi mail như bình thường
			// — đúng lỗi đang sửa (tắt mail cho 1 giải mà mail vẫn gửi vì rơi về global).
			List<EmailAutomationRule> scoped = ruleRepository
					.findByEventTypeAndTournamentId(eventType.getValue(), tournamentId);
			if (!scoped.isEmpty()) {
				return scoped.stream().filter(r -> Boolean.TRUE.equals(r.getIsEnabled())).toList();
			}
		}
		return ruleRepository.findByEventTypeAndIsEnabledTrueAndTournamentIsNull(eventType.getValue());
	}

	private EmailAutomationRule findOrThrow(Long id) {
		return ruleRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_RULE_NOT_FOUND));
	}

	/** Code hiển thị cho người dùng — bỏ hậu tố sinh ra cho bản override, giữ lại code gốc dễ đọc. */
	private String baseCodeOf(EmailAutomationRule r) {
		if (r.getTournament() == null) {
			return r.getCode();
		}
		int idx = r.getCode().indexOf(OVERRIDE_CODE_SEPARATOR);
		return idx >= 0 ? r.getCode().substring(0, idx) : r.getCode();
	}

	private EmailAutomationRuleResponse toResponse(EmailAutomationRule r) {
		return EmailAutomationRuleResponse.builder()
				.id(r.getId())
				.code(baseCodeOf(r))
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
