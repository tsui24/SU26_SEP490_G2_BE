package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.MailLayoutSettingsRequest;
import com.capstone.su26_sep490_g2_be.dto.request.MailLayoutTestSendRequest;
import com.capstone.su26_sep490_g2_be.dto.response.MailLayoutSettingsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.MailLayoutSettings;
import com.capstone.su26_sep490_g2_be.enums.EmailTriggerType;
import com.capstone.su26_sep490_g2_be.repository.MailLayoutSettingsRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MailLayoutSettingsService;
import com.capstone.su26_sep490_g2_be.service.MailRenderService;
import com.capstone.su26_sep490_g2_be.service.MailSendCommand;
import com.capstone.su26_sep490_g2_be.service.MailSendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MailLayoutSettingsServiceImpl implements MailLayoutSettingsService {

	private final MailLayoutSettingsRepository mailLayoutSettingsRepository;
	private final UserRepository userRepository;
	private final MailRenderService mailRenderService;
	private final MailSendService mailSendService;
	private final MailContextBuilder mailContextBuilder;

	@Override
	@Transactional
	public MailLayoutSettingsResponse getSettings() {
		return toResponse(findOrCreate());
	}

	@Override
	@Transactional
	public MailLayoutSettingsResponse updateSettings(Long userId, MailLayoutSettingsRequest request) {
		MailLayoutSettings settings = findOrCreate();
		settings.setHeaderHtml(request.getHeaderHtml());
		settings.setFooterHtml(request.getFooterHtml());
		settings.setUpdatedBy(userId != null ? userRepository.findById(userId).orElse(null) : null);
		return toResponse(mailLayoutSettingsRepository.save(settings));
	}

	@Override
	@Transactional
	public void sendTest(Long userId, MailLayoutTestSendRequest request) {
		Map<String, Object> variables = mailContextBuilder.sampleContext();
		RenderedEmailResponse rendered = mailRenderService.renderLayoutTest(
				request.getHeaderHtml(), request.getFooterHtml(), variables);
		mailSendService.queueAndSend(MailSendCommand.builder()
				.recipientEmail(request.getEmail())
				.variables(variables)
				.triggerType(EmailTriggerType.MANUAL)
				.createdByUserId(userId)
				.renderedOverride(rendered)
				.build());
	}

	private MailLayoutSettings findOrCreate() {
		return mailLayoutSettingsRepository.findFirstByOrderByIdAsc()
				.orElseGet(() -> mailLayoutSettingsRepository.save(MailLayoutSettings.builder()
						.headerHtml(MailLayoutSettings.DEFAULT_HEADER_HTML)
						.footerHtml(MailLayoutSettings.DEFAULT_FOOTER_HTML)
						.build()));
	}

	private MailLayoutSettingsResponse toResponse(MailLayoutSettings settings) {
		return MailLayoutSettingsResponse.builder()
				.id(settings.getId())
				.headerHtml(settings.getHeaderHtml())
				.footerHtml(settings.getFooterHtml())
				.updatedAt(settings.getUpdatedAt())
				.build();
	}
}
