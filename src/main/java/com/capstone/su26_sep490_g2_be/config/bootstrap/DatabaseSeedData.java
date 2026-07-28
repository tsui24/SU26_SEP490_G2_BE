package com.capstone.su26_sep490_g2_be.config.bootstrap;

import com.capstone.su26_sep490_g2_be.entity.ConfigFieldDefinition;
import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Static seed data — Strategy B (data-init.md).
 */
@UtilityClass
public class DatabaseSeedData {

	public static List<ConfigFieldDefinition> configFieldCatalog() {
		return List.of(
				field("break_rule", "Luật break", "COMMON", "ENUM", "SELECT",
						"[\"ALTERNATE_BREAK\",\"WINNER_BREAK\",\"LOSER_BREAK\"]", null, null,
						"Luật xác định ai break sau mỗi rack"),
				field("lag_for_break", "Lag for break", "COMMON", "BOOLEAN", "CHECKBOX",
						null, null, null, "Bốc thăm quyền break game đầu"),
				field("scoring_unit", "Đơn vị tính điểm", "COMMON", "ENUM", "SELECT",
						"[\"GAME\",\"FRAME\"]", null, null, "Đơn vị thắng trận"),
				field("bracket_size", "Số slot bracket", "KNOCKOUT", "INT", "NUMBER",
						null, 8, 64, "Số slot trên nhánh đấu loại trực tiếp"),
				field("allow_bye", "Cho phép BYE", "KNOCKOUT", "BOOLEAN", "CHECKBOX",
						null, null, null, "Tự động thắng khi không đủ số người"),
				field("seeding_enabled", "Bốc hạt giống", "KNOCKOUT", "BOOLEAN", "CHECKBOX",
						null, null, null, "Sắp xếp vị trí theo hạt giống"),
				field("third_place_match", "Trận tranh hạng 3", "KNOCKOUT", "BOOLEAN", "CHECKBOX",
						null, null, null, "Tổ chức trận tranh hạng 3"),
				field("grand_final_bracket_reset", "Bracket reset chung kết", "DOUBLE_ELIM", "BOOLEAN", "CHECKBOX",
						null, null, null, "Nếu nhánh thua thắng chung kết lớn thì đánh thêm trận"),
				field("group_count", "Số bảng", "GROUP", "INT", "NUMBER",
						null, 2, 8, "Chia bao nhiêu bảng vòng bảng"),
				field("players_per_group", "Số người / bảng", "GROUP", "INT", "NUMBER",
						null, 3, 8, "Số participant tối đa mỗi bảng"),
				field("advance_per_group", "Số đội đi tiếp / bảng", "GROUP", "INT", "NUMBER",
						null, 1, 4, "Số đội top mỗi bảng vào playoff"),
				field("group_assignment", "Phân bảng", "GROUP", "ENUM", "SELECT",
						"[\"RANDOM\",\"SEEDED\",\"MANUAL\"]", null, null, "Cách xếp participant vào bảng"),
				field("group_points_win", "Điểm thắng vòng bảng", "GROUP", "INT", "NUMBER",
						null, 0, 3, "Điểm cộng khi thắng trận vòng bảng"),
				field("group_points_loss", "Điểm thua vòng bảng", "GROUP", "INT", "NUMBER",
						null, 0, 3, "Điểm cộng khi thua trận vòng bảng"),
				field("group_tiebreaker_order", "Thứ tự tie-break", "GROUP", "STRING", "TEXT",
						null, null, null, "Quy tắc xếp hạng khi bằng điểm, vd HEAD_TO_HEAD,SCORE_DIFF"),
				field("playoff_bracket_size", "Số slot playoff", "PLAYOFF", "INT", "NUMBER",
						null, 4, 32, "Kích thước nhánh playoff sau vòng bảng"),
				field("playoff_bye_top_seeds", "BYE cho hạt giống playoff", "PLAYOFF", "INT", "NUMBER",
						null, 0, 8, "Số hạt giống được BYE vòng playoff đầu"),
				field("pe_survivors_per_stage", "Số người đi tiếp mỗi giai đoạn", "PROGRESSIVE", "STRING", "TEXT",
						null, null, null,
						"Danh sách số người còn lại SAU mỗi giai đoạn vòng tròn, cách nhau bởi dấu phẩy (vd 10,6,4). "
								+ "Phần tử cuối là số người vào Playoff. Mọi phần tử phải là số chẵn, giảm dần."),
				field("final_playoff_size", "Số người vào Playoff", "PROGRESSIVE", "INT", "NUMBER",
						null, 4, 8, "Số cơ thủ xếp hạng cao nhất vào vòng Playoff loại trực tiếp (4 hoặc 8).")
		);
	}

