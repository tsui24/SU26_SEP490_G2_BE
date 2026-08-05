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
import com.capstone.su26_sep490_g2_be.service.MailRenderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link EmailTemplateServiceImpl}.
 *
 * <p>Mirrors the <b>EmailTemplateService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — FT-26 / FT-27, the email use cases (UC-42…48), numbered when the
 * rest of Wave 5 is written up.
 *
 * <p>The Admin edits these templates and every automated mail in the product renders from one.
 * The code is what the rules and the send flow look a template up by, so the two places it can be
 * set — create and update — both guard its uniqueness.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · EmailTemplateService — FT-26, FT-27")
class EmailTemplateServiceImplTest {

	@Mock EmailTemplateRepository emailTemplateRepository;
	@Mock UserRepository userRepository;
	@Mock MailRenderService mailRenderService;
	@Mock MailContextBuilder mailContextBuilder;

	@InjectMocks EmailTemplateServiceImpl service;

	private static final Long TEMPLATE_ID = 9L;

	private static EmailTemplate template(long id, String code, String category, boolean active, Instant createdAt) {
		EmailTemplate template = EmailTemplate.builder()
				.id(id).code(code).name("Mẫu " + code).description("mô tả")
				.category(category).scope("GLOBAL")
				.subjectTemplate("Chào {{user.fullName}}")
				.bodyHtmlTemplate("<p>{{tournament.name}}</p>")
				.availableVariables("[\"user.fullName\"]")
				.isActive(active)
				.build();
		template.setCreatedAt(createdAt);
		return template;
	}

	private static EmailTemplate template(long id, String code) {
		return template(id, code, EmailTemplateCategory.TOURNAMENT.name(), true, Instant.parse("2026-01-01T00:00:00Z"));
	}

	private static EmailTemplateRequest request(String code) {
		EmailTemplateRequest request = new EmailTemplateRequest();
		request.setCode(code);
		request.setName("Mẫu mới");
		request.setDescription("mô tả");
		request.setCategory(EmailTemplateCategory.TOURNAMENT.name());
		request.setSubjectTemplate("Chào {{user.fullName}}");
		request.setBodyHtmlTemplate("<p>xin chào</p>");
		request.setAvailableVariables(List.of("user.fullName"));
		return request;
	}

	private EmailTemplate savedTemplate() {
		ArgumentCaptor<EmailTemplate> captor = ArgumentCaptor.forClass(EmailTemplate.class);
		verify(emailTemplateRepository).save(captor.capture());
		return captor.getValue();
	}

	// ══════════════════════════ listing ══════════════════════════

	@Test
	@DisplayName("TC-001 · The Admin list is ordered newest first")
	void TC001_listTemplates_newestFirst() {
		when(emailTemplateRepository.findAll()).thenReturn(List.of(
				template(1L, "OLD", EmailTemplateCategory.TOURNAMENT.name(), true, Instant.parse("2026-01-01T00:00:00Z")),
				template(2L, "NEW", EmailTemplateCategory.TOURNAMENT.name(), true, Instant.parse("2026-03-01T00:00:00Z"))));

		PageResponse<EmailTemplateResponse> page = service.listTemplates(null, null, 0, 20);

		assertEquals("NEW", page.getContent().get(0).getCode());
		assertEquals("OLD", page.getContent().get(1).getCode());
	}

	@Test
	@DisplayName("TC-002 · The category filter ignores case")
	void TC002_listTemplates_categoryFilterCaseInsensitive() {
		when(emailTemplateRepository.findAll()).thenReturn(List.of(
				template(1L, "A", EmailTemplateCategory.TOURNAMENT.name(), true, Instant.now()),
				template(2L, "B", EmailTemplateCategory.MARKETING.name(), true, Instant.now())));

		PageResponse<EmailTemplateResponse> page = service.listTemplates("tournament", null, 0, 20);

		assertEquals(1, page.getContent().size());
		assertEquals("A", page.getContent().get(0).getCode());
	}

