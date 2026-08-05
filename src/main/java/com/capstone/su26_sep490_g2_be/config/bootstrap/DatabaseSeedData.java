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
				field("break_rule", "Luật giao bóng (break)", "COMMON", "ENUM", "SELECT",
						"[\"ALTERNATE_BREAK\",\"WINNER_BREAK\",\"LOSER_BREAK\"]", null, null,
						"Quy định ai được phá bi mở ván sau mỗi ván đấu: luân phiên, người thắng ván trước, "
								+ "hoặc người thua ván trước."),
				field("lag_for_break", "Đấu lag giành quyền giao bóng", "COMMON", "BOOLEAN", "CHECKBOX",
						null, null, null,
						"Hai cơ thủ cùng đẩy bi chạm băng cuối bàn để giành quyền phá bi ván đầu tiên, "
								+ "thay vì bốc thăm."),
				field("scoring_unit", "Đơn vị tính điểm", "COMMON", "ENUM", "SELECT",
						"[\"GAME\",\"FRAME\"]", null, null,
						"Đơn vị dùng để tính thắng thua một trận: ván (game) hoặc hiệp (frame)."),
				field("bracket_size", "Số người tối đa", "KNOCKOUT", "INT", "NUMBER",
						null, 8, 64, "Đồng bộ hai chiều với số người tối đa của giải. "
								+ "Nhánh đấu thật luôn tự tính theo số người dự thi thực tế."),
				field("third_place_match", "Trận tranh hạng 3", "KNOCKOUT", "BOOLEAN", "CHECKBOX",
						null, null, null,
						"Tổ chức thêm trận giữa hai cơ thủ thua bán kết để xác định hạng 3."),
				field("pe_survivors_per_stage", "Số người đi tiếp mỗi giai đoạn", "PROGRESSIVE", "STRING", "TEXT",
						null, null, null,
						"Danh sách số người còn lại SAU mỗi giai đoạn vòng tròn, cách nhau bởi dấu phẩy (vd 10,6,4). "
								+ "Phần tử cuối là số người vào vòng chung kết loại trực tiếp. "
								+ "Mọi phần tử phải là số chẵn, giảm dần."),
				field("final_playoff_size", "Số người vào vòng chung kết", "PROGRESSIVE", "INT", "NUMBER",
						null, 4, 8,
						"Số cơ thủ xếp hạng cao nhất được vào vòng chung kết loại trực tiếp (4 hoặc 8)."),
				field("de_mode", "Cách kết thúc giải loại kép", "KNOCKOUT", "ENUM", "RADIO_GROUP",
						"[\"FULL_DE\",\"CUT_TO_SE\"]", null, null,
						"Đánh loại kép tới tận ngôi vô địch, hoặc đánh loại kép tới một mốc rồi "
								+ "gộp hai nhánh lại đánh loại trực tiếp một lần thua."),
				field("se_phase_size", "Số người vào vòng loại trực tiếp", "KNOCKOUT", "INT", "NUMBER",
						null, 4, 256,
						"Chỉ dùng khi chọn cách gộp hai nhánh: tổng số cơ thủ còn sống ở cả nhánh thắng và "
								+ "nhánh thua được chuyển sang đánh loại trực tiếp. Phải là lũy thừa của 2.")
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
				format("PROGRESSIVE_ROUND_ROBIN",
						"Vòng tròn loại dần + Playoff",
						"Nhiều giai đoạn vòng tròn loại dần, nhóm còn lại vào Playoff loại trực tiếp.",
						"pool_progressive_round_robin_handler")
		);
	}

	public static List<FormatConfigFieldSeed> formatConfigFields() {
		return List.of(
				// SINGLE_ELIMINATION (5) — bracket_size chỉ có tác dụng thật ở thể thức này
				configField("SINGLE_ELIMINATION", "bracket_size", "16", true),
				configField("SINGLE_ELIMINATION", "third_place_match", "true", true),
				configField("SINGLE_ELIMINATION", "break_rule", "ALTERNATE_BREAK", true),
				configField("SINGLE_ELIMINATION", "lag_for_break", "true", true),
				configField("SINGLE_ELIMINATION", "scoring_unit", "GAME", false),

				// DOUBLE_ELIMINATION (3) — de_mode / se_phase_size gắn riêng ở DataInitializer
				configField("DOUBLE_ELIMINATION", "break_rule", "ALTERNATE_BREAK", true),
				configField("DOUBLE_ELIMINATION", "lag_for_break", "true", true),
				configField("DOUBLE_ELIMINATION", "scoring_unit", "GAME", false),

				// PROGRESSIVE_ROUND_ROBIN (5)
				configField("PROGRESSIVE_ROUND_ROBIN", "pe_survivors_per_stage", "10,6,4", true),
				configField("PROGRESSIVE_ROUND_ROBIN", "final_playoff_size", "4", true),
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