	public static List<GameTypeDefinition> gameTypes() {
		return List.of(
				gameType("9_BALL", "9-Ball (Bida lỗ 9 bi)", "Race-to, alternate break.", 7, "[\"POOL\"]"),
				gameType("8_BALL", "8-Ball (Bida lỗ 8 bi)", "Race-to phổ biến quán pool.", 5, "[\"POOL\"]"),
				gameType("10_BALL", "10-Ball (Bida lỗ 10 bi)", "Race-to, luật WPA.", 7, "[\"POOL\"]")
		);
	}

	public static List<TournamentFormatDefinition> tournamentFormats() {
		return List.of(
				format("SINGLE_ELIMINATION",
						"Loại trực tiếp (1 lần thua)",
						"Thua 1 trận là bị loại. Race-to theo vòng.",
						"pool_single_elimination_handler"),
				format("DOUBLE_ELIMINATION",
						"Loại kép (2 lần thua)",
						"Nhánh thắng + nhánh thua, chung kết lớn.",
						"pool_double_elimination_handler"),
				format("GROUP_PLAYOFF",
						"Vòng bảng + Playoff",
						"Chia bảng round-robin, top bảng vào knockout.",
						"pool_group_playoff_handler"),
				format("PROGRESSIVE_ROUND_ROBIN",
						"Vòng tròn loại dần + Playoff",
						"Nhiều giai đoạn vòng tròn loại dần, nhóm còn lại vào Playoff loại trực tiếp.",
						"pool_progressive_round_robin_handler")
		);
	}

