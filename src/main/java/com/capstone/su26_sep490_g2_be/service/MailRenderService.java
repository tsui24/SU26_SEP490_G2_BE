package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;

import java.util.Map;

public interface MailRenderService {

	/**
	 * Render subject + body của template với context. Key trong {@code variables} lồng nhau
	 * theo namespace (vd. {"tournament": {"name": "..."}, "custom": {"otp": "123456"}}) và được
	 * flatten thành placeholder dạng "tournament.name", "custom.otp" khớp với {{...}} trong template.
	 */
	RenderedEmailResponse render(EmailTemplate template, Map<String, Object> variables);

	RenderedEmailResponse renderByCode(String templateCode, Map<String, Object> variables);

	/**
	 * Render nội dung mẫu (không lấy từ {@link EmailTemplate} trong DB) bọc trong khung header/footer,
	 * dùng cho tính năng "gửi thử" ở trang cấu hình khung email. {@code headerHtmlOverride}/
	 * {@code footerHtmlOverride} cho phép xem trước bản đang soạn dở, chưa lưu — null/rỗng thì dùng
	 * bản đã lưu trong {@link com.capstone.su26_sep490_g2_be.entity.MailLayoutSettings}.
	 */
	RenderedEmailResponse renderLayoutTest(String headerHtmlOverride, String footerHtmlOverride,
			Map<String, Object> variables);
}
