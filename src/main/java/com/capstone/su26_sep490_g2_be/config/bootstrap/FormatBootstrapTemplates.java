package com.capstone.su26_sep490_g2_be.config.bootstrap;

import com.capstone.su26_sep490_g2_be.dto.request.UpsertFormatConfigFieldsRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpsertFormatRaceToRulesRequest;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@UtilityClass
public class FormatBootstrapTemplates {

	private static final Map<String, List<UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest>> CONFIG_FIELDS = Map.of(
			"SINGLE_ELIMINATION", List.of(
					field("bracket_size", "16"),
					field("allow_bye", "true"),
					field("seeding_enabled", "true"),
					field("third_place_match", "true"),
					field("break_rule", "ALTERNATE_BREAK"),
					field("lag_for_break", "true"),
					field("scoring_unit", "GAME", false),
					field("is_show_tournament", "false"),
					field("is_public_ratio", "false"),
					field("is_register", "false")
			),
			"DOUBLE_ELIMINATION", List.of(
					field("bracket_size", "16"),
					field("allow_bye", "true"),
					field("seeding_enabled", "true"),
					field("grand_final_bracket_reset", "false"),
					field("break_rule", "ALTERNATE_BREAK"),
					field("lag_for_break", "true"),
					field("scoring_unit", "GAME", false),
					field("is_show_tournament", "false"),
					field("is_public_ratio", "false"),
					field("is_register", "false")
			),
			"GROUP_PLAYOFF", List.of(
					field("break_rule", "ALTERNATE_BREAK"),
					field("lag_for_break", "true"),
					field("scoring_unit", "GAME", false),
					field("is_show_tournament", "false"),
					field("is_public_ratio", "false"),
					field("is_register", "false"),
					field("group_count", "4"),
					field("players_per_group", "4"),
					field("advance_per_group", "2"),
					field("group_assignment", "RANDOM"),
					field("group_points_win", "1"),
					field("group_points_loss", "0"),
					field("group_tiebreaker_order", "HEAD_TO_HEAD,SCORE_DIFF"),
					field("playoff_bracket_size", "8"),
					field("playoff_bye_top_seeds", "0")
			)
	);

	private static final Map<String, List<UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest>> RACE_TO_RULES = Map.of(
			"SINGLE_ELIMINATION", List.of(
					rule("round_1", "Vòng 1", "KNOCKOUT", 5),
					rule("quarter_final", "Tứ kết", "KNOCKOUT", 7),
					rule("semi_final", "Bán kết", "KNOCKOUT", 7),
					rule("third_place", "Tranh hạng 3", "KNOCKOUT", 7),
					rule("final", "Chung kết", "KNOCKOUT", 9)
			),
			"DOUBLE_ELIMINATION", List.of(
					rule("winners_r1", "NT — Vòng 1", "WINNERS", 5),
					rule("winners_qf", "NT — Tứ kết", "WINNERS", 7),
					rule("winners_sf", "NT — Bán kết", "WINNERS", 7),
					rule("losers_r1", "NTh — Vòng 1", "LOSERS", 5),
					rule("losers_r2", "NTh — Vòng 2", "LOSERS", 7),
					rule("losers_r3", "NTh — Vòng 3", "LOSERS", 7),
					rule("losers_final", "NTh — Chung kết nhánh", "LOSERS", 7),
					rule("grand_final", "Chung kết lớn", "GRAND_FINAL", 9)
			),
			"GROUP_PLAYOFF", List.of(
					rule("group_default", "Vòng bảng", "GROUP", 5),
					rule("playoff_r1", "Playoff — Vòng 1", "PLAYOFF", 7),
					rule("playoff_qf", "Playoff — Tứ kết", "PLAYOFF", 7),
					rule("playoff_sf", "Playoff — Bán kết", "PLAYOFF", 7),
					rule("playoff_final", "Playoff — Chung kết", "KNOCKOUT", 9),
					rule("third_place", "Tranh hạng 3", "KNOCKOUT", 7)
			)
	);

	public static Optional<List<UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest>> configFields(String formatCode) {
		return Optional.ofNullable(CONFIG_FIELDS.get(formatCode));
	}

	public static Optional<List<UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest>> raceToRules(String formatCode) {
		return Optional.ofNullable(RACE_TO_RULES.get(formatCode));
	}

	public static boolean isSupported(String formatCode) {
		return CONFIG_FIELDS.containsKey(formatCode);
	}

	private static UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest field(String key, String value) {
		return field(key, value, true);
	}

	private static UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest field(
			String key, String value, boolean visibleToOwner) {
		UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest item =
				new UpsertFormatConfigFieldsRequest.FormatConfigFieldItemRequest();
		item.setFieldKey(key);
		item.setDefaultValue(value);
		item.setIsRequired(true);
		item.setIsVisibleToOwner(visibleToOwner);
		return item;
	}

	private static UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest rule(
			String roundKey, String label, String phase, int raceTo) {
		UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest item =
				new UpsertFormatRaceToRulesRequest.FormatRaceToRuleItemRequest();
		item.setRoundKey(roundKey);
		item.setLabel(label);
		item.setBracketPhase(phase);
		item.setRaceTo(raceTo);
		return item;
	}
}
