package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.MailLayoutSettingsRequest;
import com.capstone.su26_sep490_g2_be.dto.response.MailLayoutSettingsResponse;
import com.capstone.su26_sep490_g2_be.entity.MailLayoutSettings;
import com.capstone.su26_sep490_g2_be.repository.MailLayoutSettingsRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MailLayoutSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MailLayoutSettingsServiceImpl implements MailLayoutSettingsService {

	private final MailLayoutSettingsRepository mailLayoutSettingsRepository;
	private final UserRepository userRepository;

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
