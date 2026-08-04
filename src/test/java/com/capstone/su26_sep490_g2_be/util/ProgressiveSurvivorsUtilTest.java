package com.capstone.su26_sep490_g2_be.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L1 unit tests for {@link ProgressiveSurvivorsUtil}.
 *
 * <p>Mirrors the <b>ProgressiveSurvivorsUtil</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-17 BR-14 (the survivor list of a progressive round robin).
 *
 * <p>The rule is enforced three times over the life of a tournament — when the configuration is
 * saved (against the advertised capacity), when registration closes and again at the draw (both
 * against the players who actually signed up) — and all three call this one utility.
 */
@DisplayName("L1 · ProgressiveSurvivorsUtil — UC-17 BR-14")
class ProgressiveSurvivorsUtilTest {

	// ══════════════════════════ parse ══════════════════════════

	@Test
	@DisplayName("TC-001 · A well-formed survivor list is read into numbers")
	void TC001_parse_happyPath() {
		assertEquals(List.of(10, 6, 4), ProgressiveSurvivorsUtil.parse("10,6,4"));
	}

	@Test
	@DisplayName("TC-002 · Spaces around the numbers are ignored")
	void TC002_parse_trimsWhitespace() {
		assertEquals(List.of(10, 6, 4), ProgressiveSurvivorsUtil.parse(" 10 , 6 , 4 "));
	}

	@Test
	@DisplayName("TC-003 · A trailing comma does not create a phantom stage")
	void TC003_parse_ignoresEmptyElements() {
		assertEquals(List.of(10, 6), ProgressiveSurvivorsUtil.parse("10,,6,"));
	}

	@Test
	@DisplayName("TC-004 · A single stage is a valid list")
	void TC004_parse_singleStage() {
		assertEquals(List.of(4), ProgressiveSurvivorsUtil.parse("4"));
	}

	@Test
	@DisplayName("TC-005 · An empty configuration is rejected")
	void TC005_parse_blankRejected() {
		assertThrows(IllegalArgumentException.class, () -> ProgressiveSurvivorsUtil.parse("   "));
	}

	@Test
	@DisplayName("TC-006 · A null configuration is rejected")
	void TC006_parse_nullRejected() {
		assertThrows(IllegalArgumentException.class, () -> ProgressiveSurvivorsUtil.parse(null));
	}

	@Test
	@DisplayName("TC-007 · A configuration of nothing but commas is rejected")
	void TC007_parse_onlySeparatorsRejected() {
		assertThrows(IllegalArgumentException.class, () -> ProgressiveSurvivorsUtil.parse(",,,"));
	}

	@Test
	@DisplayName("TC-008 · A value that is not a whole number is rejected by name")
	void TC008_parse_nonNumericRejected() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> ProgressiveSurvivorsUtil.parse("10,sáu,4"));

		assertTrue(ex.getMessage().contains("sáu"), "the message must name the offending value");
	}

	// ══════════════════════════ validate ══════════════════════════

	@Test
	@DisplayName("TC-009 · A survivor list that fits its tournament passes")
	void TC009_validate_happyPath() {
		assertTrue(ProgressiveSurvivorsUtil.validate(List.of(10, 6, 4), 16, 4).isEmpty());
	}

	@Test
	@DisplayName("TC-010 · The first stage has to eliminate somebody")
	void TC010_validate_firstStageMustCutSomeone() {
		List<String> errors = ProgressiveSurvivorsUtil.validate(List.of(16, 6, 4), 16, 4);

		assertEquals(1, errors.size());
		assertTrue(errors.get(0).contains("16"));
	}

	@Test
	@DisplayName("TC-011 · A stage may not keep as many players as the one before it")
	void TC011_validate_mustDecreaseStrictly() {
		List<String> errors = ProgressiveSurvivorsUtil.validate(List.of(10, 10, 4), 16, 4);

		assertTrue(errors.stream().anyMatch(e -> e.contains("giảm dần")));
	}

	@Test
	@DisplayName("TC-012 · A stage may not keep fewer than four players")
	void TC012_validate_minimumFour() {
		List<String> errors = ProgressiveSurvivorsUtil.validate(List.of(10, 6, 2), 16, 2);

		assertTrue(errors.stream().anyMatch(e -> e.contains("ít nhất 4")));
	}

	@Test
	@DisplayName("TC-013 · An odd number of survivors would leave somebody without an opponent")
	void TC013_validate_mustBeEven() {
		List<String> errors = ProgressiveSurvivorsUtil.validate(List.of(10, 7, 4), 16, 4);

		assertTrue(errors.stream().anyMatch(e -> e.contains("số chẵn")));
	}

	@Test
	@DisplayName("TC-014 · The last stage has to hand exactly the playoff field over")
	void TC014_validate_lastStageMatchesPlayoffSize() {
		List<String> errors = ProgressiveSurvivorsUtil.validate(List.of(10, 6, 4), 16, 8);

		assertTrue(errors.stream().anyMatch(e -> e.contains("Playoff")));
	}

	@Test
	@DisplayName("TC-015 · Every problem in one configuration is reported at once")
	void TC015_validate_reportsEveryProblemTogether() {
		List<String> errors = ProgressiveSurvivorsUtil.validate(List.of(7, 9), 6, 4);

		assertTrue(errors.size() >= 4, "the organiser should not have to fix these one save at a time");
	}

	@Test
	@DisplayName("TC-016 · An empty list is reported without looking any further")
	void TC016_validate_emptyList() {
		List<String> errors = ProgressiveSurvivorsUtil.validate(List.of(), 16, 4);

		assertEquals(1, errors.size());
	}

	@Test
	@DisplayName("TC-017 · A turnout smaller than the first stage demands is caught")
	void TC017_validate_turnoutBelowFirstStage() {
		// the same call the draw makes: maxParticipants is the real head count
		List<String> errors = ProgressiveSurvivorsUtil.validate(List.of(10, 6, 4), 8, 4);

		assertTrue(errors.stream().anyMatch(e -> e.contains("8")),
				"eight players cannot be cut down to ten");
	}
}
