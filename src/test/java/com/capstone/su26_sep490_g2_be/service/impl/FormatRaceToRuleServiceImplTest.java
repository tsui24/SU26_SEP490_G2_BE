package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.FormatRaceToRule;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.FormatRaceToRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link FormatRaceToRuleServiceImpl}.
 *
 * <p>Mirrors the <b>FormatRaceToRuleService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-13 BR-04 and BR-05 (one race-to rule per round key).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · FormatRaceToRuleService — UC-13")
class FormatRaceToRuleServiceImplTest {

	@Mock FormatRaceToRuleRepository repository;

	@InjectMocks FormatRaceToRuleServiceImpl service;

	private static final String FORMAT = "SINGLE_ELIM";
	private static final String ROUND = "final";

	private static FormatRaceToRule rule(String roundKey, Integer raceTo, String phase) {
		return FormatRaceToRule.builder()
				.id(1L).formatCode(FORMAT).roundKey(roundKey)
				.raceTo(raceTo).bracketPhase(phase)
				.build();
	}

	@Test
	@DisplayName("TC-001 · Listing every race-to rule of a format")
	void TC001_getByFormat_returnsAll() {
		when(repository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(rule(ROUND, 9, "PLAYOFF"), rule("semi", 7, "PLAYOFF")));

		assertEquals(2, service.getByFormat(FORMAT).size());
	}

	@Test
	@DisplayName("TC-002 · Looking up the rule for one round")
	void TC002_findByFormatAndRoundKey_present() {
		when(repository.findByFormatCodeAndRoundKey(FORMAT, ROUND))
				.thenReturn(Optional.of(rule(ROUND, 9, "PLAYOFF")));

		Optional<FormatRaceToRule> found = service.findByFormatAndRoundKey(FORMAT, ROUND);

		assertTrue(found.isPresent());
		assertEquals(9, found.get().getRaceTo());
	}

	@Test
	@DisplayName("TC-003 · A round with no rule returns empty rather than throwing")
	void TC003_findByFormatAndRoundKey_absent() {
		when(repository.findByFormatCodeAndRoundKey(FORMAT, "no_such")).thenReturn(Optional.empty());

		// Returning Optional rather than throwing is deliberate: callers resolving a race-to
		// value fall back to the format default when a round carries no override
		assertTrue(service.findByFormatAndRoundKey(FORMAT, "no_such").isEmpty());
	}

	@Test
	@DisplayName("TC-004 · Upserting a round the format does not cover yet inserts it")
	void TC004_upsert_insertsWhenAbsent() {
		FormatRaceToRule incoming = rule(ROUND, 9, "PLAYOFF");
		when(repository.findByFormatCodeAndRoundKey(FORMAT, ROUND)).thenReturn(Optional.empty());
		when(repository.save(incoming)).thenReturn(incoming);

		assertSame(incoming, service.upsert(incoming));
	}

	@Test
	@DisplayName("TC-005 · Upserting a round already covered updates it in place")
	void TC005_upsert_updatesWhenPresent() {
		FormatRaceToRule existing = rule(ROUND, 7, "WINNERS");
		FormatRaceToRule incoming = rule(ROUND, 9, "PLAYOFF");
		when(repository.findByFormatCodeAndRoundKey(FORMAT, ROUND)).thenReturn(Optional.of(existing));
		when(repository.save(existing)).thenReturn(existing);

		FormatRaceToRule result = service.upsert(incoming);

		// UC-13 BR-05: within a format each round key may hold only one race-to rule
		assertSame(existing, result);
		assertEquals(9, existing.getRaceTo());
		assertEquals("PLAYOFF", existing.getBracketPhase());
		verify(repository, never()).save(incoming);
	}

	@Test
	@DisplayName("TC-006 · The round key itself is never rewritten by an upsert")
	void TC006_upsert_roundKeyUntouched() {
		FormatRaceToRule existing = rule(ROUND, 7, "WINNERS");
		when(repository.findByFormatCodeAndRoundKey(FORMAT, ROUND)).thenReturn(Optional.of(existing));
		when(repository.save(existing)).thenReturn(existing);

		service.upsert(rule(ROUND, 9, "PLAYOFF"));

		// Only the race-to target and bracket phase are copied across; the round key and format
		// code identify the row and stay put
		assertEquals(ROUND, existing.getRoundKey());
		assertEquals(FORMAT, existing.getFormatCode());
	}

	@Test
	@DisplayName("TC-007 · Deleting a race-to rule")
	void TC007_delete_happyPath() {
		FormatRaceToRule existing = rule(ROUND, 9, "PLAYOFF");
		when(repository.findById(1L)).thenReturn(Optional.of(existing));

		service.delete(1L);

		verify(repository).delete(existing);
	}

	@Test
	@DisplayName("TC-008 · Deleting a race-to rule that does not exist")
	void TC008_delete_notFound() {
		when(repository.findById(9999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(9999L));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(repository, never()).delete(any(FormatRaceToRule.class));
	}
}
