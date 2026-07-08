package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MailProperties;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.EmailTriggerType;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.EmailTemplateRepository;
import com.capstone.su26_sep490_g2_be.service.EmailService;
import com.capstone.su26_sep490_g2_be.service.MailSendCommand;
import com.capstone.su26_sep490_g2_be.service.MailSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

	private static final String AUTH_OTP_TEMPLATE_CODE = "AUTH_OTP";

	private final EmailTemplateRepository emailTemplateRepository;
	private final MailSendService mailSendService;
	private final MailProperties mailProperties;

	@Override
	public void sendOtpEmail(String to, String otp) {
		EmailTemplate template = emailTemplateRepository.findByCode(AUTH_OTP_TEMPLATE_CODE)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));

		Map<String, Object> variables = Map.of(
				"custom", Map.of("otp", otp),
				"system", Map.of("appName", mailProperties.getAppName()));

		mailSendService.queueAndSend(MailSendCommand.builder()
				.template(template)
				.recipientEmail(to)
				.variables(variables)
				.triggerType(EmailTriggerType.SYSTEM)
				.build());
		log.info("OTP email queued for {}", to);
	}
}
