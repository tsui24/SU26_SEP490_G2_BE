package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplatePreviewRequest;
import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplateRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmailTemplateResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailVariableItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailTemplateCategory;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.EmailTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.EmailTemplateService;
import com.capstone.su26_sep490_g2_be.service.MailRenderService;
import com.capstone.su26_sep490_g2_be.util.JsonParseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailTemplateServiceImpl implements EmailTemplateService {

	private static final List<EmailVariableItemResponse> STANDARD_VARIABLES = List.of(
			var("user.fullName", "Họ tên người nhận"),
			var("user.email", "Email người nhận"),
			var("tournament.name", "Tên giải đấu"),
			var("tournament.startAt", "Ngày bắt đầu giải"),
			var("tournament.status", "Trạng thái giải đấu"),
			var("registration.playerFullName", "Họ tên người đăng ký"),
			var("registration.status", "Trạng thái đăng ký"),
			var("match.roundNo", "Số thứ tự vòng đấu"),
			var("match.player1Name", "Tên vận động viên 1"),
			var("match.player2Name", "Tên vận động viên 2"),
			var("match.score", "Tỉ số trận đấu"),
			var("payment.amount", "Số tiền thanh toán"),
			var("payment.checkoutUrl", "Link thanh toán"),
			var("system.appName", "Tên hệ thống"),
			var("system.supportEmail", "Email hỗ trợ"),
			var("system.currentYear", "Năm hiện tại"),
			var("custom.*", "Biến tự nhập khi gửi thủ công, ví dụ custom.message"));

	private final EmailTemplateRepository emailTemplateRepository;
	private final UserRepository userRepository;
	private final MailRenderService mailRenderService;
	private final MailContextBuilder mailContextBuilder;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<EmailTemplateResponse> listTemplates(String category, Boolean isActive, int page, int size) {
		List<EmailTemplateResponse> filtered = emailTemplateRepository.findAll().stream()
				.filter(t -> category == null || category.isBlank() || category.equalsIgnoreCase(t.getCategory()))
				.filter(t -> isActive == null || isActive.equals(t.getIsActive()))
				.sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
				.map(this::toResponse)
				.toList();
		return PageResponse.of(filtered, page, size);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<EmailTemplateResponse> listTemplatesForOwner(Long ownerId, int page, int size) {
		var result = emailTemplateRepository.findByScopeOrOwnerId("GLOBAL", ownerId, PageRequest.of(page, size));
		return PageResponse.of(result, this::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public EmailTemplateResponse getTemplate(Long id) {
		return toResponse(findOrThrow(id));
	}

	@Override
	@Transactional
	public EmailTemplateResponse createTemplate(Long userId, EmailTemplateRequest request) {
		if (emailTemplateRepository.existsByCode(request.getCode())) {
			throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_CODE_EXISTS);
		}
		User createdBy = userId != null ? userRepository.findById(userId).orElse(null) : null;

		EmailTemplate template = emailTemplateRepository.save(EmailTemplate.builder()
				.code(request.getCode())
				.name(request.getName())
				.description(request.getDescription())
				.category(request.getCategory())
				.scope("GLOBAL")
				.subjectTemplate(request.getSubjectTemplate())
				.bodyHtmlTemplate(request.getBodyHtmlTemplate())
				.availableVariables(JsonParseUtil.toJson(request.getAvailableVariables()))
				.isActive(true)
				.createdBy(createdBy)
				.build());
		return toResponse(template);
	}

	@Override
	@Transactional
	public EmailTemplateResponse updateTemplate(Long id, EmailTemplateRequest request) {
		EmailTemplate template = findOrThrow(id);
		if (!template.getCode().equals(request.getCode()) && emailTemplateRepository.existsByCode(request.getCode())) {
			throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_CODE_EXISTS);
		}
		template.setCode(request.getCode());
		template.setName(request.getName());
		template.setDescription(request.getDescription());
		template.setCategory(request.getCategory());
		template.setSubjectTemplate(request.getSubjectTemplate());
		template.setBodyHtmlTemplate(request.getBodyHtmlTemplate());
		template.setAvailableVariables(JsonParseUtil.toJson(request.getAvailableVariables()));
		return toResponse(emailTemplateRepository.save(template));
	}

	@Override
	@Transactional
	public EmailTemplateResponse setActive(Long id, boolean active) {
		EmailTemplate template = findOrThrow(id);
		template.setIsActive(active);
		return toResponse(emailTemplateRepository.save(template));
	}

	@Override
	@Transactional(readOnly = true)
	public RenderedEmailResponse preview(EmailTemplatePreviewRequest request) {
		EmailTemplate template = request.getTemplateId() != null
				? findOrThrow(request.getTemplateId())
				: emailTemplateRepository.findByCode(request.getTemplateCode())
						.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));

		Map<String, Object> context = new HashMap<>(
				mailContextBuilder.previewContext(request.getTournamentId(), request.getSampleRegistrationId()));
		if (request.getVariables() != null) {
			context.putAll(request.getVariables());
		}
		return mailRenderService.render(template, context);
	}

	@Override
	public List<EmailVariableItemResponse> listVariables() {
		return STANDARD_VARIABLES;
	}

	private EmailTemplate findOrThrow(Long id) {
		return emailTemplateRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));
	}

	private EmailTemplateResponse toResponse(EmailTemplate t) {
		EmailTemplateCategory category = EmailTemplateCategory.valueOf(t.getCategory());
		return EmailTemplateResponse.builder()
				.id(t.getId())
				.code(t.getCode())
				.name(t.getName())
				.description(t.getDescription())
				.category(t.getCategory())
				.categoryDisplayName(category.getDisplayName())
				.scope(t.getScope())
				.ownerId(t.getOwner() != null ? t.getOwner().getId() : null)
				.subjectTemplate(t.getSubjectTemplate())
				.bodyHtmlTemplate(t.getBodyHtmlTemplate())
				.availableVariables(JsonParseUtil.parseStringList(t.getAvailableVariables()))
				.isActive(t.getIsActive())
				.createdAt(t.getCreatedAt())
				.updatedAt(t.getUpdatedAt())
				.build();
	}

	private static EmailVariableItemResponse var(String key, String description) {
		return EmailVariableItemResponse.builder().key(key).description(description).build();
	}
}