	public static List<FormatConfigFieldSeed> formatConfigFields() {
		return List.of(
				// SINGLE_ELIMINATION (7)
				configField("SINGLE_ELIMINATION", "bracket_size", "16", true),
				configField("SINGLE_ELIMINATION", "allow_bye", "true", true),
				configField("SINGLE_ELIMINATION", "seeding_enabled", "true", true),
				configField("SINGLE_ELIMINATION", "third_place_match", "true", true),
				configField("SINGLE_ELIMINATION", "break_rule", "ALTERNATE_BREAK", true),
				configField("SINGLE_ELIMINATION", "lag_for_break", "true", true),
				configField("SINGLE_ELIMINATION", "scoring_unit", "GAME", false),

				// DOUBLE_ELIMINATION (7)
				configField("DOUBLE_ELIMINATION", "bracket_size", "16", true),
				configField("DOUBLE_ELIMINATION", "allow_bye", "true", true),
				configField("DOUBLE_ELIMINATION", "seeding_enabled", "true", true),
				configField("DOUBLE_ELIMINATION", "grand_final_bracket_reset", "false", true),
				configField("DOUBLE_ELIMINATION", "break_rule", "ALTERNATE_BREAK", true),
				configField("DOUBLE_ELIMINATION", "lag_for_break", "true", true),
				configField("DOUBLE_ELIMINATION", "scoring_unit", "GAME", false),

				// GROUP_PLAYOFF (12)
				configField("GROUP_PLAYOFF", "break_rule", "ALTERNATE_BREAK", true),
				configField("GROUP_PLAYOFF", "lag_for_break", "true", true),
				configField("GROUP_PLAYOFF", "scoring_unit", "GAME", false),
				configField("GROUP_PLAYOFF", "group_count", "4", true),
				configField("GROUP_PLAYOFF", "players_per_group", "4", true),
				configField("GROUP_PLAYOFF", "advance_per_group", "2", true),
				configField("GROUP_PLAYOFF", "group_assignment", "RANDOM", true),
				configField("GROUP_PLAYOFF", "group_points_win", "1", true),
				configField("GROUP_PLAYOFF", "group_points_loss", "0", true),
				configField("GROUP_PLAYOFF", "group_tiebreaker_order", "HEAD_TO_HEAD,SCORE_DIFF", true),
				configField("GROUP_PLAYOFF", "playoff_bracket_size", "8", true),
				configField("GROUP_PLAYOFF", "playoff_bye_top_seeds", "0", true),

				// PROGRESSIVE_ROUND_ROBIN (6)
				configField("PROGRESSIVE_ROUND_ROBIN", "pe_survivors_per_stage", "10,6,4", true),
				configField("PROGRESSIVE_ROUND_ROBIN", "final_playoff_size", "4", true),
				configField("PROGRESSIVE_ROUND_ROBIN", "group_tiebreaker_order", "POINTS,RACK_DIFF,RACKS_WON,HEAD_TO_HEAD", true),
				configField("PROGRESSIVE_ROUND_ROBIN", "break_rule", "ALTERNATE_BREAK", true),
				configField("PROGRESSIVE_ROUND_ROBIN", "lag_for_break", "true", true),
				configField("PROGRESSIVE_ROUND_ROBIN", "scoring_unit", "GAME", false)
		);
	}

	public static List<FormatRaceToRuleSeed> formatRaceToRules() {
		return List.of(
				// SINGLE_ELIMINATION (5)
				raceTo("SINGLE_ELIMINATION", "round_1", "Vòng 1", "KNOCKOUT", 5),
				raceTo("SINGLE_ELIMINATION", "quarter_final", "Tứ kết", "KNOCKOUT", 7),
				raceTo("SINGLE_ELIMINATION", "semi_final", "Bán kết", "KNOCKOUT", 7),
				raceTo("SINGLE_ELIMINATION", "third_place", "Tranh hạng 3", "KNOCKOUT", 7),
				raceTo("SINGLE_ELIMINATION", "final", "Chung kết", "KNOCKOUT", 9),

				// DOUBLE_ELIMINATION (13) — winners_r1/qf/sf/final và losers_r1/r2/r3/final dùng
				// chung cho cả FULL_DE lẫn CUT_TO_SE (xem resolveWinnersRoundKey/resolveLosersRoundKey
				// trong BracketGenerationServiceImpl); se_* dành riêng cho bracket Last-X của CUT_TO_SE.
				raceTo("DOUBLE_ELIMINATION", "winners_r1", "NT — Vòng 1", "WINNERS", 5),
				raceTo("DOUBLE_ELIMINATION", "winners_qf", "NT — Tứ kết", "WINNERS", 7),
				raceTo("DOUBLE_ELIMINATION", "winners_sf", "NT — Bán kết", "WINNERS", 7),
				raceTo("DOUBLE_ELIMINATION", "winners_final", "NT — Chung kết nhánh", "WINNERS", 9),
				raceTo("DOUBLE_ELIMINATION", "losers_r1", "NTh — Vòng 1", "LOSERS", 5),
				raceTo("DOUBLE_ELIMINATION", "losers_r2", "NTh — Vòng 2", "LOSERS", 7),
				raceTo("DOUBLE_ELIMINATION", "losers_r3", "NTh — Vòng 3", "LOSERS", 7),
				raceTo("DOUBLE_ELIMINATION", "losers_final", "NTh — Chung kết nhánh", "LOSERS", 7),
				raceTo("DOUBLE_ELIMINATION", "grand_final", "Chung kết lớn", "GRAND_FINAL", 9),
				raceTo("DOUBLE_ELIMINATION", "se_round_1", "Last X — Vòng đầu", "FINAL_BRACKET", 5),
				raceTo("DOUBLE_ELIMINATION", "se_quarter_final", "Last X — Tứ kết", "FINAL_BRACKET", 7),
				raceTo("DOUBLE_ELIMINATION", "se_semi_final", "Last X — Bán kết", "FINAL_BRACKET", 7),
				raceTo("DOUBLE_ELIMINATION", "se_final", "Last X — Chung kết", "FINAL_BRACKET", 9),

				// GROUP_PLAYOFF (6)
				raceTo("GROUP_PLAYOFF", "group_default", "Vòng bảng", "GROUP", 5),
				raceTo("GROUP_PLAYOFF", "playoff_r1", "Playoff — Vòng 1", "PLAYOFF", 7),
				raceTo("GROUP_PLAYOFF", "playoff_qf", "Playoff — Tứ kết", "PLAYOFF", 7),
				raceTo("GROUP_PLAYOFF", "playoff_sf", "Playoff — Bán kết", "PLAYOFF", 7),
				raceTo("GROUP_PLAYOFF", "playoff_final", "Playoff — Chung kết", "KNOCKOUT", 9),
				raceTo("GROUP_PLAYOFF", "third_place", "Tranh hạng 3", "KNOCKOUT", 7),

				// PROGRESSIVE_ROUND_ROBIN (2)
				raceTo("PROGRESSIVE_ROUND_ROBIN", "league_stage", "Vòng tròn loại dần", "PROGRESSIVE_ROUND", 5),
				raceTo("PROGRESSIVE_ROUND_ROBIN", "playoff", "Playoff", "PROGRESSIVE_PLAYOFF", 7)
		);
	}