	@Test
	@DisplayName("TC-003 · A blank category is not a filter")
	void TC003_listTemplates_blankCategoryIgnored() {
		when(emailTemplateRepository.findAll()).thenReturn(List.of(
				template(1L, "A", EmailTemplateCategory.TOURNAMENT.name(), true, Instant.now()),
				template(2L, "B", EmailTemplateCategory.MARKETING.name(), true, Instant.now())));

		// An unselected dropdown sends an empty string, which must not filter everything out
		assertEquals(2, service.listTemplates("   ", null, 0, 20).getContent().size());
	}

	@Test
	@DisplayName("TC-004 · The active filter separates retired templates from live ones")
	void TC004_listTemplates_activeFilter() {
		when(emailTemplateRepository.findAll()).thenReturn(List.of(
				template(1L, "LIVE", EmailTemplateCategory.TOURNAMENT.name(), true, Instant.now()),
				template(2L, "RETIRED", EmailTemplateCategory.TOURNAMENT.name(), false, Instant.now())));

		assertEquals("LIVE", service.listTemplates(null, true, 0, 20).getContent().get(0).getCode());
		assertEquals("RETIRED", service.listTemplates(null, false, 0, 20).getContent().get(0).getCode());
	}

	@Test
	@DisplayName("TC-005 · The Admin list is paged in memory after filtering")
	void TC005_listTemplates_paged() {
		when(emailTemplateRepository.findAll()).thenReturn(List.of(
				template(1L, "A", EmailTemplateCategory.TOURNAMENT.name(), true, Instant.parse("2026-01-01T00:00:00Z")),
				template(2L, "B", EmailTemplateCategory.TOURNAMENT.name(), true, Instant.parse("2026-02-01T00:00:00Z"))));

		PageResponse<EmailTemplateResponse> page = service.listTemplates(null, null, 0, 1);

		// The two filters are computed in Java, so the slice has to be taken after them
		assertEquals(1, page.getContent().size());
		assertEquals(2L, page.getTotalElements());
		assertEquals(2, page.getTotalPages());
	}

	@Test
	@DisplayName("TC-006 · An Owner sees the shared templates plus their own")
	void TC006_listTemplatesForOwner_scoped() {
		when(emailTemplateRepository.findByScopeOrOwnerId(eq("GLOBAL"), eq(4L), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(template(1L, "SHARED"))));

		PageResponse<EmailTemplateResponse> page = service.listTemplatesForOwner(4L, 0, 20);

		// Scoped in the query rather than in Java, so one chain cannot read another chain's drafts
		assertEquals(1, page.getContent().size());
		assertEquals("SHARED", page.getContent().get(0).getCode());
	}

	@Test
	@DisplayName("TC-007 · The response spells the category out in Vietnamese")
	void TC007_toResponse_categoryDisplayName() {
		when(emailTemplateRepository.findById(TEMPLATE_ID))
				.thenReturn(Optional.of(template(TEMPLATE_ID, "REG_APPROVED")));

		EmailTemplateResponse response = service.getTemplate(TEMPLATE_ID);

		assertEquals("Giải đấu", response.getCategoryDisplayName());
		assertEquals(List.of("user.fullName"), response.getAvailableVariables());
		// A global template belongs to no one chain
		assertNull(response.getOwnerId());
	}

	@Test
	@DisplayName("TC-008 · Opening a template that does not exist")
	void TC008_getTemplate_notFound() {
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.getTemplate(TEMPLATE_ID));

