package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MailProperties;
import com.capstone.su26_sep490_g2_be.dto.response.RenderedEmailResponse;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.entity.MailLayoutSettings;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.EmailTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.MailLayoutSettingsRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link MailRenderServiceImpl} and {@link MailContextBuilder}.
 *
 * <p>Mirrors the <b>MailRendering</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — FT-26 / FT-27, the email use cases (UC-42…48). The sub-numbers go
 * in when the rest of Wave 5 is written up.
 *
 * <p>These two decide what a Player actually receives. The escaping rule is the sharp edge: every
 * value is HTML-escaped except the {@code custom} namespace, which an Owner types by hand and
 * which is therefore run through an OWASP allow-list instead. Get that backwards and a tournament
 * name becomes a script tag in every inbox it reaches.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · MailRendering — FT-26, FT-27")
class MailRenderingTest {

	@Mock EmailTemplateRepository templateRepository;
	@Mock MailLayoutSettingsRepository mailLayoutSettingsRepository;

	@InjectMocks MailRenderServiceImpl renderService;

	@Mock MailProperties mailProperties;
	@Mock TournamentRepository tournamentRepository;
	@Mock RegistrationRepository registrationRepository;

	@InjectMocks MailContextBuilder contextBuilder;

	private static EmailTemplate template(String subject, String body) {
		return EmailTemplate.builder()
				.id(9L).code("REG_APPROVED").name("Đăng ký được duyệt")
				.subjectTemplate(subject).bodyHtmlTemplate(body).isActive(true)
				.build();
	}

