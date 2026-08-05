package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MailProperties;
import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailRecipientType;
import com.capstone.su26_sep490_g2_be.enums.EmailSendStatus;
import com.capstone.su26_sep490_g2_be.enums.EmailTriggerType;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.repository.EmailSendLogRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.EmailQueuedEvent;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.MailRenderService;
import com.capstone.su26_sep490_g2_be.service.MailSendCommand;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link MailRecipientResolverImpl}, {@link MailSendServiceImpl} and
 * {@link MailDispatcher}.
 *
 * <p>Mirrors the <b>MailDelivery</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — FT-26 / FT-27, the email use cases (UC-42…48), numbered when the
 * rest of Wave 5 is written up.
 *
 * <p>Between them these three answer "who gets it", "is this a duplicate" and "did it actually
 * leave". The de-duplication in the resolver and the idempotency window in the sender are the two
 * places where a mistake means a Player receives the same mail twice.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · MailDelivery — FT-26, FT-27")
class MailDeliveryTest {

	@Mock ParticipantRepository participantRepository;
	@Mock RegistrationRepository registrationRepository;
	@Mock UserRepository userRepository;
	@Mock TournamentRepository tournamentRepository;

	@InjectMocks MailRecipientResolverImpl resolver;

	@Mock MailRenderService mailRenderService;
	@Mock EmailSendLogRepository emailSendLogRepository;
	@Mock ApplicationEventPublisher eventPublisher;

	@InjectMocks MailSendServiceImpl sendService;

	@Mock JavaMailSender mailSender;
	@Mock MailProperties mailProperties;

	@InjectMocks MailDispatcher dispatcher;

	private static final Long TOURNAMENT_ID = 77L;

	private static User user(long id, String email) {
		return User.builder().id(id).email(email).build();
	}

	private static Registration registration(long id, User user) {
		return Registration.builder().id(id).user(user).status(RegistrationStatus.APPROVED.getValue()).build();
	}

	private static Participant participant(long id, Registration registration) {
		return Participant.builder().id(id).displayName("P" + id).registration(registration).build();
	}

	private static EmailTemplate template() {
		return EmailTemplate.builder().id(9L).code("REG_APPROVED").name("Đăng ký được duyệt")
				.subjectTemplate("s").bodyHtmlTemplate("<p>b</p>").isActive(true).build();
	}

	private static MailSendCommand.MailSendCommandBuilder command() {
		return MailSendCommand.builder()
				.template(template())
				.recipientEmail("a@example.com")
				.triggerType(EmailTriggerType.MANUAL)
				.variables(java.util.Map.of());
	}

	private EmailSendLog capturedLog() {
		ArgumentCaptor<EmailSendLog> captor = ArgumentCaptor.forClass(EmailSendLog.class);
		verify(emailSendLogRepository).save(captor.capture());
		return captor.getValue();
	}

	// ══════════════ the recipient resolver ══════════════

	@Test
	@DisplayName("TC-001 · Every active entrant with an account is a recipient")
	void TC001_resolve_allParticipants() {
		when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(List.of(
						participant(1L, registration(31L, user(1L, "a@example.com"))),
						participant(2L, registration(32L, user(2L, "b@example.com")))));

		List<MailRecipient> recipients = resolver.resolve(EmailRecipientType.ALL_PARTICIPANTS, TOURNAMENT_ID, null);

		assertEquals(2, recipients.size());
		// The entry id travels with the address so the mail can fill in {{registration.*}}
		assertTrue(recipients.stream().allMatch(r -> r.registrationId() != null));
	}

	@Test
	@DisplayName("TC-002 · A player entered twice is mailed once")
	void TC002_resolve_deduplicatesByUser() {
		User same = user(1L, "a@example.com");
		when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(List.of(
						participant(1L, registration(31L, same)),
						participant(2L, registration(32L, same))));

		List<MailRecipient> recipients = resolver.resolve(EmailRecipientType.ALL_PARTICIPANTS, TOURNAMENT_ID, null);

		// A doubles tournament lists the same person in two pairings; two identical mails would
		// read as a system fault
		assertEquals(1, recipients.size());
		assertEquals(1L, recipients.get(0).userId());
	}

