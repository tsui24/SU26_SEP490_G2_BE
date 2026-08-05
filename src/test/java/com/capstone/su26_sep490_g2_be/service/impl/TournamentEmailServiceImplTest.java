package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.EmailTemplatePreviewRequest;
import com.capstone.su26_sep490_g2_be.dto.request.ManualSendEmailRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmailAutomationRuleResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmailSendLogResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ManualSendResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.EmailAutomationRule;
import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailRecipientType;
import com.capstone.su26_sep490_g2_be.enums.EmailSendStatus;
import com.capstone.su26_sep490_g2_be.enums.EmailTriggerType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.EmailAutomationRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.EmailSendLogRepository;
import com.capstone.su26_sep490_g2_be.repository.EmailTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.MailAutomationService;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.MailRenderService;
import com.capstone.su26_sep490_g2_be.service.MailSendCommand;
import com.capstone.su26_sep490_g2_be.service.MailSendService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link TournamentEmailServiceImpl}.
 *
 * <p>Mirrors the <b>TournamentEmailService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — FT-26 / FT-27, the email use cases (UC-42…48). The wave has not
 * been written up yet, so rows carry the feature-level reference; the sub-numbers go in when the
 * Wave 5 sheets are built.
 *
 * <p>Every entry point runs the same branch-access check first, which is why the guard cases come
 * before the behaviour of any one method: an Owner may mail the whole chain, a Manager only the
 * branches they hold, and the Admin path skips the check entirely.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · TournamentEmailService — FT-26, FT-27")
class TournamentEmailServiceImplTest {

	@Mock TournamentRepository tournamentRepository;
	@Mock EmailTemplateRepository emailTemplateRepository;
	@Mock EmailAutomationRuleRepository emailAutomationRuleRepository;
	@Mock EmailSendLogRepository emailSendLogRepository;
	@Mock RegistrationRepository registrationRepository;
	@Mock PaymentRepository paymentRepository;
	@Mock com.capstone.su26_sep490_g2_be.service.MailRecipientResolver mailRecipientResolver;
	@Mock MailRenderService mailRenderService;
	@Mock MailSendService mailSendService;
	@Mock MailAutomationService mailAutomationService;
	@Mock MailContextBuilder mailContextBuilder;
	@Mock UserRepository userRepository;
	@Mock BranchAccessService branchAccessService;

	@InjectMocks TournamentEmailServiceImpl service;

	private static final Long USER_ID = 4L;
	private static final Long TOURNAMENT_ID = 77L;
	private static final Long TEMPLATE_ID = 9L;
	private static final Long BRANCH_ID = 5L;

	private static Tournament tournament() {
		return Tournament.builder()
				.id(TOURNAMENT_ID).name("Summer Open 2026")
				.branch(Branch.builder().id(BRANCH_ID).name("Chi nhánh Quận 1").build())
				.build();
	}

	private static User actor() {
		return User.builder().id(USER_ID).email("manager@example.com").build();
	}

	private static EmailTemplate template(boolean active) {
		return EmailTemplate.builder()
				.id(TEMPLATE_ID).code("TOURNAMENT_REMINDER").name("Nhắc lịch thi đấu")
				.subjectTemplate("Nhắc lịch {{tournament.name}}")
				.bodyHtmlTemplate("<p>Xin chào {{registration.playerName}}</p>")
				.isActive(active)
				.build();
	}

	private static ManualSendEmailRequest manualRequest(EmailRecipientType type) {
		ManualSendEmailRequest request = new ManualSendEmailRequest();
		request.setTemplateId(TEMPLATE_ID);
		request.setRecipientType(type.name());
		return request;
	}

	private static RenderedEmailResponse rendered() {
		return RenderedEmailResponse.builder().subject("Nhắc lịch Summer Open 2026").bodyHtml("<p>…</p>").build();
	}

