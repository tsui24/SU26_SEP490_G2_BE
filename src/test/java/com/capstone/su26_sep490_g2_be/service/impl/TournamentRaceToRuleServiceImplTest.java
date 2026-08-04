package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.FormatRaceToRule;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentRaceToRule;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.FormatRaceToRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRaceToRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link TournamentRaceToRuleServiceImpl}.
 *
 * <p>Mirrors the <b>TournamentRaceToRuleService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-17 BR-06 (a race-to resolves from the tournament override
 * first and the format default second).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · TournamentRaceToRuleService — UC-17")
class TournamentRaceToRuleServiceImplTest {

	@Mock TournamentRaceToRuleRepository ruleRepository;
	@Mock FormatRaceToRuleRepository formatRuleRepository;
	@Mock TournamentRepository tournamentRepository;

	@InjectMocks TournamentRaceToRuleServiceImpl service;

	private static final Long TOURNAMENT_ID = 800L;
	private static final String FORMAT = "SINGLE_ELIMINATION";

	private static TournamentRaceToRule override(String roundKey, int raceTo, String phase) {
		return TournamentRaceToRule.builder()
				.id(1L).tournament(Tournament.builder().id(TOURNAMENT_ID).build())
				.roundKey(roundKey).raceTo(raceTo).bracketPhase(phase)
				.build();
	}

	@Test
	@DisplayName("TC-001 · A tournament override wins over the format default")
	void TC001_resolveRaceTo_overrideWins() {
		when(ruleRepository.findByTournamentIdAndRoundKey(TOURNAMENT_ID, "final"))
				.thenReturn(Optional.of(override("final", 11, "KNOCKOUT")));

		assertEquals(11, service.resolveRaceTo(TOURNAMENT_ID, FORMAT, "final"));
		verify(formatRuleRepository, never()).findByFormatCodeAndRoundKey(any(), any());
	}

	@Test
	@DisplayName("TC-002 · Without an override the format default is used")
	void TC002_resolveRaceTo_fallsBackToFormatDefault() {
		when(ruleRepository.findByTournamentIdAndRoundKey(TOURNAMENT_ID, "final"))
				.thenReturn(Optional.empty());
		when(formatRuleRepository.findByFormatCodeAndRoundKey(FORMAT, "final")).thenReturn(Optional.of(
				FormatRaceToRule.builder().id(1L).formatCode(FORMAT).roundKey("final").raceTo(9).build()));

		assertEquals(9, service.resolveRaceTo(TOURNAMENT_ID, FORMAT, "final"));
	}

	@Test
	@DisplayName("TC-003 · A round key neither the tournament nor the format defines")
	void TC003_resolveRaceTo_unknownRoundKey() {
		when(ruleRepository.findByTournamentIdAndRoundKey(TOURNAMENT_ID, "quarter_final"))
				.thenReturn(Optional.empty());
		when(formatRuleRepository.findByFormatCodeAndRoundKey(FORMAT, "quarter_final"))
				.thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.resolveRaceTo(TOURNAMENT_ID, FORMAT, "quarter_final"));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-004 · Overriding a round for the first time stores a new rule")
	void TC004_upsert_createsWhenAbsent() {
		TournamentRaceToRule fresh = override("final", 11, "KNOCKOUT");
		when(ruleRepository.findByTournamentIdAndRoundKey(TOURNAMENT_ID, "final")).thenReturn(Optional.empty());
		when(ruleRepository.save(fresh)).thenReturn(fresh);

		assertEquals(11, service.upsert(fresh).getRaceTo());
		verify(ruleRepository).save(fresh);
	}

	@Test
	@DisplayName("TC-005 · Overriding a round twice updates the rule rather than duplicating it")
	void TC005_upsert_updatesWhenPresent() {
		TournamentRaceToRule existing = override("final", 9, "KNOCKOUT");
		TournamentRaceToRule incoming = override("final", 11, "GRAND_FINAL");
		when(ruleRepository.findByTournamentIdAndRoundKey(TOURNAMENT_ID, "final"))
				.thenReturn(Optional.of(existing));
		when(ruleRepository.save(existing)).thenReturn(existing);

		TournamentRaceToRule saved = service.upsert(incoming);

		assertEquals(11, saved.getRaceTo());
		assertEquals("GRAND_FINAL", saved.getBracketPhase());
		assertEquals(existing, saved, "the round key is unique per tournament, so the row is reused");
	}

	@Test
	@DisplayName("TC-006 · Reading every override of a tournament")
	void TC006_getByTournament_delegates() {
		when(ruleRepository.findByTournamentId(TOURNAMENT_ID)).thenReturn(List.of());

		assertTrue(service.getByTournament(TOURNAMENT_ID).isEmpty());
	}

	@Test
	@DisplayName("TC-007 · Changing the format clears every override the old one had")
	void TC007_deleteByTournament_delegates() {
		service.deleteByTournament(TOURNAMENT_ID);

		verify(ruleRepository).deleteByTournamentId(TOURNAMENT_ID);
	}

	@Test
	@DisplayName("TC-008 · Setting one round back to its default deletes only that override")
	void TC008_deleteByTournamentAndRoundKey_delegates() {
		service.deleteByTournamentAndRoundKey(TOURNAMENT_ID, "final");

		verify(ruleRepository).deleteByTournamentIdAndRoundKey(TOURNAMENT_ID, "final");
		verify(ruleRepository, never()).deleteByTournamentId(any());
	}
}
