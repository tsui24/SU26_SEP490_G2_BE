package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.EmailAutomationRuleRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmailAutomationRuleResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailAutomationRule;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.EmailRecipientType;
import com.capstone.su26_sep490_g2_be.enums.EmailScope;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.EmailAutomationRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.EmailTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link MailAutomationServiceImpl}.
 *
 * <p>Mirrors the <b>MailAutomationService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — FT-26, the email automation use cases (UC-42…48), numbered when
 * that wave is written up.
 *
 * <p>This class decides which rules fire for an event. The rule that matters most is the
 * precedence one: a rule written for a single tournament replaces the chain-wide rule rather than
 * adding to it, so a player never receives the same mail twice.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · MailAutomationService — FT-26")
class MailAutomationServiceImplTest {

	@Mock EmailAutomationRuleRepository ruleRepository;
	@Mock EmailTemplateRepository templateRepository;
	@Mock TournamentRepository tournamentRepository;
	@Mock UserRepository userRepository;

	@InjectMocks MailAutomationServiceImpl service;

	private static final Long TOURNAMENT_ID = 77L;
	private static final Long TEMPLATE_ID = 9L;
	private static final Long USER_ID = 4L;

	private static EmailTemplate template() {
		return EmailTemplate.builder()
				.id(TEMPLATE_ID).code("REG_APPROVED").name("Đăng ký được duyệt").build();
	}

	private static EmailAutomationRule rule(Long id, String code, Tournament tournament) {
		return EmailAutomationRule.builder()
				.id(id).code(code).name("Duyệt đăng ký")
				.eventType(EmailEventType.REGISTRATION_APPROVED.name())
				.template(template())
				.scope(tournament != null ? EmailScope.TOURNAMENT.getValue() : EmailScope.GLOBAL.getValue())
				.tournament(tournament)
				.recipientType(EmailRecipientType.REGISTRATION_USER.name())
				.isEnabled(true).delayMinutes(0)
				.build();
	}

	private static Tournament tournament() {
		return Tournament.builder().id(TOURNAMENT_ID).name("Summer Open 2026").build();
	}

	private static EmailAutomationRuleRequest request(Long tournamentId, Integer delayMinutes) {
		EmailAutomationRuleRequest request = new EmailAutomationRuleRequest();
		request.setCode("ON_APPROVED");
		request.setName("Duyệt đăng ký");
		request.setDescription("Gửi khi BTC duyệt một đăng ký");
		request.setEventType(EmailEventType.REGISTRATION_APPROVED.name());
		request.setTemplateId(TEMPLATE_ID);
		request.setRecipientType(EmailRecipientType.REGISTRATION_USER.name());
		request.setTournamentId(tournamentId);
		request.setDelayMinutes(delayMinutes);
		return request;
	}

	private EmailAutomationRule savedRule() {
		ArgumentCaptor<EmailAutomationRule> captor = ArgumentCaptor.forClass(EmailAutomationRule.class);
		verify(ruleRepository).save(captor.capture());
		return captor.getValue();
	}

	// ══════════════════════════ reading the rules ══════════════════════════

	@Test
	@DisplayName("TC-001 · The Admin list holds the chain-wide rules, paged in memory")
	void TC001_listGlobalRules_paged() {
		when(ruleRepository.findByScope(EmailScope.GLOBAL.getValue()))
				.thenReturn(List.of(rule(1L, "ON_APPROVED", null), rule(2L, "ON_REJECTED", null)));

		PageResponse<EmailAutomationRuleResponse> response = service.listGlobalRules(0, 1);

		// The scope is not something the database can page on cheaply, so the slice is in memory
		assertEquals(1, response.getContent().size());
		assertEquals(2L, response.getTotalElements());
		assertEquals(2, response.getTotalPages());
		assertEquals("ON_APPROVED", response.getContent().get(0).getCode());
	}

	@Test
	@DisplayName("TC-002 · The response spells out the event and recipient types")
	void TC002_toResponse_displayNames() {
		when(ruleRepository.findByScope(EmailScope.GLOBAL.getValue()))
				.thenReturn(List.of(rule(1L, "ON_APPROVED", null)));

		EmailAutomationRuleResponse response = service.listGlobalRules(0, 20).getContent().get(0);

		assertEquals("Đăng ký được duyệt", response.getEventTypeDisplayName());
		assertEquals("Người đăng ký", response.getRecipientTypeDisplayName());
		assertEquals(TEMPLATE_ID, response.getTemplateId());
		assertEquals("REG_APPROVED", response.getTemplateCode());
		// A chain-wide rule carries no tournament id
		assertNull(response.getTournamentId());
	}