	/** The access check every entry point runs first. */
	private void givenAccessibleTournament() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
		lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(actor()));
		lenient().when(branchAccessService.canActorAccessBranch(any(User.class), eq(BRANCH_ID))).thenReturn(true);
	}

	private void givenSendableTemplate() {
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(mailContextBuilder.systemContext()).thenReturn(new java.util.HashMap<>(Map.of("system.year", "2026")));
	}

	private List<MailSendCommand> capturedCommands() {
		ArgumentCaptor<MailSendCommand> captor = ArgumentCaptor.forClass(MailSendCommand.class);
		verify(mailSendService, times(1)).queueAndSend(captor.capture());
		return captor.getAllValues();
	}

	// ══════════════ the access guard shared by every entry point ══════════════

	@Test
	@DisplayName("TC-001 · Mailing a tournament that does not exist")
	void TC001_loadTournament_notFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.listAutomationRules(USER_ID, TOURNAMENT_ID, true));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-002 · An anonymous caller cannot reach an owned tournament")
	void TC002_assertBranchAccess_nullUser() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.listAutomationRules(null, TOURNAMENT_ID, true));

		// The check runs before the account lookup, so a null id never reaches the repository
		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
		verify(userRepository, never()).findById(any());
	}

	@Test
	@DisplayName("TC-003 · A caller whose account has gone is refused")
	void TC003_assertBranchAccess_unknownActor() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.listAutomationRules(USER_ID, TOURNAMENT_ID, true));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-004 · A Manager without the branch cannot mail its tournament")
	void TC004_assertBranchAccess_branchDenied() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(actor()));
		when(branchAccessService.canActorAccessBranch(any(User.class), eq(BRANCH_ID))).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.listAutomationRules(USER_ID, TOURNAMENT_ID, true));

		// One chain, so an Owner passes for every branch while a Manager passes only for theirs
		assertEquals(ErrorCode.AUTH_ACCESS_DENIED, ex.getErrorCode());
		verify(mailAutomationService, never()).listRulesForTournament(any());
	}

	@Test
	@DisplayName("TC-005 · The Admin path skips the branch check")
	void TC005_loadTournament_ownershipNotEnforced() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
		when(mailAutomationService.listRulesForTournament(TOURNAMENT_ID)).thenReturn(List.of());

		service.listAutomationRules(null, TOURNAMENT_ID, false);

		// An Admin acts across every chain, so there is no branch to check them against
		verify(branchAccessService, never()).canActorAccessBranch(any(), any());
	}

	// ══════════════ preview ══════════════

	@Test
	@DisplayName("TC-006 · A preview by template id renders against the sample context")
	void TC006_preview_byTemplateId() {
		givenAccessibleTournament();
		when(mailContextBuilder.previewContext(TOURNAMENT_ID, 21L)).thenReturn(Map.of("tournament.name", "Summer Open 2026"));
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(mailRenderService.render(any(EmailTemplate.class), anyMap())).thenReturn(rendered());

		EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest();
		request.setTemplateId(TEMPLATE_ID);
		request.setSampleRegistrationId(21L);

		RenderedEmailResponse response = service.preview(USER_ID, TOURNAMENT_ID, true, request);

		assertEquals("Nhắc lịch Summer Open 2026", response.getSubject());
		// The tournament id is stamped back onto the request so the caller cannot preview one
		// tournament's template against another's data
		assertEquals(TOURNAMENT_ID, request.getTournamentId());
	}

	@Test
	@DisplayName("TC-007 · A preview with no id falls back to the template code")
	void TC007_preview_byTemplateCode() {
		givenAccessibleTournament();
		when(mailContextBuilder.previewContext(TOURNAMENT_ID, null)).thenReturn(Map.of());
		when(emailTemplateRepository.findByCode("TOURNAMENT_REMINDER")).thenReturn(Optional.of(template(true)));
		when(mailRenderService.render(any(EmailTemplate.class), anyMap())).thenReturn(rendered());

		EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest();
		request.setTemplateCode("TOURNAMENT_REMINDER");

		assertNotNull(service.preview(USER_ID, TOURNAMENT_ID, true, request));
		verify(emailTemplateRepository, never()).findById(any());
	}

	@Test
	@DisplayName("TC-008 · Previewing a template that does not exist")
	void TC008_preview_templateNotFound() {
		givenAccessibleTournament();
		when(mailContextBuilder.previewContext(TOURNAMENT_ID, null)).thenReturn(Map.of());
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

		EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest();
		request.setTemplateId(TEMPLATE_ID);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.preview(USER_ID, TOURNAMENT_ID, true, request));

		assertEquals(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-009 · Variables sent with the preview win over the sample context")
	void TC009_preview_variablesOverrideContext() {
		givenAccessibleTournament();
		when(mailContextBuilder.previewContext(TOURNAMENT_ID, null))
				.thenReturn(Map.of("tournament.name", "Summer Open 2026", "system.year", "2026"));
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(mailRenderService.render(any(EmailTemplate.class), anyMap())).thenReturn(rendered());

		EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest();
		request.setTemplateId(TEMPLATE_ID);
		request.setVariables(Map.of("tournament.name", "Tên thử"));

		service.preview(USER_ID, TOURNAMENT_ID, true, request);

		ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
		verify(mailRenderService).render(any(EmailTemplate.class), context.capture());
		// The point of the preview box is to try a value out, so it has to beat the sample
		assertEquals("Tên thử", context.getValue().get("tournament.name"));
		assertEquals("2026", context.getValue().get("system.year"));
	}

	// ══════════════ sendManual ══════════════

	@Test
	@DisplayName("TC-010 · A manual send queues one mail per recipient")
	void TC010_sendManual_queuesPerRecipient() {
		givenAccessibleTournament();
		givenSendableTemplate();
		when(mailRecipientResolver.resolve(eq(EmailRecipientType.ALL_PARTICIPANTS), eq(TOURNAMENT_ID), any()))
				.thenReturn(List.of(new MailRecipient(1L, "a@example.com"), new MailRecipient(2L, "b@example.com")));

		ManualSendResultResponse result =
				service.sendManual(USER_ID, TOURNAMENT_ID, true, manualRequest(EmailRecipientType.ALL_PARTICIPANTS));

		assertEquals(2, result.getQueuedCount());
		ArgumentCaptor<MailSendCommand> commands = ArgumentCaptor.forClass(MailSendCommand.class);
		verify(mailSendService, times(2)).queueAndSend(commands.capture());
		assertEquals(EmailTriggerType.MANUAL, commands.getAllValues().get(0).triggerType());
		assertEquals(USER_ID, commands.getAllValues().get(0).createdByUserId());
		assertEquals("a@example.com", commands.getAllValues().get(0).recipientEmail());
	}

	@Test
	@DisplayName("TC-011 · A disabled template cannot be sent")
	void TC011_sendManual_inactiveTemplate() {
		givenAccessibleTournament();
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(false)));

		BusinessException ex = assertThrows(BusinessException.class, () ->
				service.sendManual(USER_ID, TOURNAMENT_ID, true, manualRequest(EmailRecipientType.ALL_PARTICIPANTS)));

		// Disabling a template is how an Admin retires it — it must stop manual sends too
		assertEquals(ErrorCode.EMAIL_TEMPLATE_INACTIVE, ex.getErrorCode());
		verify(mailSendService, never()).queueAndSend(any());
	}

	@Test
	@DisplayName("TC-012 · A send that resolves to nobody is refused")
	void TC012_sendManual_noRecipient() {
		givenAccessibleTournament();
		when(emailTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(true)));
		when(mailRecipientResolver.resolve(any(), eq(TOURNAMENT_ID), any())).thenReturn(List.of());

		BusinessException ex = assertThrows(BusinessException.class, () ->
				service.sendManual(USER_ID, TOURNAMENT_ID, true, manualRequest(EmailRecipientType.CUSTOM_LIST)));

		// Better an error than a silent success that leaves the organiser thinking mail went out
		assertEquals(ErrorCode.EMAIL_RECIPIENT_EMPTY, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-013 · An edited subject renders through a throwaway template")
	void TC013_sendManual_subjectOverride() {
		givenAccessibleTournament();
		givenSendableTemplate();
		when(mailRecipientResolver.resolve(any(), eq(TOURNAMENT_ID), any()))
				.thenReturn(List.of(new MailRecipient(1L, "a@example.com")));
		when(mailRenderService.render(any(EmailTemplate.class), anyMap())).thenReturn(rendered());

		ManualSendEmailRequest request = manualRequest(EmailRecipientType.ALL_PARTICIPANTS);
		request.setSubjectOverride("Đổi giờ thi đấu");

		service.sendManual(USER_ID, TOURNAMENT_ID, true, request);

		ArgumentCaptor<EmailTemplate> renderTemplate = ArgumentCaptor.forClass(EmailTemplate.class);
		verify(mailRenderService).render(renderTemplate.capture(), anyMap());
		assertEquals("Đổi giờ thi đấu", renderTemplate.getValue().getSubjectTemplate());
		// The body keeps the stored text — only what was overridden changes
		assertEquals("<p>Xin chào {{registration.playerName}}</p>", renderTemplate.getValue().getBodyHtmlTemplate());
		MailSendCommand command = capturedCommands().get(0);
		assertNotNull(command.renderedOverride());
		// The log still points at the persisted template, so the history stays traceable
		assertEquals(TEMPLATE_ID, command.template().getId());
	}

	@Test
	@DisplayName("TC-014 · An unedited send renders nothing up front")
	void TC014_sendManual_noOverride() {
		givenAccessibleTournament();
		givenSendableTemplate();
		when(mailRecipientResolver.resolve(any(), eq(TOURNAMENT_ID), any()))
				.thenReturn(List.of(new MailRecipient(1L, "a@example.com")));

		service.sendManual(USER_ID, TOURNAMENT_ID, true, manualRequest(EmailRecipientType.ALL_PARTICIPANTS));

		// Without an override the send service does its own rendering, so doing it here would
		// be work thrown away
		assertNull(capturedCommands().get(0).renderedOverride());
		verify(mailRenderService, never()).render(any(EmailTemplate.class), anyMap());
	}

	@Test
	@DisplayName("TC-015 · A recipient tied to an entry gets the entry and payment variables")
	void TC015_sendManual_enrichesWithRegistration() {
		givenAccessibleTournament();
		givenSendableTemplate();
		Registration registration = Registration.builder().id(31L).playerFullName("Nguyễn Văn A").build();
		Payment payment = Payment.builder().id(41L).amount(new BigDecimal("300000"))
				.status(PaymentStatus.SUCCESS.getValue()).build();
		when(mailRecipientResolver.resolve(any(), eq(TOURNAMENT_ID), any()))
				.thenReturn(List.of(new MailRecipient(1L, "a@example.com", 31L)));
		when(registrationRepository.findById(31L)).thenReturn(Optional.of(registration));
		when(paymentRepository.findFirstByRegistrationIdAndStatusOrderByPaidAtDesc(31L, PaymentStatus.SUCCESS.getValue()))
				.thenReturn(Optional.of(payment));

		service.sendManual(USER_ID, TOURNAMENT_ID, true, manualRequest(EmailRecipientType.ALL_PARTICIPANTS));

		// {{registration.*}} and {{payment.*}} only resolve if the send fills them in per recipient
		verify(mailContextBuilder).putRegistration(anyMap(), eq(registration));
		verify(mailContextBuilder).putPayment(anyMap(), eq(payment));
	}

	@Test
	@DisplayName("TC-016 · A recipient with no entry skips the enrichment")
	void TC016_sendManual_recipientWithoutRegistration() {
		givenAccessibleTournament();
		givenSendableTemplate();
		when(mailRecipientResolver.resolve(any(), eq(TOURNAMENT_ID), any()))
				.thenReturn(List.of(new MailRecipient(1L, "staff@example.com")));

		service.sendManual(USER_ID, TOURNAMENT_ID, true, manualRequest(EmailRecipientType.ROLE_STAFF));

		// A staff member has no entry in the tournament, and that is not an error
		verify(registrationRepository, never()).findById(anyLong());
		verify(mailContextBuilder, never()).putRegistration(anyMap(), any());
	}

	@Test
	@DisplayName("TC-017 · An entry that has since been deleted does not stop the send")
	void TC017_sendManual_registrationGone() {
		givenAccessibleTournament();
		givenSendableTemplate();
		when(mailRecipientResolver.resolve(any(), eq(TOURNAMENT_ID), any()))
				.thenReturn(List.of(new MailRecipient(1L, "a@example.com", 31L)));
		when(registrationRepository.findById(31L)).thenReturn(Optional.empty());

		ManualSendResultResponse result =
				service.sendManual(USER_ID, TOURNAMENT_ID, true, manualRequest(EmailRecipientType.ALL_PARTICIPANTS));

		assertEquals(1, result.getQueuedCount());
		verify(mailContextBuilder, never()).putRegistration(anyMap(), any());
		verify(paymentRepository, never())
				.findFirstByRegistrationIdAndStatusOrderByPaidAtDesc(anyLong(), any());
	}

	@Test
	@DisplayName("TC-018 · Variables sent with the send win over the tournament context")
	void TC018_sendManual_variablesOverride() {
		givenAccessibleTournament();
		givenSendableTemplate();
		when(mailRecipientResolver.resolve(any(), eq(TOURNAMENT_ID), any()))
				.thenReturn(List.of(new MailRecipient(1L, "a@example.com")));

		ManualSendEmailRequest request = manualRequest(EmailRecipientType.ALL_PARTICIPANTS);
		request.setVariables(Map.of("custom.note", "Mang theo cơ riêng"));

		service.sendManual(USER_ID, TOURNAMENT_ID, true, request);

		Map<String, Object> variables = capturedCommands().get(0).variables();
		assertEquals("Mang theo cơ riêng", variables.get("custom.note"));
		// The system context is still there beneath the caller's additions
		assertEquals("2026", variables.get("system.year"));
	}

	// ══════════════ automation rules ══════════════

	@Test
	@DisplayName("TC-019 · Listing the automation rules of a tournament")
	void TC019_listAutomationRules_delegates() {
		givenAccessibleTournament();
		when(mailAutomationService.listRulesForTournament(TOURNAMENT_ID))
				.thenReturn(List.of(EmailAutomationRuleResponse.builder().id(1L).code("ON_APPROVED").build()));

		List<EmailAutomationRuleResponse> rules = service.listAutomationRules(USER_ID, TOURNAMENT_ID, true);

		assertEquals(1, rules.size());
		assertEquals("ON_APPROVED", rules.get(0).getCode());
	}

	@Test
	@DisplayName("TC-020 · Toggling a rule that does not exist")
	void TC020_setRuleEnabled_ruleNotFound() {
		givenAccessibleTournament();
		when(emailAutomationRuleRepository.findById(2L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.setRuleEnabled(USER_ID, TOURNAMENT_ID, true, 2L, false));

		assertEquals(ErrorCode.EMAIL_RULE_NOT_FOUND, ex.getErrorCode());
		verify(mailAutomationService, never()).setEnabled(anyLong(), any(Boolean.class));
	}

	@Test
	@DisplayName("TC-021 · A rule belonging to another tournament cannot be toggled from this one")
	void TC021_setRuleEnabled_ruleOfAnotherTournament() {
		givenAccessibleTournament();
		EmailAutomationRule foreign = EmailAutomationRule.builder()
				.id(2L).code("ON_APPROVED")
				.tournament(Tournament.builder().id(88L).name("Autumn Cup").build())
				.build();
		when(emailAutomationRuleRepository.findById(2L)).thenReturn(Optional.of(foreign));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.setRuleEnabled(USER_ID, TOURNAMENT_ID, true, 2L, false));

		// Reported as not-found rather than forbidden, so the id of another tournament's rule
		// cannot be confirmed by probing
		assertEquals(ErrorCode.EMAIL_RULE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-022 · A chain-wide rule can be switched off for one tournament")
	void TC022_setRuleEnabled_globalRule() {
		givenAccessibleTournament();
		EmailAutomationRule global = EmailAutomationRule.builder().id(2L).code("ON_APPROVED").tournament(null).build();
		when(emailAutomationRuleRepository.findById(2L)).thenReturn(Optional.of(global));
		when(mailAutomationService.setEnabled(2L, false))
				.thenReturn(EmailAutomationRuleResponse.builder().id(2L).code("ON_APPROVED").build());

		assertEquals(2L, service.setRuleEnabled(USER_ID, TOURNAMENT_ID, true, 2L, false).getId());
	}

	// ══════════════ send log ══════════════

	@Test
	@DisplayName("TC-023 · The send log of a tournament, unfiltered")
	void TC023_listLogs_noStatusFilter() {
		givenAccessibleTournament();
		EmailSendLog log = EmailSendLog.builder()
				.id(1L).template(template(true)).tournament(tournament())
				.triggerType(EmailTriggerType.MANUAL.name())
				.recipientEmail("a@example.com").subjectRendered("Nhắc lịch")
				.status(EmailSendStatus.SENT.name())
				.build();
		when(emailSendLogRepository.findByTournamentId(eq(TOURNAMENT_ID), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(log)));

		PageResponse<EmailSendLogResponse> logs = service.listLogs(USER_ID, TOURNAMENT_ID, true, null, 0, 20);

		assertEquals(1, logs.getContent().size());
		assertEquals("TOURNAMENT_REMINDER", logs.getContent().get(0).getTemplateCode());
		// The status is translated for display so the screen never shows a raw enum name
		assertEquals("Đã gửi", logs.getContent().get(0).getStatusDisplayName());
		verify(emailSendLogRepository, never()).findByTournamentIdAndStatus(any(), any(), any());
	}

	@Test
	@DisplayName("TC-024 · A status filter narrows the send log")
	void TC024_listLogs_withStatusFilter() {
		givenAccessibleTournament();
		when(emailSendLogRepository.findByTournamentIdAndStatus(
				eq(TOURNAMENT_ID), eq(EmailSendStatus.FAILED.name()), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		service.listLogs(USER_ID, TOURNAMENT_ID, true, EmailSendStatus.FAILED.name(), 0, 20);

		verify(emailSendLogRepository, never()).findByTournamentId(any(), any());
	}

	@Test
	@DisplayName("TC-025 · A log row with no template, rule or tournament still renders")
	void TC025_listLogs_sparseRow() {
		givenAccessibleTournament();
		EmailSendLog sparse = EmailSendLog.builder()
				.id(2L).recipientEmail("a@example.com")
				.status(EmailSendStatus.FAILED.name()).errorMessage("SMTP timeout")
				.build();
		when(emailSendLogRepository.findByTournamentId(eq(TOURNAMENT_ID), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(sparse)));

		EmailSendLogResponse response =
				service.listLogs(USER_ID, TOURNAMENT_ID, true, "  ", 0, 20).getContent().get(0);

		// A template deleted after the mail went out must not take the whole log screen down
		assertNull(response.getTemplateCode());
		assertNull(response.getRuleCode());
		assertNull(response.getTournamentId());
		assertEquals("SMTP timeout", response.getErrorMessage());
	}
}
