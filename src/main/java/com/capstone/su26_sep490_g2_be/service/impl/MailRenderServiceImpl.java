package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.entity.MailLayoutSettings;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.EmailTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.MailLayoutSettingsRepository;
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

	/**
	 * Khung HTML dùng chung cho mọi email (header thương hiệu + card nội dung + footer), bọc
	 * quanh bodyHtml đã render từ template trong DB — để mọi email đều có giao diện nhất quán
	 * thay vì chỉ là text trần. Header/footer lấy từ {@link MailLayoutSettings} (chỉnh được qua
	 * Admin UI); phần khung table/CSS bên ngoài (bố cục, màu nền, responsive) giữ cố định trong
	 * code vì đây là phần kỹ thuật đảm bảo email hiển thị đúng trên các mail client.
	 * <p>
	 * Ghép bằng nối chuỗi thay vì thay thế token — vì headerHtml/footerHtml do admin tự nhập nên
	 * không thể loại trừ khả năng chúng chứa trùng token, dẫn tới thay thế nhầm nếu dùng chuỗi
	 * {@code .replace()} nhiều lần.
	 */
	private static final String LAYOUT_HEAD = """
			<!DOCTYPE html>
			<html lang="vi">
			<head>
			<meta charset="UTF-8"/>
			<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
			<style>
			  .btms-mail p { margin:0 0 12px; }
			  .btms-mail a { color:#5b3fd6; }
			  .btms-mail .btn { display:inline-block; background:#010851; color:#ffffff !important; text-decoration:none; font-weight:600; padding:12px 28px; border-radius:8px; }
			</style>
			</head>
			<body style="margin:0;padding:0;background:#f2f2f7;">
			  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" class="btms-mail" style="background:#f2f2f7;padding:32px 16px;">
			    <tr><td align="center">
			      <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 4px 24px rgba(1,8,81,0.08);font-family:Arial,Helvetica,sans-serif;">
			        <tr>
			          <td style="background:linear-gradient(135deg,#010851,#241c7a);padding:28px 32px;color:#ffffff;">
			""";

	private static final String LAYOUT_AFTER_HEADER = """
			          </td>
			        </tr>
			        <tr>
			          <td style="padding:32px;color:#1f2430;font-size:15px;line-height:1.65;">
			""";

	private static final String LAYOUT_AFTER_BODY = """
			          </td>
			        </tr>
			        <tr>
			          <td style="background:#f7f7fb;padding:18px 32px;text-align:center;font-size:12px;color:#8a8fa3;border-top:1px solid #ececf3;">
			""";

	private static final String LAYOUT_TAIL = """
			          </td>
			        </tr>
			      </table>
			    </td></tr>
			  </table>
			</body>
			</html>
			""";

	private static final String LAYOUT_TEST_SUBJECT = "[Thử nghiệm] Kiểm tra khung email — {{system.appName}}";
	private static final String LAYOUT_TEST_BODY = """
			<p>Xin chào {{user.fullName}},</p>
			<p>Đây là email <b>thử nghiệm</b> để kiểm tra khung header/footer trước khi áp dụng cho toàn hệ thống.</p>
			<p>Nội dung thật của từng loại email (đăng ký, thanh toán, nhắc lịch thi đấu...) sẽ hiển thị ở vị trí này.</p>
			""";

	private final EmailTemplateRepository templateRepository;
	private final MailLayoutSettingsRepository mailLayoutSettingsRepository;

	@Override
	public RenderedEmailResponse render(EmailTemplate template, Map<String, Object> variables) {
		try {
			Map<String, String> flatPlain = new LinkedHashMap<>();
			Map<String, String> flatHtml = new LinkedHashMap<>();
			flatten("", variables, flatPlain, flatHtml);

			StringSubstitutor htmlSubstitutor = new StringSubstitutor(flatHtml, PREFIX, SUFFIX);
			String subject = new StringSubstitutor(flatPlain, PREFIX, SUFFIX)
					.replace(template.getSubjectTemplate());
			String innerBodyHtml = htmlSubstitutor.replace(template.getBodyHtmlTemplate());
			String bodyHtml = wrapLayout(innerBodyHtml, htmlSubstitutor);

			return RenderedEmailResponse.builder()
					.subject(subject)
					.bodyHtml(bodyHtml)
					.build();
		} catch (Exception e) {
			log.error("Render email template {} failed", template.getCode(), e);
			throw new BusinessException(ErrorCode.EMAIL_RENDER_FAILED);
		}
	}

	/** Bọc nội dung template đã render vào khung HTML thương hiệu chung (header/footer từ DB). */
	private String wrapLayout(String innerBodyHtml, StringSubstitutor htmlSubstitutor) {
		MailLayoutSettings layout = currentLayout();
		return buildLayout(innerBodyHtml, htmlSubstitutor, layout.getHeaderHtml(), layout.getFooterHtml());
	}

	private String buildLayout(String innerBodyHtml, StringSubstitutor htmlSubstitutor,
			String headerHtmlTemplate, String footerHtmlTemplate) {
		String headerHtml = htmlSubstitutor.replace(headerHtmlTemplate);
		String footerHtml = htmlSubstitutor.replace(footerHtmlTemplate);
		return LAYOUT_HEAD + headerHtml + LAYOUT_AFTER_HEADER + innerBodyHtml
				+ LAYOUT_AFTER_BODY + footerHtml + LAYOUT_TAIL;
	}

	private MailLayoutSettings currentLayout() {
		return mailLayoutSettingsRepository.findFirstByOrderByIdAsc()
				.orElseGet(() -> MailLayoutSettings.builder()
						.headerHtml(MailLayoutSettings.DEFAULT_HEADER_HTML)
						.footerHtml(MailLayoutSettings.DEFAULT_FOOTER_HTML)
						.build());
	}

	@Override
	public RenderedEmailResponse renderByCode(String templateCode, Map<String, Object> variables) {
		EmailTemplate template = templateRepository.findByCode(templateCode)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));
		return render(template, variables);
	}

	@Override
	public RenderedEmailResponse renderLayoutTest(String headerHtmlOverride, String footerHtmlOverride,
			Map<String, Object> variables) {
		try {
			Map<String, String> flatPlain = new LinkedHashMap<>();
			Map<String, String> flatHtml = new LinkedHashMap<>();
			flatten("", variables, flatPlain, flatHtml);

			StringSubstitutor htmlSubstitutor = new StringSubstitutor(flatHtml, PREFIX, SUFFIX);
			String subject = new StringSubstitutor(flatPlain, PREFIX, SUFFIX).replace(LAYOUT_TEST_SUBJECT);
			String innerBodyHtml = htmlSubstitutor.replace(LAYOUT_TEST_BODY);

			MailLayoutSettings layout = currentLayout();
			String headerHtmlTemplate = (headerHtmlOverride != null && !headerHtmlOverride.isBlank())
					? headerHtmlOverride
					: layout.getHeaderHtml();
			String footerHtmlTemplate = (footerHtmlOverride != null && !footerHtmlOverride.isBlank())
					? footerHtmlOverride
					: layout.getFooterHtml();
			String bodyHtml = buildLayout(innerBodyHtml, htmlSubstitutor, headerHtmlTemplate, footerHtmlTemplate);

			return RenderedEmailResponse.builder()
					.subject(subject)
					.bodyHtml(bodyHtml)
					.build();
		} catch (Exception e) {
			log.error("Render layout test failed", e);
			throw new BusinessException(ErrorCode.EMAIL_RENDER_FAILED);
		}
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
