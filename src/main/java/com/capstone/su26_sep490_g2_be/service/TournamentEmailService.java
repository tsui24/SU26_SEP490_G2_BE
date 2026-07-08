package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplatePreviewRequest;
import com.capstone.su26_sep490_g2_be.dto.request.ManualSendEmailRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmailAutomationRuleResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailSendLogResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ManualSendResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;

import java.util.List;

/**
 * Email theo phạm vi 1 giải đấu — dùng chung cho Owner (enforceOwnership=true) và Manager
 * (enforceOwnership=false), theo đúng pattern của {@code OwnerTournamentServiceImpl}.
 */
public interface TournamentEmailService {

	RenderedEmailResponse preview(Long userId, Long tournamentId, boolean enforceOwnership,
			EmailTemplatePreviewRequest request);

	ManualSendResultResponse sendManual(Long userId, Long tournamentId, boolean enforceOwnership,
			ManualSendEmailRequest request);

	List<EmailAutomationRuleResponse> listAutomationRules(Long userId, Long tournamentId, boolean enforceOwnership);

	EmailAutomationRuleResponse setRuleEnabled(Long userId, Long tournamentId, boolean enforceOwnership,
			Long ruleId, boolean enabled);

	PageResponse<EmailSendLogResponse> listLogs(Long userId, Long tournamentId, boolean enforceOwnership,
			String status, int page, int size);
}
