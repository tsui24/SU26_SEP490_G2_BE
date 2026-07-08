package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.EmailTemplateRepository;
import com.capstone.su26_sep490_g2_be.service.MailRenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.StringSubstitutor;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailRenderServiceImpl implements MailRenderService {

	private static final String PREFIX = "{{";
	private static final String SUFFIX = "}}";

	private static final PolicyFactory CUSTOM_HTML_POLICY = Sanitizers.FORMATTING
			.and(Sanitizers.BLOCKS)
			.and(Sanitizers.LINKS);

	private final EmailTemplateRepository templateRepository;

	@Override
	public RenderedEmailResponse render(EmailTemplate template, Map<String, Object> variables) {
		try {
			Map<String, String> flatPlain = new LinkedHashMap<>();
			Map<String, String> flatHtml = new LinkedHashMap<>();
			flatten("", variables, flatPlain, flatHtml);

			String subject = new StringSubstitutor(flatPlain, PREFIX, SUFFIX)
					.replace(template.getSubjectTemplate());
			String bodyHtml = new StringSubstitutor(flatHtml, PREFIX, SUFFIX)
					.replace(template.getBodyHtmlTemplate());

			return RenderedEmailResponse.builder()
					.subject(subject)
					.bodyHtml(bodyHtml)
					.build();
		} catch (Exception e) {
			log.error("Render email template {} failed", template.getCode(), e);
			throw new BusinessException(ErrorCode.EMAIL_RENDER_FAILED);
		}
	}

	@Override
	public RenderedEmailResponse renderByCode(String templateCode, Map<String, Object> variables) {
		EmailTemplate template = templateRepository.findByCode(templateCode)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));
		return render(template, variables);
	}

	/**
	 * Flatten map lồng nhau thành dạng "namespace.key" -> value.toString(), tạo song song 2 bản:
	 * plain (dùng cho subject, không escape) và html (dùng cho body — escape mọi giá trị, trừ
	 * namespace "custom" được sanitize bằng OWASP policy để cho phép định dạng cơ bản do
	 * Owner/Manager tự nhập).
	 */
	@SuppressWarnings("unchecked")
	private void flatten(String prefix, Map<String, Object> source,
			Map<String, String> flatPlain, Map<String, String> flatHtml) {
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
			Object value = entry.getValue();
			if (value instanceof Map<?, ?> nested) {
				flatten(key, (Map<String, Object>) nested, flatPlain, flatHtml);
			} else {
				String plain = value == null ? "" : value.toString();
				flatPlain.put(key, plain);
				boolean isCustom = key.equals("custom") || key.startsWith("custom.");
				flatHtml.put(key, isCustom ? CUSTOM_HTML_POLICY.sanitize(plain) : StringEscapeUtils.escapeHtml4(plain));
			}
		}
	}
}