	private void givenDefaultLayout() {
		when(mailLayoutSettingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
	}

	private static Map<String, Object> ctx(Object... pairs) {
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	// ══════════════════════════ render — the substitution ══════════════════════════

	@Test
	@DisplayName("TC-001 · A nested context is flattened into dotted placeholders")
	void TC001_render_flattensNestedContext() {
		givenDefaultLayout();

		RenderedEmailResponse rendered = renderService.render(
				template("Chào {{user.fullName}}", "<p>Giải {{tournament.name}}</p>"),
				ctx("user", Map.of("fullName", "Nguyễn Văn A"),
						"tournament", Map.of("name", "Summer Open 2026")));

		assertEquals("Chào Nguyễn Văn A", rendered.getSubject());
		assertTrue(rendered.getBodyHtml().contains("<p>Giải Summer Open 2026</p>"));
	}

	@Test
	@DisplayName("TC-002 · A placeholder with no value is left as it stands")
	void TC002_render_unknownPlaceholderUntouched() {
		givenDefaultLayout();

		RenderedEmailResponse rendered = renderService.render(
				template("{{user.fullName}}", "<p>{{match.score}}</p>"), ctx());

		// StringSubstitutor leaves an unresolved token alone rather than blanking it, which makes
		// a template typo visible in the preview instead of silently producing an empty line
		assertEquals("{{user.fullName}}", rendered.getSubject());
		assertTrue(rendered.getBodyHtml().contains("{{match.score}}"));
	}

	@Test
	@DisplayName("TC-003 · A null value renders as an empty string, not the word null")
	void TC003_render_nullValueBecomesEmpty() {
		givenDefaultLayout();
		Map<String, Object> context = new HashMap<>();
		Map<String, Object> user = new HashMap<>();
		user.put("fullName", null);
		context.put("user", user);

		RenderedEmailResponse rendered = renderService.render(template("Chào {{user.fullName}}", "<p>x</p>"), context);

		assertEquals("Chào ", rendered.getSubject());
	}

	@Test
	@DisplayName("TC-004 · A non-string value is written out through toString")
	void TC004_render_nonStringValue() {
		givenDefaultLayout();

		RenderedEmailResponse rendered = renderService.render(
				template("Vòng {{match.roundNo}}", "<p>x</p>"),
				ctx("match", Map.of("roundNo", 3)));

		assertEquals("Vòng 3", rendered.getSubject());
	}

	@Test
	@DisplayName("TC-005 · Deeply nested namespaces keep their full dotted path")
	void TC005_render_deepNesting() {
		givenDefaultLayout();

		RenderedEmailResponse rendered = renderService.render(
				template("{{a.b.c}}", "<p>x</p>"),
				ctx("a", Map.of("b", Map.of("c", "sâu"))));

		assertEquals("sâu", rendered.getSubject());
	}

	// ══════════════════════════ render — the escaping rule ══════════════════════════

	@Test
	@DisplayName("TC-006 · A tournament name carrying markup is escaped in the body")
	void TC006_render_escapesHtmlInBody() {
		givenDefaultLayout();

		RenderedEmailResponse rendered = renderService.render(
				template("x", "<p>{{tournament.name}}</p>"),
				ctx("tournament", Map.of("name", "<script>alert(1)</script>")));

		// The name is typed by an Owner and reaches every entrant's inbox — it must arrive as text
		assertFalse(rendered.getBodyHtml().contains("<script>"));
		assertTrue(rendered.getBodyHtml().contains("&lt;script&gt;"));
	}

	@Test
	@DisplayName("TC-007 · The subject is not HTML-escaped, because it is not HTML")
	void TC007_render_subjectNotEscaped() {
		givenDefaultLayout();

		RenderedEmailResponse rendered = renderService.render(
				template("{{tournament.name}}", "<p>x</p>"),
				ctx("tournament", Map.of("name", "Giải \"Mở rộng\" & Cúp")));

		// Escaping here would show &quot; and &amp; in the inbox subject line
		assertEquals("Giải \"Mở rộng\" & Cúp", rendered.getSubject());
	}

	@Test
	@DisplayName("TC-008 · The custom namespace keeps safe formatting tags")
	void TC008_render_customNamespaceAllowsFormatting() {
		givenDefaultLayout();

		RenderedEmailResponse rendered = renderService.render(
				template("x", "<div>{{custom.message}}</div>"),
				ctx("custom", Map.of("message", "<p>Xin <b>chào</b></p>")));

		// An Owner writes this in a rich-text box, so the formatting has to survive
		assertTrue(rendered.getBodyHtml().contains("<b>chào</b>"));
	}

	@Test
	@DisplayName("TC-009 · The custom namespace still strips a script tag")
	void TC009_render_customNamespaceStripsScript() {
		givenDefaultLayout();

		RenderedEmailResponse rendered = renderService.render(
				template("x", "<div>{{custom.message}}</div>"),
				ctx("custom", Map.of("message", "Xin chào<script>alert(1)</script>")));

		// Allow-list, not deny-list: formatting is permitted and everything else is dropped
		assertFalse(rendered.getBodyHtml().contains("<script"));
		assertFalse(rendered.getBodyHtml().contains("alert(1)"));
		assertTrue(rendered.getBodyHtml().contains("Xin chào"));
	}

	@Test
	@DisplayName("TC-010 · The custom namespace drops an inline event handler")
	void TC010_render_customNamespaceStripsEventHandler() {
		givenDefaultLayout();

		RenderedEmailResponse rendered = renderService.render(
				template("x", "<div>{{custom.message}}</div>"),
				ctx("custom", Map.of("message", "<b onclick=\"steal()\">bấm vào đây</b>")));

		assertFalse(rendered.getBodyHtml().contains("onclick"));
		assertTrue(rendered.getBodyHtml().contains("bấm vào đây"));
	}

	@Test
	@DisplayName("TC-011 · A namespace merely starting with the word custom is still escaped")
	void TC011_render_customPrefixIsExact() {
		givenDefaultLayout();

		RenderedEmailResponse rendered = renderService.render(
				template("x", "<div>{{customer.note}}</div>"),
				ctx("customer", Map.of("note", "<b>đậm</b>")));

		// The check is "custom" or "custom." — a namespace called customer must not inherit the
		// relaxed policy by accident
		assertTrue(rendered.getBodyHtml().contains("&lt;b&gt;"));
	}

	// ══════════════════════════ render — the layout wrapper ══════════════════════════

	@Test
	@DisplayName("TC-012 · The rendered body is wrapped in the branded frame")
	void TC012_render_wrapsInLayout() {
		givenDefaultLayout();

		String html = renderService.render(template("x", "<p>Nội dung</p>"), ctx()).getBodyHtml();

		assertTrue(html.startsWith("<!DOCTYPE html>"));
		assertTrue(html.contains("<p>Nội dung</p>"));
		assertTrue(html.trim().endsWith("</html>"));
	}

	@Test
	@DisplayName("TC-013 · The header and footer stored by the Admin are used")
	void TC013_render_usesStoredLayout() {
		when(mailLayoutSettingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(
				MailLayoutSettings.builder()
						.headerHtml("<h1>CLB Bi-a Sao Mai</h1>")
						.footerHtml("<span>Liên hệ 0901234567</span>")
						.build()));

		String html = renderService.render(template("x", "<p>Nội dung</p>"), ctx()).getBodyHtml();

		assertTrue(html.contains("<h1>CLB Bi-a Sao Mai</h1>"));
		assertTrue(html.contains("<span>Liên hệ 0901234567</span>"));
	}

	@Test
	@DisplayName("TC-014 · With no layout row the built-in header and footer are used")
	void TC014_render_fallsBackToDefaultLayout() {
		givenDefaultLayout();

		String html = renderService.render(template("x", "<p>Nội dung</p>"), ctx()).getBodyHtml();

		// A fresh install has no settings row and must still send a branded email
		assertTrue(html.contains(MailLayoutSettings.DEFAULT_FOOTER_HTML.substring(0, 20)));
	}

	@Test
	@DisplayName("TC-015 · Placeholders inside the header and footer are substituted too")
	void TC015_render_layoutPlaceholdersSubstituted() {
		when(mailLayoutSettingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(
				MailLayoutSettings.builder()
						.headerHtml("<h1>{{system.appName}}</h1>")
						.footerHtml("<span>© {{system.currentYear}}</span>")
						.build()));

		String html = renderService.render(template("x", "<p>x</p>"),
				ctx("system", Map.of("appName", "BTMS", "currentYear", "2026"))).getBodyHtml();

		assertTrue(html.contains("<h1>BTMS</h1>"));
		assertTrue(html.contains("© 2026"));
	}

	// ══════════════════════════ render — failure and renderByCode ══════════════════════════

	@Test
	@DisplayName("TC-016 · A value that cannot be turned into text is reported as a render failure")
	void TC016_render_wrapsUnexpectedFailure() {
		Object hostile = new Object() {
			@Override
			public String toString() {
				throw new IllegalStateException("lazy proxy not initialised");
			}
		};

		BusinessException ex = assertThrows(BusinessException.class,
				() -> renderService.render(template("Chào {{user.fullName}}", "<p>x</p>"),
						ctx("user", Map.of("fullName", hostile))));

		// A detached JPA proxy in the context throws on toString; wrapping it means the caller
		// gets a mail-specific error naming the template rather than a bare runtime exception
		assertEquals(ErrorCode.EMAIL_RENDER_FAILED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-017 · Rendering by code loads the template first")
	void TC017_renderByCode_loadsTemplate() {
		givenDefaultLayout();
		when(templateRepository.findByCode("REG_APPROVED"))
				.thenReturn(Optional.of(template("Chào {{user.fullName}}", "<p>x</p>")));

		RenderedEmailResponse rendered = renderService.renderByCode("REG_APPROVED",
				ctx("user", Map.of("fullName", "Nguyễn Văn A")));

		assertEquals("Chào Nguyễn Văn A", rendered.getSubject());
	}

	@Test
	@DisplayName("TC-018 · Rendering by a code that does not exist")
	void TC018_renderByCode_templateNotFound() {
		when(templateRepository.findByCode("NO_SUCH")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> renderService.renderByCode("NO_SUCH", ctx()));

		assertEquals(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ MailContextBuilder ══════════════════════════

	@Test
	@DisplayName("TC-019 · The system namespace carries the app name and the current year")
	void TC019_systemContext() {
		when(mailProperties.getAppName()).thenReturn("BTMS");
		when(mailProperties.getSupportEmail()).thenReturn("support@btms.vn");

		@SuppressWarnings("unchecked")
		Map<String, Object> system = (Map<String, Object>) contextBuilder.systemContext().get("system");

		assertEquals("BTMS", system.get("appName"));
		assertEquals("support@btms.vn", system.get("supportEmail"));
		assertEquals(String.valueOf(LocalDate.now().getYear()), system.get("currentYear"));
	}

	@Test
	@DisplayName("TC-020 · A user with a profile is addressed by their full name")
	void TC020_putUser_usesProfileName() {
		Map<String, Object> context = new HashMap<>();
		User user = User.builder().id(1L).email("a@example.com")
				.profile(UserProfile.builder().userId(1L).fullName("Nguyễn Văn A").build())
				.build();

		contextBuilder.putUser(context, user);

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) context.get("user");
		assertEquals("Nguyễn Văn A", map.get("fullName"));
		assertEquals("a@example.com", map.get("email"));
	}

	@Test
	@DisplayName("TC-021 · A user with no profile is addressed by their address")
	void TC021_putUser_fallsBackToEmail() {
		Map<String, Object> context = new HashMap<>();

		contextBuilder.putUser(context, User.builder().id(1L).email("a@example.com").build());

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) context.get("user");
		// "Chào a@example.com" is awkward but readable; "Chào null" is not
		assertEquals("a@example.com", map.get("fullName"));
	}

	@Test
	@DisplayName("TC-022 · A null entity adds no namespace at all")
	void TC022_putters_ignoreNull() {
		Map<String, Object> context = new HashMap<>();

		contextBuilder.putUser(context, null);
		contextBuilder.putTournament(context, null);
		contextBuilder.putRegistration(context, null);
		contextBuilder.putMatch(context, null);
		contextBuilder.putPayment(context, null);

		// The placeholder then renders as its own token rather than the word null
		assertTrue(context.isEmpty());
	}

	@Test
	@DisplayName("TC-023 · A tournament start date is written in Vietnam time")
	void TC023_putTournament_formatsDate() {
		Map<String, Object> context = new HashMap<>();
		Tournament tournament = Tournament.builder()
				.id(77L).name("Summer Open 2026").status("IN_PROGRESS")
				.startAt(Instant.parse("2026-08-19T18:00:00Z"))
				.build();

		contextBuilder.putTournament(context, tournament);

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) context.get("tournament");
		// 18:00 UTC is already the 20th in Hanoi, and the entrant reads it in local time
		assertEquals("2026-08-20", map.get("startAt"));
		assertEquals("Summer Open 2026", map.get("name"));
	}

	@Test
	@DisplayName("TC-024 · A tournament with no start date leaves the placeholder empty")
	void TC024_putTournament_nullDate() {
		Map<String, Object> context = new HashMap<>();

		contextBuilder.putTournament(context, Tournament.builder().id(77L).name("x").status("DRAFT").build());

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) context.get("tournament");
		assertEquals("", map.get("startAt"));
	}

	@Test
	@DisplayName("TC-025 · A registration pulls its tournament and its user in with it")
	void TC025_putRegistration_cascades() {
		Map<String, Object> context = new HashMap<>();
		Registration registration = Registration.builder()
				.id(31L).playerFullName("Nguyễn Văn A").status("APPROVED")
				.tournament(Tournament.builder().id(77L).name("Summer Open 2026").status("IN_PROGRESS").build())
				.user(User.builder().id(1L).email("a@example.com").build())
				.build();

		contextBuilder.putRegistration(context, registration);

		// One call fills three namespaces, so a template may mix {{registration.*}} with
		// {{tournament.*}} and {{user.*}} without the caller wiring each one
		assertTrue(context.containsKey("registration"));
		assertTrue(context.containsKey("tournament"));
		assertTrue(context.containsKey("user"));
	}

	@Test
	@DisplayName("TC-026 · A match with an empty slot still renders both player names")
	void TC026_putMatch_emptySlot() {
		Map<String, Object> context = new HashMap<>();
		Match match = Match.builder()
				.id(1L).roundNo(2).player1Score(3).player2Score(1)
				.player1(Participant.builder().id(11L).displayName("Nguyễn Văn A").build())
				.player2(null)
				.build();

		contextBuilder.putMatch(context, match);

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) context.get("match");
		assertEquals("Nguyễn Văn A", map.get("player1Name"));
		// Map.of refuses a null value, so the empty slot has to become an empty string here
		assertEquals("", map.get("player2Name"));
		assertEquals("3 - 1", map.get("score"));
	}

	@Test
	@DisplayName("TC-027 · A payment with no checkout link renders an empty one")
	void TC027_putPayment_nullCheckoutUrl() {
		Map<String, Object> context = new HashMap<>();

		contextBuilder.putPayment(context, Payment.builder()
				.id(41L).amount(new BigDecimal("300000")).status("SUCCESS").build());

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) context.get("payment");
		assertEquals("300000 VNĐ", map.get("amount"));
		assertEquals("", map.get("checkoutUrl"));
	}