	@Test
	@DisplayName("TC-003 · A participant added by hand is skipped, having no address")
	void TC003_resolve_skipsManualParticipant() {
		when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(List.of(
						participant(1L, null),
						participant(2L, registration(32L, user(2L, "b@example.com")))));

		List<MailRecipient> recipients = resolver.resolve(EmailRecipientType.ALL_PARTICIPANTS, TOURNAMENT_ID, null);

		assertEquals(1, recipients.size());
		assertEquals("b@example.com", recipients.get(0).email());
	}

	@Test
	@DisplayName("TC-004 · An entry whose account has no address is skipped")
	void TC004_resolve_skipsAccountWithoutEmail() {
		when(participantRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue()))
				.thenReturn(List.of(participant(1L, registration(31L, user(1L, null)))));

		assertTrue(resolver.resolve(EmailRecipientType.ALL_PARTICIPANTS, TOURNAMENT_ID, null).isEmpty());
	}

	@Test
	@DisplayName("TC-005 · Only approved entries are mailed on the registration list")
	void TC005_resolve_approvedRegistrationsOnly() {
		when(registrationRepository.findByTournamentIdAndStatus(
				eq(TOURNAMENT_ID), eq(RegistrationStatus.APPROVED.getValue()), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(registration(31L, user(1L, "a@example.com")))));

		List<MailRecipient> recipients = resolver.resolve(EmailRecipientType.REGISTRATION_USER, TOURNAMENT_ID, null);

		// A rejected or unpaid entrant must not receive a "you are in" mail
		assertEquals(1, recipients.size());
		assertEquals(31L, recipients.get(0).registrationId());
	}

	@Test
	@DisplayName("TC-006 · The staff list is taken from the tournament's own chain")
	void TC006_resolve_staffOfTheOwningChain() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				Tournament.builder().id(TOURNAMENT_ID).name("Summer Open").createdBy(user(4L, "owner@x.com")).build()));
		when(userRepository.searchStaffsByManager(eq(4L), eq(null), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(user(5L, "staff@x.com"))));

		List<MailRecipient> recipients = resolver.resolve(EmailRecipientType.ROLE_STAFF, TOURNAMENT_ID, null);

		assertEquals(1, recipients.size());
		// Staff have no entry in the tournament, so no registration id travels with them
		assertNull(recipients.get(0).registrationId());
	}

	@Test
	@DisplayName("TC-007 · A tournament that does not resolve yields no staff")
	void TC007_resolve_staffTournamentGone() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		assertTrue(resolver.resolve(EmailRecipientType.ROLE_STAFF, TOURNAMENT_ID, null).isEmpty());
		verify(userRepository, never()).searchStaffsByManager(anyLong(), any(), any(Pageable.class));
	}

	@Test
	@DisplayName("TC-008 · A typed address matching an entrant is linked to their entry")
	void TC008_resolve_customListLinksKnownUser() {
		when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user(1L, "a@example.com")));
		when(registrationRepository.findByTournamentIdAndUserId(TOURNAMENT_ID, 1L))
				.thenReturn(Optional.of(registration(31L, user(1L, "a@example.com"))));

		List<MailRecipient> recipients = resolver.resolve(
				EmailRecipientType.CUSTOM_LIST, TOURNAMENT_ID, List.of("a@example.com"));

		// Typing an entrant's address by hand should still fill in {{registration.*}}
		assertEquals(1L, recipients.get(0).userId());
		assertEquals(31L, recipients.get(0).registrationId());
	}

	@Test
	@DisplayName("TC-009 · A typed address nobody holds is still mailed, unlinked")
	void TC009_resolve_customListUnknownAddress() {
		when(userRepository.findByEmail("guest@example.com")).thenReturn(Optional.empty());

		List<MailRecipient> recipients = resolver.resolve(
				EmailRecipientType.CUSTOM_LIST, TOURNAMENT_ID, List.of("guest@example.com"));

		assertEquals(1, recipients.size());
		assertNull(recipients.get(0).userId());
		assertNull(recipients.get(0).registrationId());
		verify(registrationRepository, never()).findByTournamentIdAndUserId(anyLong(), anyLong());
	}

	@Test
	@DisplayName("TC-010 · An account with no entry in this tournament is linked to no entry")
	void TC010_resolve_customListUserWithoutRegistration() {
		when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user(4L, "owner@example.com")));
		when(registrationRepository.findByTournamentIdAndUserId(TOURNAMENT_ID, 4L)).thenReturn(Optional.empty());

		List<MailRecipient> recipients = resolver.resolve(
				EmailRecipientType.CUSTOM_LIST, TOURNAMENT_ID, List.of("owner@example.com"));

		assertEquals(4L, recipients.get(0).userId());
		assertNull(recipients.get(0).registrationId());
	}

	@Test
	@DisplayName("TC-011 · Blank and repeated addresses are cleaned out of the typed list")
	void TC011_resolve_customListCleaned() {
		when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.empty());
		lenient().when(registrationRepository.findByTournamentIdAndUserId(anyLong(), anyLong()))
				.thenReturn(Optional.empty());

		List<String> typed = new java.util.ArrayList<>(List.of("a@example.com", "  ", "a@example.com"));
		typed.add(null);

		List<MailRecipient> recipients = resolver.resolve(EmailRecipientType.CUSTOM_LIST, TOURNAMENT_ID, typed);

		// A textarea produces blank lines and repeats; each address is mailed exactly once
		assertEquals(1, recipients.size());
	}

	@Test
	@DisplayName("TC-012 · A typed list that was never filled in yields nobody")
	void TC012_resolve_customListNull() {
		assertTrue(resolver.resolve(EmailRecipientType.CUSTOM_LIST, TOURNAMENT_ID, null).isEmpty());
	}

	@Test
	@DisplayName("TC-013 · The two match-scoped types resolve to nobody here")
	void TC013_resolve_matchScopedTypesAreEmpty() {
		assertTrue(resolver.resolve(EmailRecipientType.PLAYER, TOURNAMENT_ID, null).isEmpty());
		assertTrue(resolver.resolve(EmailRecipientType.MATCH_PLAYERS, TOURNAMENT_ID, null).isEmpty());
		// They depend on which match fired the event, which only the publisher knows
	}

	@Test
	@DisplayName("TC-014 · Every tournament-scoped type yields nobody without a tournament")
	void TC014_resolve_noTournamentId() {
		for (EmailRecipientType type : List.of(
				EmailRecipientType.ALL_PARTICIPANTS,
				EmailRecipientType.REGISTRATION_USER,
				EmailRecipientType.ROLE_STAFF)) {
			assertTrue(resolver.resolve(type, null, null).isEmpty(), type.name());
		}
	}

	// ══════════════ queueAndSend ══════════════

	@Test
	@DisplayName("TC-015 · A queued mail is logged and announced for dispatch")
	void TC015_queueAndSend_logsAndPublishes() {
		when(mailRenderService.render(any(EmailTemplate.class), any()))
				.thenReturn(RenderedEmailResponse.builder().subject("Chào").bodyHtml("<p>x</p>").build());
		when(emailSendLogRepository.save(any(EmailSendLog.class)))
				.thenAnswer(inv -> { EmailSendLog l = inv.getArgument(0); l.setId(1L); return l; });

		EmailSendLog saved = sendService.queueAndSend(command().build());

		assertEquals(EmailSendStatus.QUEUED.getValue(), saved.getStatus());
		assertEquals("Chào", saved.getSubjectRendered());
		// The dispatcher listens after commit, so the event is what actually sends the mail
		verify(eventPublisher).publishEvent(any(EmailQueuedEvent.class));
	}

	@Test
	@DisplayName("TC-016 · A pre-rendered mail is not rendered a second time")
	void TC016_queueAndSend_usesRenderedOverride() {
		when(emailSendLogRepository.save(any(EmailSendLog.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		sendService.queueAndSend(command()
				.renderedOverride(RenderedEmailResponse.builder().subject("Đổi giờ").bodyHtml("<p>y</p>").build())
				.build());

		assertEquals("Đổi giờ", capturedLog().getSubjectRendered());
		verify(mailRenderService, never()).render(any(EmailTemplate.class), any());
	}

	@Test
	@DisplayName("TC-017 · A repeat inside the idempotency window is not queued again")
	void TC017_queueAndSend_skipsDuplicate() {
		EmailSendLog existing = EmailSendLog.builder().id(1L).status(EmailSendStatus.SENT.getValue()).build();
		when(emailSendLogRepository.findFirstByIdempotencyKeyAndCreatedAtAfter(eq("reg-31-approved"), any(Instant.class)))
				.thenReturn(Optional.of(existing));

		EmailSendLog result = sendService.queueAndSend(command().idempotencyKey("reg-31-approved").build());

		// The same domain event can fire twice on a retry; the entrant must not be mailed twice
		assertEquals(existing, result);
		verify(emailSendLogRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any(EmailQueuedEvent.class));
	}

	@Test
	@DisplayName("TC-018 · The same key outside the window is queued as a new mail")
	void TC018_queueAndSend_outsideWindowSendsAgain() {
		when(emailSendLogRepository.findFirstByIdempotencyKeyAndCreatedAtAfter(anyString(), any(Instant.class)))
				.thenReturn(Optional.empty());
		when(mailRenderService.render(any(EmailTemplate.class), any()))
				.thenReturn(RenderedEmailResponse.builder().subject("s").bodyHtml("<p>b</p>").build());
		when(emailSendLogRepository.save(any(EmailSendLog.class))).thenAnswer(inv -> inv.getArgument(0));

		sendService.queueAndSend(command().idempotencyKey("reg-31-approved").build());

		// The window is five minutes, not forever — a reminder resent next week is a real send
		assertEquals("reg-31-approved", capturedLog().getIdempotencyKey());
		verify(eventPublisher).publishEvent(any(EmailQueuedEvent.class));
	}

	@Test
	@DisplayName("TC-019 · A mail with no key skips the duplicate check entirely")
	void TC019_queueAndSend_noIdempotencyKey() {
		when(mailRenderService.render(any(EmailTemplate.class), any()))
				.thenReturn(RenderedEmailResponse.builder().subject("s").bodyHtml("<p>b</p>").build());
		when(emailSendLogRepository.save(any(EmailSendLog.class))).thenAnswer(inv -> inv.getArgument(0));

		sendService.queueAndSend(command().build());

		verify(emailSendLogRepository, never())
				.findFirstByIdempotencyKeyAndCreatedAtAfter(anyString(), any(Instant.class));
	}

	@Test
	@DisplayName("TC-020 · The recipient and the sender are resolved onto the log")
	void TC020_queueAndSend_resolvesUsers() {
		when(mailRenderService.render(any(EmailTemplate.class), any()))
				.thenReturn(RenderedEmailResponse.builder().subject("s").bodyHtml("<p>b</p>").build());
		when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "a@example.com")));
		when(userRepository.findById(4L)).thenReturn(Optional.of(user(4L, "owner@example.com")));
		when(emailSendLogRepository.save(any(EmailSendLog.class))).thenAnswer(inv -> inv.getArgument(0));

		sendService.queueAndSend(command().recipientUserId(1L).createdByUserId(4L).build());

		EmailSendLog log = capturedLog();
		assertEquals(1L, log.getRecipientUser().getId());
		assertEquals(4L, log.getCreatedBy().getId());
	}

	@Test
	@DisplayName("TC-021 · A mail to an address with no account is still logged")
	void TC021_queueAndSend_noRecipientUser() {
		when(mailRenderService.render(any(EmailTemplate.class), any()))
				.thenReturn(RenderedEmailResponse.builder().subject("s").bodyHtml("<p>b</p>").build());
		when(emailSendLogRepository.save(any(EmailSendLog.class))).thenAnswer(inv -> inv.getArgument(0));

		sendService.queueAndSend(command().build());

		EmailSendLog log = capturedLog();
		// A typed address belongs to nobody in the system, and that is a normal manual send
		assertNull(log.getRecipientUser());
		assertNull(log.getCreatedBy());
		assertEquals("a@example.com", log.getRecipientEmail());
		verify(userRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("TC-022 · An account deleted since the send was composed does not stop the mail")
	void TC022_queueAndSend_recipientUserGone() {
		when(mailRenderService.render(any(EmailTemplate.class), any()))
				.thenReturn(RenderedEmailResponse.builder().subject("s").bodyHtml("<p>b</p>").build());
		when(userRepository.findById(1L)).thenReturn(Optional.empty());
		when(emailSendLogRepository.save(any(EmailSendLog.class))).thenAnswer(inv -> inv.getArgument(0));

		sendService.queueAndSend(command().recipientUserId(1L).build());

		assertNull(capturedLog().getRecipientUser());
		verify(eventPublisher).publishEvent(any(EmailQueuedEvent.class));
	}

	// ══════════════ the dispatcher ══════════════

	@Test
	@DisplayName("TC-023 · A queued log is sent and marked as delivered")
	void TC023_dispatch_marksSent() {
		EmailSendLog emailLog = EmailSendLog.builder()
				.id(1L).recipientEmail("a@example.com").subjectRendered("Chào")
				.bodyRendered("<p>x</p>").status(EmailSendStatus.QUEUED.getValue())
				.build();
		when(emailSendLogRepository.findById(1L)).thenReturn(Optional.of(emailLog));
		when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
		when(mailProperties.getFromAddress()).thenReturn("no-reply@btms.vn");
		when(mailProperties.getFromName()).thenReturn("BTMS");

		dispatcher.onEmailQueued(new EmailQueuedEvent(1L));

		assertEquals(EmailSendStatus.SENT.getValue(), emailLog.getStatus());
		assertNotNull(emailLog.getSentAt());
		assertNull(emailLog.getErrorMessage());
		verify(mailSender).send(any(MimeMessage.class));
		verify(emailSendLogRepository).save(emailLog);
	}

	@Test
	@DisplayName("TC-024 · A refused send is recorded with the reason instead of being lost")
	void TC024_dispatch_marksFailed() {
		EmailSendLog emailLog = EmailSendLog.builder()
				.id(1L).recipientEmail("a@example.com").subjectRendered("Chào")
				.bodyRendered("<p>x</p>").status(EmailSendStatus.QUEUED.getValue())
				.build();
		when(emailSendLogRepository.findById(1L)).thenReturn(Optional.of(emailLog));
		when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
		when(mailProperties.getFromAddress()).thenReturn("no-reply@btms.vn");
		when(mailProperties.getFromName()).thenReturn("BTMS");
		doThrow(new org.springframework.mail.MailSendException("SMTP timeout"))
				.when(mailSender).send(any(MimeMessage.class));

		dispatcher.onEmailQueued(new EmailQueuedEvent(1L));

		// The log is the only record the Owner can see, so a failure has to land in it
		assertEquals(EmailSendStatus.FAILED.getValue(), emailLog.getStatus());
		assertTrue(emailLog.getErrorMessage().contains("SMTP timeout"));
		assertNull(emailLog.getSentAt());
		verify(emailSendLogRepository).save(emailLog);
	}

	@Test
	@DisplayName("TC-025 · An event for a log that has gone is dropped quietly")
	void TC025_dispatch_logNotFound() {
		when(emailSendLogRepository.findById(1L)).thenReturn(Optional.empty());

		dispatcher.onEmailQueued(new EmailQueuedEvent(1L));

		// The listener runs after commit and asynchronously; throwing here would only fill the log
		verify(mailSender, never()).send(any(MimeMessage.class));
		verify(emailSendLogRepository, never()).save(any());
	}
}