		assertEquals(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ create ══════════════════════════

	@Test
	@DisplayName("TC-009 · A new template is created live and shared across the platform")
	void TC009_createTemplate_happyPath() {
		when(emailTemplateRepository.existsByCode("REG_APPROVED")).thenReturn(false);
		when(userRepository.findById(4L)).thenReturn(Optional.of(User.builder().id(4L).build()));
		when(emailTemplateRepository.save(any(EmailTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

		service.createTemplate(4L, request("REG_APPROVED"));

		EmailTemplate saved = savedTemplate();
		assertEquals("REG_APPROVED", saved.getCode());
		assertEquals("GLOBAL", saved.getScope());
		// A template is usable the moment it exists; there is no separate publish step
		assertTrue(saved.getIsActive());
		assertEquals(4L, saved.getCreatedBy().getId());
		assertEquals("[\"user.fullName\"]", saved.getAvailableVariables());
	}

	@Test
	@DisplayName("TC-010 · A template code already in use is rejected")
	void TC010_createTemplate_duplicateCode() {
		when(emailTemplateRepository.existsByCode("REG_APPROVED")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTemplate(4L, request("REG_APPROVED")));

		// The code is what rules and sends look a template up by, so a duplicate would make the
		// choice between two templates arbitrary
		assertEquals(ErrorCode.EMAIL_TEMPLATE_CODE_EXISTS, ex.getErrorCode());
		verify(emailTemplateRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-011 · A template created by the system carries no author")
	void TC011_createTemplate_noUserId() {
		when(emailTemplateRepository.existsByCode("REG_APPROVED")).thenReturn(false);
		when(emailTemplateRepository.save(any(EmailTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

		service.createTemplate(null, request("REG_APPROVED"));

		assertNull(savedTemplate().getCreatedBy());
		verify(userRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("TC-012 · An author whose account has gone does not block the create")
	void TC012_createTemplate_authorGone() {
		when(emailTemplateRepository.existsByCode("REG_APPROVED")).thenReturn(false);
		when(userRepository.findById(4L)).thenReturn(Optional.empty());
		when(emailTemplateRepository.save(any(EmailTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

		service.createTemplate(4L, request("REG_APPROVED"));

		assertNull(savedTemplate().getCreatedBy());
	}

	// ══════════════════════════ update ══════════════════════════

	@Test
	@DisplayName("TC-013 · Editing a template rewrites its content")
	void TC013_updateTemplate_happyPath() {
		EmailTemplate existing = template(TEMPLATE_ID, "REG_APPROVED");
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));
		when(emailTemplateRepository.save(existing)).thenReturn(existing);

		EmailTemplateRequest request = request("REG_APPROVED");
		request.setName("Đăng ký được duyệt (bản mới)");
		request.setSubjectTemplate("Chúc mừng {{user.fullName}}");

		EmailTemplateResponse response = service.updateTemplate(TEMPLATE_ID, request);

		assertEquals("Đăng ký được duyệt (bản mới)", response.getName());
		assertEquals("Chúc mừng {{user.fullName}}", response.getSubjectTemplate());
		// Resubmitting the same code is not a duplicate
		verify(emailTemplateRepository, never()).existsByCode(any());
	}

	@Test
	@DisplayName("TC-014 · Renaming the code onto one already in use is rejected")
	void TC014_updateTemplate_codeCollision() {
		when(emailTemplateRepository.findById(TEMPLATE_ID))
				.thenReturn(Optional.of(template(TEMPLATE_ID, "REG_APPROVED")));
		when(emailTemplateRepository.existsByCode("REG_REJECTED")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateTemplate(TEMPLATE_ID, request("REG_REJECTED")));

		assertEquals(ErrorCode.EMAIL_TEMPLATE_CODE_EXISTS, ex.getErrorCode());
		verify(emailTemplateRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-015 · Renaming the code onto a free one is allowed")
	void TC015_updateTemplate_codeChangedToFreeCode() {
		EmailTemplate existing = template(TEMPLATE_ID, "REG_APPROVED");
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));
		when(emailTemplateRepository.existsByCode("REG_CONFIRMED")).thenReturn(false);
		when(emailTemplateRepository.save(existing)).thenReturn(existing);

		assertEquals("REG_CONFIRMED",
				service.updateTemplate(TEMPLATE_ID, request("REG_CONFIRMED")).getCode());
	}

	@Test
	@DisplayName("TC-016 · Editing a template that does not exist")
	void TC016_updateTemplate_notFound() {
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateTemplate(TEMPLATE_ID, request("X")));

		assertEquals(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-017 · Retiring a template keeps the row")
	void TC017_setActive_disables() {
		EmailTemplate existing = template(TEMPLATE_ID, "REG_APPROVED");
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));
		when(emailTemplateRepository.save(existing)).thenReturn(existing);

		assertFalse(service.setActive(TEMPLATE_ID, false).getIsActive());
		// Send logs point at the template, so deleting it would orphan the history
		verify(emailTemplateRepository, never()).delete(any(EmailTemplate.class));
	}

	// ══════════════════════════ preview and variables ══════════════════════════

	@Test
	@DisplayName("TC-018 · A preview by id renders against the sample context")
	void TC018_preview_byId() {
		when(emailTemplateRepository.findById(TEMPLATE_ID))
				.thenReturn(Optional.of(template(TEMPLATE_ID, "REG_APPROVED")));
		when(mailContextBuilder.previewContext(77L, null)).thenReturn(Map.of("tournament.name", "Summer Open"));
		when(mailRenderService.render(any(EmailTemplate.class), anyMap()))
				.thenReturn(RenderedEmailResponse.builder().subject("Chào").bodyHtml("<p>x</p>").build());

		EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest();
		request.setTemplateId(TEMPLATE_ID);
		request.setTournamentId(77L);

		assertEquals("Chào", service.preview(request).getSubject());
	}

	@Test
	@DisplayName("TC-019 · A preview with no id falls back to the template code")
	void TC019_preview_byCode() {
		when(emailTemplateRepository.findByCode("REG_APPROVED"))
				.thenReturn(Optional.of(template(TEMPLATE_ID, "REG_APPROVED")));
		when(mailContextBuilder.previewContext(null, null)).thenReturn(Map.of());
		when(mailRenderService.render(any(EmailTemplate.class), anyMap()))
				.thenReturn(RenderedEmailResponse.builder().subject("Chào").bodyHtml("<p>x</p>").build());

		EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest();
		request.setTemplateCode("REG_APPROVED");

		service.preview(request);

		verify(emailTemplateRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("TC-020 · A preview of a code that does not exist")
	void TC020_preview_codeNotFound() {
		when(emailTemplateRepository.findByCode("NO_SUCH")).thenReturn(Optional.empty());

		EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest();
		request.setTemplateCode("NO_SUCH");

		BusinessException ex = assertThrows(BusinessException.class, () -> service.preview(request));

		assertEquals(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-021 · Variables typed into the preview win over the sample data")
	void TC021_preview_variablesOverride() {
		when(emailTemplateRepository.findById(TEMPLATE_ID))
				.thenReturn(Optional.of(template(TEMPLATE_ID, "REG_APPROVED")));
		when(mailContextBuilder.previewContext(null, null))
				.thenReturn(Map.of("tournament.name", "Mẫu", "system.appName", "BTMS"));
		when(mailRenderService.render(any(EmailTemplate.class), anyMap()))
				.thenReturn(RenderedEmailResponse.builder().subject("x").bodyHtml("<p>x</p>").build());

		EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest();
		request.setTemplateId(TEMPLATE_ID);
		request.setVariables(Map.of("tournament.name", "Tên thử"));

		service.preview(request);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
		verify(mailRenderService).render(any(EmailTemplate.class), context.capture());
		assertEquals("Tên thử", context.getValue().get("tournament.name"));
		assertEquals("BTMS", context.getValue().get("system.appName"));
	}

	@Test
	@DisplayName("TC-022 · The variable reference lists every namespace a template may use")
	void TC022_listVariables() {
		List<EmailVariableItemResponse> variables = service.listVariables();

		List<String> keys = variables.stream().map(EmailVariableItemResponse::getKey).toList();
		// The Admin writes templates against this list, so a namespace missing here is a namespace
		// nobody knows exists
		assertTrue(keys.contains("user.fullName"));
		assertTrue(keys.contains("tournament.name"));
		assertTrue(keys.contains("registration.status"));
		assertTrue(keys.contains("match.score"));
		assertTrue(keys.contains("payment.checkoutUrl"));
		assertTrue(keys.contains("system.currentYear"));
		assertTrue(keys.contains("custom.*"));
		assertTrue(variables.stream().allMatch(v -> v.getDescription() != null && !v.getDescription().isBlank()));
	}
}