	@Test
	@DisplayName("TC-028 · The sample context fills every namespace a template can use")
	void TC028_sampleContext_coversEveryNamespace() {
		when(mailProperties.getAppName()).thenReturn("BTMS");
		when(mailProperties.getSupportEmail()).thenReturn("support@btms.vn");

		Map<String, Object> sample = contextBuilder.sampleContext();

		// The preview must never show a bare {{token}} — the Admin would read it as a broken template
		for (String namespace : new String[] {"system", "user", "tournament", "registration", "match", "payment", "custom"}) {
			assertTrue(sample.containsKey(namespace), "missing namespace " + namespace);
		}
	}

	@Test
	@DisplayName("TC-029 · A preview with a real entry overlays the sample data")
	void TC029_previewContext_withRegistration() {
		lenient().when(mailProperties.getAppName()).thenReturn("BTMS");
		lenient().when(mailProperties.getSupportEmail()).thenReturn("support@btms.vn");
		when(registrationRepository.findById(31L)).thenReturn(Optional.of(Registration.builder()
				.id(31L).playerFullName("Trần Thị B").status("APPROVED").build()));

		Map<String, Object> context = contextBuilder.previewContext(77L, 31L);

		@SuppressWarnings("unchecked")
		Map<String, Object> registration = (Map<String, Object>) context.get("registration");
		assertEquals("Trần Thị B", registration.get("playerFullName"));
		// The entry wins over the tournament id, so only one lookup is issued
		verify(tournamentRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("TC-030 · A preview with only a tournament id overlays that tournament")
	void TC030_previewContext_withTournamentOnly() {
		lenient().when(mailProperties.getAppName()).thenReturn("BTMS");
		lenient().when(mailProperties.getSupportEmail()).thenReturn("support@btms.vn");
		when(tournamentRepository.findById(77L)).thenReturn(Optional.of(
				Tournament.builder().id(77L).name("Summer Open 2026").status("IN_PROGRESS").build()));

		Map<String, Object> context = contextBuilder.previewContext(77L, null);

		@SuppressWarnings("unchecked")
		Map<String, Object> tournament = (Map<String, Object>) context.get("tournament");
		assertEquals("Summer Open 2026", tournament.get("name"));
	}

	@Test
	@DisplayName("TC-031 · A preview with no ids at all is still fully populated")
	void TC031_previewContext_noIds() {
		when(mailProperties.getAppName()).thenReturn("BTMS");
		when(mailProperties.getSupportEmail()).thenReturn("support@btms.vn");

		Map<String, Object> context = contextBuilder.previewContext(null, null);

		assertTrue(context.containsKey("tournament"));
		verify(registrationRepository, never()).findById(anyLong());
		verify(tournamentRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("TC-032 · An entry id that no longer resolves leaves the sample data in place")
	void TC032_previewContext_registrationGone() {
		lenient().when(mailProperties.getAppName()).thenReturn("BTMS");
		lenient().when(mailProperties.getSupportEmail()).thenReturn("support@btms.vn");
		when(registrationRepository.findById(31L)).thenReturn(Optional.empty());

		Map<String, Object> context = contextBuilder.previewContext(77L, 31L);

		@SuppressWarnings("unchecked")
		Map<String, Object> registration = (Map<String, Object>) context.get("registration");
		// The preview degrades to the sample rather than showing a half-empty template
		assertEquals("Nguyễn Văn A", registration.get("playerFullName"));
	}
}
