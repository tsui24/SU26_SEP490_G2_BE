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

	/** Bật/tắt rule GLOBAL thật sự (trang Admin) — không dùng cho toggle theo từng giải. */
	EmailAutomationRuleResponse setEnabled(Long id, boolean enabled);

	/**
	 * Bật/tắt 1 rule CHO ĐÚNG 1 GIẢI. Nếu rule đang trỏ tới là rule GLOBAL, tự tạo (hoặc tái dùng)
	 * 1 bản sao riêng cho giải này rồi bật/tắt bản sao đó — không bao giờ sửa trực tiếp rule GLOBAL,
	 * tránh ảnh hưởng tới mọi giải khác đang dùng chung rule đó.
	 */
	EmailAutomationRuleResponse setEnabledForTournament(Long tournamentId, Long ruleId, boolean enabled);

	/** Rule riêng của giải nếu có, nếu không thì fallback rule global — dùng bởi event listener. */
	List<EmailAutomationRule> resolveActiveRulesForEvent(EmailEventType eventType, Long tournamentId);
}
