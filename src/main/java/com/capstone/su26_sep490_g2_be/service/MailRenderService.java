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
}
