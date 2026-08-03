package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.EmailSendLogDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailSendLogResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import com.capstone.su26_sep490_g2_be.enums.EmailSendStatus;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.EmailSendLogRepository;
import com.capstone.su26_sep490_g2_be.service.EmailLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EmailLogServiceImpl implements EmailLogService {

	private final EmailSendLogRepository emailSendLogRepository;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<EmailSendLogResponse> search(String status, Long tournamentId, String triggerType,
			String templateCode, String recipientEmail, Instant fromDate, Instant toDate, int page, int size) {
		var result = emailSendLogRepository.search(
				blankToNull(status),
				tournamentId,
				blankToNull(triggerType),
				blankToNull(templateCode),
				blankToNull(recipientEmail),
				fromDate,
				toDate,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
		return PageResponse.of(result, this::toResponse);
	}

	private String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	@Override
	@Transactional(readOnly = true)
	public EmailSendLogDetailResponse getDetail(Long id) {
		EmailSendLog log = emailSendLogRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_LOG_NOT_FOUND));
		return EmailSendLogDetailResponse.builder()
				.id(log.getId())
				.templateCode(log.getTemplate() != null ? log.getTemplate().getCode() : null)
				.templateName(log.getTemplate() != null ? log.getTemplate().getName() : null)
				.ruleCode(log.getRule() != null ? log.getRule().getCode() : null)
				.tournamentId(log.getTournament() != null ? log.getTournament().getId() : null)
				.tournamentName(log.getTournament() != null ? log.getTournament().getName() : null)
				.triggerType(log.getTriggerType())
				.recipientEmail(log.getRecipientEmail())
				.subjectRendered(log.getSubjectRendered())
				.bodyRendered(log.getBodyRendered())
				.status(log.getStatus())
				.statusDisplayName(EmailSendStatus.valueOf(log.getStatus()).getDisplayName())
				.errorMessage(log.getErrorMessage())
				.sentAt(log.getSentAt())
				.createdAt(log.getCreatedAt())
				.build();
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