	@Test
	@DisplayName("TC-003 · A tournament sees its own rules alongside the chain-wide ones")
	void TC003_listRulesForTournament_ownRulesFirst() {
		when(ruleRepository.findByTournamentId(TOURNAMENT_ID))
				.thenReturn(List.of(rule(1L, "TOUR_APPROVED", tournament())));
		when(ruleRepository.findByScope(EmailScope.GLOBAL.getValue()))
				.thenReturn(List.of(rule(2L, "ON_APPROVED", null)));

		List<EmailAutomationRuleResponse> rules = service.listRulesForTournament(TOURNAMENT_ID);

		// Both are listed so the organiser can see what a tournament rule is overriding
		assertEquals(2, rules.size());
		assertEquals("TOUR_APPROVED", rules.get(0).getCode());
		assertEquals(TOURNAMENT_ID, rules.get(0).getTournamentId());
		assertEquals("ON_APPROVED", rules.get(1).getCode());
	}

	@Test
	@DisplayName("TC-004 · A tournament with no rules of its own still sees the chain-wide ones")
	void TC004_listRulesForTournament_onlyGlobal() {
		when(ruleRepository.findByTournamentId(TOURNAMENT_ID)).thenReturn(List.of());
		when(ruleRepository.findByScope(EmailScope.GLOBAL.getValue()))
				.thenReturn(List.of(rule(2L, "ON_APPROVED", null)));

		assertEquals(1, service.listRulesForTournament(TOURNAMENT_ID).size());
	}

	// ══════════════════════════ createRule ══════════════════════════