	public record FormatConfigFieldSeed(
			String formatCode,
			String fieldKey,
			String defaultValue,
			boolean visibleToOwner
	) {}

	public record FormatRaceToRuleSeed(
			String formatCode,
			String roundKey,
			String label,
			String bracketPhase,
			int raceTo
	) {}

	private static ConfigFieldDefinition field(
			String key, String label, String scope, String dataType, String uiComponent,
			String enumOptions, Integer min, Integer max, String description) {
		return ConfigFieldDefinition.builder()
				.fieldKey(key)
				.label(label)
				.description(description)
				.dataType(dataType)
				.fieldScope(scope)
				.enumOptions(enumOptions)
				.uiComponent(uiComponent)
				.minValue(min)
				.maxValue(max)
				.isActive(true)
				.build();
	}

	private static GameTypeDefinition gameType(
			String code, String name, String description, int defaultRaceTo, String tableTypes) {
		return GameTypeDefinition.builder()
				.code(code)
				.name(name)
				.description(description)
				.defaultRaceTo(defaultRaceTo)
				.compatibleTableTypes(tableTypes)
				.isActive(true)
				.build();
	}

	private static TournamentFormatDefinition format(
			String code, String name, String description, String handlerKey) {
		return TournamentFormatDefinition.builder()
				.code(code)
				.name(name)
				.description(description)
				.handlerKey(handlerKey)
				.schemaVersion("1.0")
				.isActive(true)
				.build();
	}

	private static FormatConfigFieldSeed configField(
			String formatCode, String fieldKey, String defaultValue, boolean visibleToOwner) {
		return new FormatConfigFieldSeed(formatCode, fieldKey, defaultValue, visibleToOwner);
	}

	private static FormatRaceToRuleSeed raceTo(
			String formatCode, String roundKey, String label, String bracketPhase, int raceTo) {
		return new FormatRaceToRuleSeed(formatCode, roundKey, label, bracketPhase, raceTo);
	}
}
