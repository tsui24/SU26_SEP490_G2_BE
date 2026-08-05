package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.EmailSendLogDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailSendLogResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;

import java.time.Instant;

public interface EmailLogService {

	PageResponse<EmailSendLogResponse> search(String status, Long tournamentId, String triggerType,
			String templateCode, String recipientEmail, Instant fromDate, Instant toDate, int page, int size);

	EmailSendLogDetailResponse getDetail(Long id);
}
