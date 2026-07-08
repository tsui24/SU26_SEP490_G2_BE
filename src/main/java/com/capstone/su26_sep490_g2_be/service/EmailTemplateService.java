package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplatePreviewRequest;
import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplateRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmailTemplateResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailVariableItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;

import java.util.List;

public interface EmailTemplateService {

	PageResponse<EmailTemplateResponse> listTemplates(String category, Boolean isActive, int page, int size);

	/** Global templates + template riêng của owner (dùng cho màn Owner). */
	PageResponse<EmailTemplateResponse> listTemplatesForOwner(Long ownerId, int page, int size);

	EmailTemplateResponse getTemplate(Long id);

	EmailTemplateResponse createTemplate(Long userId, EmailTemplateRequest request);

	EmailTemplateResponse updateTemplate(Long id, EmailTemplateRequest request);

	EmailTemplateResponse setActive(Long id, boolean active);

	RenderedEmailResponse preview(EmailTemplatePreviewRequest request);

	List<EmailVariableItemResponse> listVariables();
}