	@Test
	@DisplayName("TC-005 · A rule written for one tournament is scoped to it")
	void TC005_createRule_tournamentScope() {
		when(ruleRepository.existsByCode("ON_APPROVED")).thenReturn(false);
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).build()));
		when(ruleRepository.save(any(EmailAutomationRule.class))).thenAnswer(inv -> inv.getArgument(0));

		service.createRule(USER_ID, request(TOURNAMENT_ID, 15));

		EmailAutomationRule saved = savedRule();
		assertEquals(EmailScope.TOURNAMENT.getValue(), saved.getScope());
		assertEquals(TOURNAMENT_ID, saved.getTournament().getId());
		assertEquals(15, saved.getDelayMinutes());
		// A new rule is live the moment it is created — there is no separate activation step
		assertTrue(saved.getIsEnabled());
		assertEquals(USER_ID, saved.getCreatedBy().getId());
	}

	@Test
	@DisplayName("TC-006 · A rule with no tournament is chain-wide")
	void TC006_createRule_globalScope() {
		when(ruleRepository.existsByCode("ON_APPROVED")).thenReturn(false);
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
		when(ruleRepository.save(any(EmailAutomationRule.class))).thenAnswer(inv -> inv.getArgument(0));

		service.createRule(null, request(null, null));

		EmailAutomationRule saved = savedRule();
		assertEquals(EmailScope.GLOBAL.getValue(), saved.getScope());
		assertNull(saved.getTournament());
		// An omitted delay means send immediately, not send never
		assertEquals(0, saved.getDelayMinutes());
		assertNull(saved.getCreatedBy());
		verify(tournamentRepository, never()).findById(anyLong());
		verify(userRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("TC-007 · A rule code already in use is rejected")
	void TC007_createRule_duplicateCode() {
		when(ruleRepository.existsByCode("ON_APPROVED")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createRule(USER_ID, request(null, 0)));

		assertEquals(ErrorCode.EMAIL_RULE_CODE_EXISTS, ex.getErrorCode());
		verify(ruleRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-008 · A rule pointing at a template that does not exist is rejected")
	void TC008_createRule_templateNotFound() {
		when(ruleRepository.existsByCode("ON_APPROVED")).thenReturn(false);
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createRule(USER_ID, request(null, 0)));

		// The template is what the rule sends; without it the rule could never fire
		assertEquals(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND, ex.getErrorCode());
		verify(ruleRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-009 · A tournament id that does not resolve leaves the rule chain-wide")
	void TC009_createRule_unknownTournamentFallsBackToGlobal() {
		when(ruleRepository.existsByCode("ON_APPROVED")).thenReturn(false);
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).build()));
		when(ruleRepository.save(any(EmailAutomationRule.class))).thenAnswer(inv -> inv.getArgument(0));

		service.createRule(USER_ID, request(TOURNAMENT_ID, 0));

		// The lookup fails soft rather than throwing, so the rule silently widens to the chain —
		// worth knowing, because it is not what an Admin who typed the id would expect
		assertEquals(EmailScope.GLOBAL.getValue(), savedRule().getScope());
	}

	// ══════════════════════════ updateRule and setEnabled ══════════════════════════

	@Test
	@DisplayName("TC-010 · Editing a rule leaves its code and scope alone")
	void TC010_updateRule_keepsCodeAndScope() {
		EmailAutomationRule existing = rule(1L, "ON_APPROVED", tournament());
		when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
		when(ruleRepository.save(existing)).thenReturn(existing);

		EmailAutomationRuleRequest request = request(null, 30);
		request.setName("Duyệt đăng ký (đã sửa)");

		EmailAutomationRuleResponse response = service.updateRule(1L, request);

		assertEquals("Duyệt đăng ký (đã sửa)", response.getName());
		assertEquals(30, response.getDelayMinutes());
		// The code is the key other rows point at, and the scope is decided once at creation
		assertEquals("ON_APPROVED", response.getCode());
		assertEquals(EmailScope.TOURNAMENT.getValue(), response.getScope());
	}

	@Test
	@DisplayName("TC-011 · An omitted delay on update is read as no delay")
	void TC011_updateRule_nullDelayBecomesZero() {
		EmailAutomationRule existing = rule(1L, "ON_APPROVED", null);
		existing.setDelayMinutes(60);
		when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
		when(ruleRepository.save(existing)).thenReturn(existing);

		// An omitted field resets the delay here rather than preserving it — this is a replace,
		// not a patch, which matters when the caller sends a partial body
		assertEquals(0, service.updateRule(1L, request(null, null)).getDelayMinutes());
	}

	@Test
	@DisplayName("TC-012 · Editing a rule that does not exist")
	void TC012_updateRule_notFound() {
		when(ruleRepository.findById(1L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateRule(1L, request(null, 0)));

		assertEquals(ErrorCode.EMAIL_RULE_NOT_FOUND, ex.getErrorCode());
		verify(templateRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("TC-013 · Editing a rule onto a template that does not exist is rejected")
	void TC013_updateRule_templateNotFound() {
		when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule(1L, "ON_APPROVED", null)));
		when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateRule(1L, request(null, 0)));

		assertEquals(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND, ex.getErrorCode());
		verify(ruleRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-014 · Switching a rule off keeps the row")
	void TC014_setEnabled_disables() {
		EmailAutomationRule existing = rule(1L, "ON_APPROVED", null);
		when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
		when(ruleRepository.save(existing)).thenReturn(existing);

		assertFalse(service.setEnabled(1L, false).getIsEnabled());
		// Switching off is how an Admin pauses a rule; deleting it would lose the configuration
		verify(ruleRepository, never()).delete(any(EmailAutomationRule.class));
	}

	@Test
	@DisplayName("TC-015 · Switching a rule that does not exist")
	void TC015_setEnabled_notFound() {
		when(ruleRepository.findById(1L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.setEnabled(1L, true));

		assertEquals(ErrorCode.EMAIL_RULE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ resolveActiveRulesForEvent ══════════════════════════

	@Test
	@DisplayName("TC-016 · A tournament rule replaces the chain-wide one rather than adding to it")
	void TC016_resolveActiveRules_tournamentRuleWins() {
		when(ruleRepository.findByEventTypeAndIsEnabledTrueAndTournamentId(
				EmailEventType.REGISTRATION_APPROVED.getValue(), TOURNAMENT_ID))
				.thenReturn(List.of(rule(1L, "TOUR_APPROVED", tournament())));

		List<EmailAutomationRule> rules = service.resolveActiveRulesForEvent(
				EmailEventType.REGISTRATION_APPROVED, TOURNAMENT_ID);

		assertEquals(1, rules.size());
		assertEquals("TOUR_APPROVED", rules.get(0).getCode());
		// Falling through as well would mail the player twice for one approval
		verify(ruleRepository, never()).findByEventTypeAndIsEnabledTrueAndTournamentIsNull(anyString());
	}

	@Test
	@DisplayName("TC-017 · With no tournament rule the chain-wide rules fire")
	void TC017_resolveActiveRules_fallsBackToGlobal() {
		when(ruleRepository.findByEventTypeAndIsEnabledTrueAndTournamentId(
				EmailEventType.REGISTRATION_APPROVED.getValue(), TOURNAMENT_ID))
				.thenReturn(List.of());
		when(ruleRepository.findByEventTypeAndIsEnabledTrueAndTournamentIsNull(
				EmailEventType.REGISTRATION_APPROVED.getValue()))
				.thenReturn(List.of(rule(2L, "ON_APPROVED", null)));

		List<EmailAutomationRule> rules = service.resolveActiveRulesForEvent(
				EmailEventType.REGISTRATION_APPROVED, TOURNAMENT_ID);

		assertEquals("ON_APPROVED", rules.get(0).getCode());
	}

	@Test
	@DisplayName("TC-018 · An event with no tournament attached only looks at the chain-wide rules")
	void TC018_resolveActiveRules_noTournamentContext() {
		when(ruleRepository.findByEventTypeAndIsEnabledTrueAndTournamentIsNull(
				EmailEventType.PAYMENT_SUCCESS.getValue()))
				.thenReturn(List.of(rule(3L, "ON_PAID", null)));

		List<EmailAutomationRule> rules =
				service.resolveActiveRulesForEvent(EmailEventType.PAYMENT_SUCCESS, null);

		assertEquals(1, rules.size());
		verify(ruleRepository, never()).findByEventTypeAndIsEnabledTrueAndTournamentId(anyString(), anyLong());
	}

	@Test
	@DisplayName("TC-019 · An event nobody configured a rule for fires nothing")
	void TC019_resolveActiveRules_noRuleAtAll() {
		when(ruleRepository.findByEventTypeAndIsEnabledTrueAndTournamentIsNull(
				EmailEventType.PAYMENT_FAILED.getValue()))
				.thenReturn(List.of());

		// An empty list rather than an error: not every event has to send mail
		assertTrue(service.resolveActiveRulesForEvent(EmailEventType.PAYMENT_FAILED, null).isEmpty());
	}
}
