package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.EmailAutomationRuleRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmailAutomationRuleResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailAutomationRule;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;

import java.util.List;

public interface MailAutomationService {

	PageResponse<EmailAutomationRuleResponse> listGlobalRules(int page, int size);

	/** Rule global + rule riêng của giải — dùng cho màn Owner/Manager. */
	List<EmailAutomationRuleResponse> listRulesForTournament(Long tournamentId);

	EmailAutomationRuleResponse createRule(Long userId, EmailAutomationRuleRequest request);

	EmailAutomationRuleResponse updateRule(Long id, EmailAutomationRuleRequest request);

	EmailAutomationRuleResponse setEnabled(Long id, boolean enabled);

	/** Rule riêng của giải nếu có, nếu không thì fallback rule global — dùng bởi event listener. */
	List<EmailAutomationRule> resolveActiveRulesForEvent(EmailEventType eventType, Long tournamentId);
}
