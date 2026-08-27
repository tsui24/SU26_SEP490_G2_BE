package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.config.bootstrap.DatabaseSeedData;
import com.capstone.su26_sep490_g2_be.config.bootstrap.SeedImages;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.BranchStatus;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.repository.*;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import com.capstone.su26_sep490_g2_be.util.JsonParseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

	/**
	 * Field đã bị gỡ khỏi catalog — {@link #cleanupRemovedConfigFields()} tự xoá khỏi
	 * {@code config_field_definitions}, {@code format_config_fields} và
	 * {@code tournament_config_values} để DB cũ không còn dữ liệu mồ côi.
	 *
	 * <p>Nhóm thứ hai thuộc thể thức GROUP_PLAYOFF (chia vòng bảng) — bản thân field đã gỡ ở đây
	 * từ trước, nhưng {@code tournament_format_definitions.GROUP_PLAYOFF} thì không; xem
	 * {@link #REMOVED_FORMAT_CODES} / {@link #cleanupRemovedFormats()} cho phần gỡ hẳn thể thức.
	 *
	 * <p>Nhóm thứ ba là các field <b>không nơi nào đọc</b> — Owner chỉnh được trên wizard nhưng
	 * giải chạy ra khác hẳn, gây hiểu nhầm nghiêm trọng hơn là không có field:
	 * <ul>
	 *   <li>{@code allow_bye} — BYE luôn được gán tự động khi sĩ số không phải lũy thừa 2,
	 *       tắt cờ này không ngăn được gì.</li>
	 *   <li>{@code seeding_enabled} — trùng chức năng với {@code seedingMethod = RANDOM},
	 *       hai nút cùng điều khiển một thứ.</li>
	 *   <li>{@code grand_final_bracket_reset} — bộ sinh nhánh chỉ tạo đúng một trận
	 *       GRAND_FINAL, không hề có nhánh reset.</li>
	 *   <li>{@code group_tiebreaker_order} — thứ tự tie-break hard-code trong
	 *       {@code computeStageStandings()}; tệ hơn, giá trị mặc định cũ còn ghi sai thứ tự thật.</li>
	 * </ul>
	 *
	 * <p>Các field chỉ mang tính <b>thông tin thi đấu</b> ({@code break_rule},
	 * {@code lag_for_break}, {@code scoring_unit}) thì <b>giữ lại</b>: code không đọc nhưng trọng
	 * tài và cơ thủ áp dụng bằng tay, nên chúng vẫn có giá trị khi hiển thị.
	 */
	private static final List<String> REMOVED_CONFIG_FIELD_KEYS =
			List.of("is_show_tournament", "is_public_ratio", "is_register",
					"group_count", "players_per_group", "advance_per_group", "group_assignment",
					"group_points_win", "group_points_loss",
					"playoff_bracket_size", "playoff_bye_top_seeds", "playoff_size",
					"allow_bye", "seeding_enabled", "grand_final_bracket_reset",
					"group_tiebreaker_order");

	/**
	 * Field bị gỡ khỏi <b>một thể thức cụ thể</b> nhưng vẫn sống ở thể thức khác — không thể cho
	 * vào {@link #REMOVED_CONFIG_FIELD_KEYS} vì làm vậy sẽ xoá luôn ở nơi nó còn tác dụng.
	 *
	 * <p>{@code bracket_size} đồng bộ hai chiều với {@code maxParticipants} — nhưng
	 * {@code syncBracketSizeFromMaxParticipants} / {@code syncMaxParticipantsFromBracketSize} đều
	 * thoát ngay nếu format không phải SINGLE_ELIMINATION. Với DOUBLE_ELIMINATION thì Owner vẫn
	 * thấy ô nhập, vẫn lưu được, mà không có bất kỳ tác dụng nào.
	 */
	private static final List<Map.Entry<String, String>> REMOVED_FORMAT_SCOPED_FIELDS =
			List.of(
					Map.entry("DOUBLE_ELIMINATION", "bracket_size"),
					// "Đánh loại kép tới tận vô địch" không phải cách tổ chức giải thật — bỏ lựa
					// chọn, DOUBLE_ELIMINATION giờ luôn cắt về loại trực tiếp (generateCutToSEDE).
					// Giải cũ đã bốc thăm không bị ảnh hưởng: bracket đã sinh ra là dữ liệu độc lập,
					// không đọc lại config field này.
					Map.entry("DOUBLE_ELIMINATION", "de_mode"));

	/**
	 * Thể thức đã bị gỡ khỏi {@link DatabaseSeedData#tournamentFormats()} nhưng
	 * {@link #seedTournamentFormats()} chỉ INSERT-nếu-chưa-có, không bao giờ tự xoá — hàng cũ do
	 * bản {@code DataInitializer} trước đây (hoặc do Admin tự tạo tay qua wizard format) vẫn nằm
	 * lại trong DB, {@code isActive=true}, kèm đủ config field + race-to rule, nên Owner vẫn tạo
	 * được giải GROUP_PLAYOFF và bốc thăm ra một bracket Loại trực tiếp trá hình (bracket generator
	 * không nhận diện được format lạ nên rơi vào nhánh mặc định).
	 *
	 * <p>{@link #cleanupRemovedFormats()} gỡ nốt phần này: xoá hẳn hàng
	 * {@code tournament_format_definitions} + field/race-to rule đính kèm nếu chưa giải nào từng
	 * dùng; nếu đã có giải dùng rồi (dữ liệu test cũ) thì chỉ tắt {@code isActive} — không xoá, để
	 * không phá vỡ khoá ngoại {@code tournaments.format} lẫn màn xem lại của giải đó.
	 */
	private static final List<String> REMOVED_FORMAT_CODES = List.of("GROUP_PLAYOFF");

	private final RoleRepository roleRepository;
	private final UserRepository userRepository;
	private final ConfigFieldDefinitionRepository configFieldRepository;
	private final RegistrationFieldDefinitionRepository registrationFieldRepository;
	private final RegistrationFormTemplateRepository registrationFormTemplateRepository;
	private final RegistrationFormTemplateFieldRepository registrationFormTemplateFieldRepository;
	private final GameTypeDefinitionRepository gameTypeRepository;
	private final TournamentFormatDefinitionRepository formatRepository;
	private final FormatConfigFieldRepository formatConfigFieldRepository;
	private final FormatRaceToRuleRepository formatRaceToRuleRepository;
	private final TournamentConfigValueRepository tournamentConfigValueRepository;
	private final TournamentRepository tournamentRepository;
	private final BranchRepository branchRepository;
	private final BranchManagerRepository branchManagerRepository;
	private final PasswordEncoder passwordEncoder;
	private final MatchService matchService;

	@Override
	@Transactional
	public void run(String... args) {
		seedRoles();
		seedConfigFieldCatalog();
		seedGameTypes();
		seedTournamentFormats();
		seedFormatConfigFields();
		seedFormatRaceToRules();
		cleanupRemovedConfigFields();
		cleanupRemovedFormats();
		ensureFormatConfigFieldsForDE();

		seedAccounts();
		seedBranches();
		seedRegistrationFieldCatalog();
		seedRegistrationFormTemplates();
		matchService.reconcileDeadLoserSlots();

		log.info("DataInitializer completed — roles, catalog, accounts, branches, registration templates");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Catalog: Roles, Config Fields, Game Types, Formats
	// ══════════════════════════════════════════════════════════════════════

	private void seedRoles() {
		for (RoleCode rc : RoleCode.values()) {
			if (!roleRepository.existsByCode(rc.getCode())) {
				roleRepository.save(Role.builder()
						.code(rc.getCode())
						.name(rc.getDisplayName())
						.description("Vai trò hệ thống: " + rc.getDisplayName())
						.isActive(true)
						.build());
				log.info("Seeded role: {}", rc.getCode());
			}
		}
	}

	/**
	 * Seed catalog field. Với field đã tồn tại thì <b>vẫn đồng bộ lại nhãn/mô tả/schema hiển thị</b>
	 * (label, description, dataType, uiComponent, enumOptions) — nếu chỉ {@code existsById} rồi bỏ
	 * qua, mọi lần sửa trong {@code DatabaseSeedData} sẽ không bao giờ tới được DB đang chạy, và field
	 * cũ (sai/thiếu enumOptions, uiComponent lỗi thời...) nằm lại vĩnh viễn — ví dụ thực tế: field
	 * {@code de_mode} từng được tạo trước khi {@code enumOptions} được thêm vào code, khiến control
	 * RADIO_GROUP mãi không có lựa chọn để hiển thị dù code nguồn đã đúng từ lâu.
	 * Ràng buộc/giá trị điều chỉnh được (min/max) thì <b>không</b> ghi đè vì Admin có thể đã chỉnh
	 * cho phù hợp thực tế — chỉ phần định nghĩa "field này hiển thị bằng control gì, có lựa chọn gì"
	 * mới luôn đồng bộ theo code.
	 */
	private void seedConfigFieldCatalog() {
		int seeded = 0;
		int relabeled = 0;
		for (ConfigFieldDefinition field : DatabaseSeedData.configFieldCatalog()) {
			ConfigFieldDefinition existing = configFieldRepository.findById(field.getFieldKey()).orElse(null);
			if (existing == null) {
				configFieldRepository.save(field);
				seeded++;
				continue;
			}
			if (!Objects.equals(existing.getLabel(), field.getLabel())
					|| !Objects.equals(existing.getDescription(), field.getDescription())
					|| !Objects.equals(existing.getDataType(), field.getDataType())
					|| !Objects.equals(existing.getUiComponent(), field.getUiComponent())
					|| !Objects.equals(existing.getEnumOptions(), field.getEnumOptions())) {
				existing.setLabel(field.getLabel());
				existing.setDescription(field.getDescription());
				existing.setDataType(field.getDataType());
				existing.setUiComponent(field.getUiComponent());
				existing.setEnumOptions(field.getEnumOptions());
				configFieldRepository.save(existing);
				relabeled++;
			}
		}
		if (seeded > 0) log.info("Seeded config_field_definitions: {} rows", seeded);
		if (relabeled > 0) log.info("Relabeled config_field_definitions: {} rows", relabeled);
	}

	/**
	 * Sửa lại `sortOrder` cho hàng ĐÃ có sẵn ngoài việc chèn hàng mới — trước đây chỉ insert-if-missing
	 * nên môi trường đã seed từ trước (VD prod) giữ nguyên `sortOrder=0` cho mọi hàng mãi mãi dù
	 * sửa lại `DatabaseSeedData`, vì code chỉ chạy qua nhánh insert khi hàng chưa tồn tại. Đây là
	 * field duy nhất được đồng bộ lại — không đụng name/description vì Admin có thể đã tự sửa qua
	 * `AdminGameTypeController`, còn `sortOrder` thì chưa có màn nào cho sửa cả.
	 */
	private void seedGameTypes() {
		int seeded = 0;
		int resorted = 0;
		for (GameTypeDefinition gameType : DatabaseSeedData.gameTypes()) {
			GameTypeDefinition existing = gameTypeRepository.findById(gameType.getCode()).orElse(null);
			if (existing == null) {
				gameTypeRepository.save(gameType);
				seeded++;
			} else if (!gameType.getSortOrder().equals(existing.getSortOrder())) {
				existing.setSortOrder(gameType.getSortOrder());
				gameTypeRepository.save(existing);
				resorted++;
			}
		}
		if (seeded > 0) log.info("Seeded game_type_definitions: {} rows", seeded);
		if (resorted > 0) log.info("Re-sorted game_type_definitions: {} rows", resorted);
	}

	private void seedTournamentFormats() {
		int seeded = 0;
		for (TournamentFormatDefinition format : DatabaseSeedData.tournamentFormats()) {
			if (!formatRepository.existsById(format.getCode())) {
				formatRepository.save(format);
				seeded++;
			}
		}
		if (seeded > 0) log.info("Seeded tournament_format_definitions: {} rows", seeded);
	}

	private void seedFormatConfigFields() {
		int seeded = 0;
		for (DatabaseSeedData.FormatConfigFieldSeed seed : DatabaseSeedData.formatConfigFields()) {
			if (formatConfigFieldRepository.existsByFormatCodeAndFieldKey(seed.formatCode(), seed.fieldKey())) {
				continue;
			}
			formatConfigFieldRepository.save(FormatConfigField.builder()
					.formatCode(seed.formatCode())
					.fieldKey(seed.fieldKey())
					.defaultValue(seed.defaultValue())
					.isRequired(true)
					.isVisibleToOwner(seed.visibleToOwner())
					.build());
			seeded++;
		}
		if (seeded > 0) log.info("Seeded format_config_fields: {} rows", seeded);
	}

	private void seedFormatRaceToRules() {
		int seeded = 0;
		for (DatabaseSeedData.FormatRaceToRuleSeed seed : DatabaseSeedData.formatRaceToRules()) {
			if (formatRaceToRuleRepository.findByFormatCodeAndRoundKey(seed.formatCode(), seed.roundKey())
					.isPresent()) {
				continue;
			}
			formatRaceToRuleRepository.save(FormatRaceToRule.builder()
					.formatCode(seed.formatCode())
					.roundKey(seed.roundKey())
					.label(seed.label())
					.bracketPhase(seed.bracketPhase())
					.raceTo(seed.raceTo())
					.build());
			seeded++;
		}
		if (seeded > 0) log.info("Seeded format_race_to_rules: {} rows", seeded);
		relabelWinnersLosersRaceToRules();
	}

	/**
	 * Chỉ đổi nhãn "Tứ kết/Bán kết/Chung kết nhánh" cũ của NT/NTh (WINNERS/LOSERS) sang "Vòng N" —
	 * seedFormatRaceToRules() ở trên chỉ insert-nếu-chưa-có nên 4 dòng này đã tồn tại sẵn trên DB
	 * đã deploy, sửa lại DatabaseSeedData không tự áp dụng lại được. Chỉ đổi khi label HIỆN TẠI
	 * đúng bằng nhãn cũ (không đổi hàng loạt theo roundKey) để không đè mất label Admin đã tự tay
	 * sửa thành thứ khác qua màn "Sửa thể thức giải".
	 */
	private void relabelWinnersLosersRaceToRules() {
		Map<String, String> oldToNew = Map.of(
				"NT — Tứ kết", "NT — Vòng 2",
				"NT — Bán kết", "NT — Vòng 3",
				"NT — Chung kết nhánh", "NT — Vòng 4",
				"NTh — Chung kết nhánh", "NTh — Vòng 4");
		int relabeled = 0;
		for (FormatRaceToRule rule : formatRaceToRuleRepository.findByFormatCodeOrderByIdAsc("DOUBLE_ELIMINATION")) {
			String newLabel = oldToNew.get(rule.getLabel());
			if (newLabel != null) {
				rule.setLabel(newLabel);
				formatRaceToRuleRepository.save(rule);
				relabeled++;
			}
		}
		if (relabeled > 0) log.info("Relabeled format_race_to_rules (WINNERS/LOSERS, dropped tứ/bán/chung kết wording): {} rows", relabeled);
	}

	private void cleanupRemovedConfigFields() {
		tournamentConfigValueRepository.deleteByIdFieldKeyIn(REMOVED_CONFIG_FIELD_KEYS);
		formatConfigFieldRepository.deleteByFieldKeyIn(REMOVED_CONFIG_FIELD_KEYS);
		configFieldRepository.deleteByFieldKeyIn(REMOVED_CONFIG_FIELD_KEYS);

		// Chỉ gỡ ở format chỉ định — định nghĩa field trong catalog vẫn giữ cho format còn dùng.
		for (Map.Entry<String, String> scoped : REMOVED_FORMAT_SCOPED_FIELDS) {
			String formatCode = scoped.getKey();
			String fieldKey = scoped.getValue();
			tournamentConfigValueRepository.deleteByFieldKeyForFormat(fieldKey, formatCode);
			formatConfigFieldRepository.deleteByFormatCodeAndFieldKey(formatCode, fieldKey);
		}
	}

	/**
	 * Gỡ hẳn thể thức không còn trong {@link DatabaseSeedData#tournamentFormats()} (xem
	 * {@link #REMOVED_FORMAT_CODES}). {@code tournaments.format} có khoá ngoại tới
	 * {@code tournament_format_definitions.code} nên chỉ xoá hàng định nghĩa khi chắc chắn không
	 * còn giải nào tham chiếu tới — có giải dùng rồi thì chỉ tắt {@code isActive} (Owner không tạo
	 * mới được nữa, giải cũ vẫn xem lại được bình thường) và log rõ id để xử lý tay nếu cần.
	 */
	private void cleanupRemovedFormats() {
		for (String formatCode : REMOVED_FORMAT_CODES) {
			if (!formatRepository.existsById(formatCode)) {
				continue;
			}
			if (tournamentRepository.existsByFormat(formatCode)) {
				List<Long> stillUsedBy = tournamentRepository.findIdsByFormat(formatCode);
				formatRepository.findById(formatCode).ifPresent(f -> {
					f.setIsActive(false);
					formatRepository.save(f);
				});
				log.warn("Thể thức {} đã bị gỡ khỏi seed nhưng vẫn còn {} giải tham chiếu (id: {}) — "
								+ "đã tắt isActive để chặn tạo giải mới, KHÔNG xoá định nghĩa/field/race-to rule.",
						formatCode, stillUsedBy.size(), stillUsedBy);
				continue;
			}
			formatRaceToRuleRepository.deleteByFormatCode(formatCode);
			formatConfigFieldRepository.deleteByFormatCode(formatCode);
			formatRepository.deleteById(formatCode);
			log.info("Đã gỡ hẳn thể thức {} (không còn giải nào dùng): xoá format definition + "
					+ "config field + race-to rule đính kèm.", formatCode);
		}
	}

	/**
	 * se_phase_size bắt buộc Owner tự nhập (không có defaultValue để âm thầm dùng thay) — số này
	 * phải khớp với quy mô thật của giải (xem validateSePhaseSize ở OwnerTournamentServiceImpl),
	 * nên không có một con số mặc định nào đúng cho mọi giải.
	 */
	private void ensureFormatConfigFieldsForDE() {
		linkFormatConfigField("DOUBLE_ELIMINATION", "se_phase_size", "", true, 91);
	}

	private void linkFormatConfigField(String formatCode, String fieldKey,
			String defaultValue, boolean isRequired, int sortOrder) {
		FormatConfigField existing = formatConfigFieldRepository
				.findByFormatCodeAndFieldKey(formatCode, fieldKey).orElse(null);
		if (existing == null) {
			formatConfigFieldRepository.save(FormatConfigField.builder()
					.formatCode(formatCode)
					.fieldKey(fieldKey)
					.defaultValue(defaultValue)
					.isRequired(isRequired)
					.isVisibleToOwner(true)
					.sortOrder(sortOrder)
					.build());
			return;
		}
		if (!Objects.equals(existing.getDefaultValue(), defaultValue)
				|| !Objects.equals(existing.getIsRequired(), isRequired)) {
			existing.setDefaultValue(defaultValue);
			existing.setIsRequired(isRequired);
			formatConfigFieldRepository.save(existing);
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Accounts: Admin, Owner, 2 Manager (1/chi nhánh), 4 Staff (2/chi nhánh), 30 cơ thủ
	// ══════════════════════════════════════════════════════════════════════

	private void seedAccounts() {
		seedUser("admin@gmail.com", "admin1", RoleCode.ADMIN, "Nguyễn Bảo Toàn", "0900000001", null);
		seedUser("owner@gmail.com", "owner123", RoleCode.OWNER, "Nguyễn Thành Đạt", "0901000001", null);

		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);

		// 1 quản lý / chi nhánh — gán chi nhánh cụ thể ở seedBranches()
		seedUser("manager@gmail.com", "manager123", RoleCode.MANAGER, "Trần Quốc Bảo", "0902000001", owner);
		seedUser("manager2@gmail.com", "manager123", RoleCode.MANAGER, "Phạm Thị Ngọc Hà", "0902000002", owner);

		// 2 nhân viên / chi nhánh (lễ tân kiêm trọng tài)
		seedUser("staff1@gmail.com", "staff123", RoleCode.STAFF, "Trần Văn Trọng", "0903000001", owner);
		seedUser("staff2@gmail.com", "staff123", RoleCode.STAFF, "Lê Thị Mai", "0903000002", owner);
		seedUser("staff3@gmail.com", "staff123", RoleCode.STAFF, "Đinh Văn Sang", "0903000003", owner);
		seedUser("staff4@gmail.com", "staff123", RoleCode.STAFF, "Ngô Thị Bích Ngọc", "0903000004", owner);

		// {email, họ tên, sđt, hạng bi-a (BilliardRank)} — 30 cơ thủ đủ để làm participant/seed cho
		// mọi thể thức giải đấu (SINGLE/DOUBLE_ELIMINATION 8-32 người, PROGRESSIVE_ROUND_ROBIN...).
		String[][] players = {
				{"player1@gmail.com", "Nguyễn Văn Hùng", "0912000001", "A"},
				{"player2@gmail.com", "Trần Minh Tuấn", "0912000002", "A"},
				{"player3@gmail.com", "Lê Hoàng Nam", "0912000003", "B"},
				{"player4@gmail.com", "Phạm Đức Anh", "0912000004", "B"},
				{"player5@gmail.com", "Hoàng Quốc Việt", "0912000005", "C"},
				{"player6@gmail.com", "Vũ Thanh Bình", "0912000006", "C"},
				{"player7@gmail.com", "Phan Trọng Khôi", "0912000007", "D"},
				{"player8@gmail.com", "Trương Xuân Long", "0912000008", "D"},
				{"player9@gmail.com", "Bùi Hữu Phúc", "0912000009", "A"},
				{"player10@gmail.com", "Đặng Đình Khoa", "0912000010", "B"},
				{"player11@gmail.com", "Đỗ Công Danh", "0912000011", "B"},
				{"player12@gmail.com", "Hồ Quang Minh", "0912000012", "C"},
				{"player13@gmail.com", "Ngô Thành Trung", "0912000013", "C"},
				{"player14@gmail.com", "Dương Bảo Khánh", "0912000014", "D"},
				{"player15@gmail.com", "Nguyễn Nhật Huy", "0912000015", "D"},
				{"player16@gmail.com", "Trần Tiến Đạt", "0912000016", "B"},
				{"player17@gmail.com", "Lý Gia Bảo", "0912000017", "B"},
				{"player18@gmail.com", "Huỳnh Tấn Phát", "0912000018", "C"},
				{"player19@gmail.com", "Vương Đình Phong", "0912000019", "A"},
				{"player20@gmail.com", "Đoàn Minh Quân", "0912000020", "C"},
				{"player21@gmail.com", "Tô Ngọc Sơn", "0912000021", "D"},
				{"player22@gmail.com", "Lâm Chí Cường", "0912000022", "B"},
				{"player23@gmail.com", "Mai Xuân Kiên", "0912000023", "C"},
				{"player24@gmail.com", "Chu Bảo Long", "0912000024", "A"},
				{"player25@gmail.com", "Phùng Đăng Khoa", "0912000025", "D"},
				{"player26@gmail.com", "Kiều Anh Dũng", "0912000026", "B"},
				{"player27@gmail.com", "Thái Gia Huy", "0912000027", "C"},
				{"player28@gmail.com", "Đậu Quang Vinh", "0912000028", "CHAMPION"},
				{"player29@gmail.com", "Nguyễn Thị Thu Hằng", "0912000029", "B"},
				{"player30@gmail.com", "Phạm Thị Kim Ngân", "0912000030", "C"},
		};
		for (String[] p : players) {
			seedPlayerUser(p[0], "player123", p[1], p[2], p[3]);
		}
	}

	private void seedUser(String email, String rawPassword, RoleCode roleCode, String fullName, String phone, User owner) {
		if (userRepository.existsByEmail(email)) return;
		Role role = roleRepository.findByCode(roleCode.getCode())
				.orElseThrow(() -> new IllegalStateException("Role not found: " + roleCode));
		User user = User.builder()
				.email(email)
				.phone(phone)
				.passwordHash(passwordEncoder.encode(rawPassword))
				.role(role)
				.status(UserStatus.ACTIVE)
				.owner(owner)
				.build();
		UserProfile profile = UserProfile.builder()
				.user(user)
				.fullName(fullName)
				.displayName(fullName)
				.build();
		user.setProfile(profile);
		userRepository.save(user);
		log.info("Seeded account: {} / {} ({})", email, rawPassword, roleCode.getCode());
	}

	private void seedPlayerUser(String email, String rawPassword, String fullName, String phone, String billiardRank) {
		if (userRepository.existsByEmail(email)) return;
		Role role = roleRepository.findByCode(RoleCode.PLAYER.getCode())
				.orElseThrow(() -> new IllegalStateException("PLAYER role not found"));
		User player = User.builder()
				.email(email)
				.phone(phone)
				.passwordHash(passwordEncoder.encode(rawPassword))
				.role(role)
				.status(UserStatus.ACTIVE)
				.build();
		UserProfile profile = UserProfile.builder()
				.user(player)
				.fullName(fullName)
				.displayName(fullName)
				.billiardRank(billiardRank)
				.build();
		player.setProfile(profile);
		userRepository.save(player);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Branches: 2 chi nhánh đầy đủ thông tin, mỗi chi nhánh 1 manager + 2 staff riêng
	// ══════════════════════════════════════════════════════════════════════

	private void seedBranches() {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) return;

		// CN Thủ Đức (TP.HCM) đổi thành CN Đống Đa (Hà Nội) — cả 2 chi nhánh giờ đều ở Hà Nội. Đổi
		// tên trực tiếp thì seedBranch() bên dưới (khớp theo NAME) sẽ không nhận ra chi nhánh cũ, tạo
		// thêm 1 hàng mới thay vì sửa hàng đã deploy — migrateBranchIfRenamed() sửa tại chỗ theo tên
		// cũ trước, để lần chạy tiếp theo mới khớp đúng tên mới.
		migrateBranchIfRenamed(owner,
				"Golden Break Billiards — CN Thủ Đức",
				"Golden Break Billiards — CN Đống Đa",
				"Số 25 Phố Tây Sơn, Phường Quang Trung, Quận Đống Đa, TP. Hà Nội",
				"024 3851 2299",
				"Chi nhánh trung tâm của hệ thống Golden Break Billiards tại Hà Nội — 12 bàn Pool "
						+ "thi đấu chuẩn Rasson/Diamond, hệ thống đèn LED chuyên dụng cho truyền hình trực "
						+ "tiếp, khu khán đài 80 chỗ và màn hình lớn phục vụ các giải đấu quy mô CLB/liên CLB. "
						+ "Mở cửa 08:00 – 24:00 tất cả các ngày trong tuần, có bãi giữ xe riêng và quầy "
						+ "phục vụ đồ uống.");

		Branch branch1 = seedBranch(owner,
				"Golden Break Billiards — CN Đống Đa",
				"Số 25 Phố Tây Sơn, Phường Quang Trung, Quận Đống Đa, TP. Hà Nội",
				"024 3851 2299",
				"Chi nhánh trung tâm của hệ thống Golden Break Billiards tại Hà Nội — 12 bàn Pool "
						+ "thi đấu chuẩn Rasson/Diamond, hệ thống đèn LED chuyên dụng cho truyền hình trực "
						+ "tiếp, khu khán đài 80 chỗ và màn hình lớn phục vụ các giải đấu quy mô CLB/liên CLB. "
						+ "Mở cửa 08:00 – 24:00 tất cả các ngày trong tuần, có bãi giữ xe riêng và quầy "
						+ "phục vụ đồ uống.");

		Branch branch2 = seedBranch(owner,
				"Golden Break Billiards — CN Cầu Giấy",
				"Số 15 Phố Trần Thái Tông, Phường Dịch Vọng Hậu, Quận Cầu Giấy, TP. Hà Nội",
				"024 3795 6677",
				"Chi nhánh Hà Nội của hệ thống Golden Break Billiards — 10 bàn Pool thi đấu chuẩn WPA, "
						+ "phòng VIP cách âm cho khách đoàn, quầy check-in đăng ký giải và màn hình trực "
						+ "tiếp tỉ số thi đấu. Mở cửa 09:00 – 23:30 tất cả các ngày trong tuần.");

		User manager1 = userRepository.findByEmail("manager@gmail.com").orElse(null);
		if (manager1 != null) assignManagerToBranch(branch1, manager1, owner);

		User manager2 = userRepository.findByEmail("manager2@gmail.com").orElse(null);
		if (manager2 != null) assignManagerToBranch(branch2, manager2, owner);

		assignStaffToBranch("staff1@gmail.com", branch1);
		assignStaffToBranch("staff2@gmail.com", branch2);
		assignStaffToBranch("staff3@gmail.com", branch1);
		assignStaffToBranch("staff4@gmail.com", branch2);
	}

	private void assignStaffToBranch(String email, Branch branch) {
		User staff = userRepository.findByEmail(email).orElse(null);
		if (staff != null && staff.getBranch() == null) {
			staff.setBranch(branch);
			userRepository.save(staff);
		}
	}

	/**
	 * Đổi tên/địa chỉ 1 chi nhánh ĐÃ deploy — {@code seedBranch()} bên dưới chỉ insert-nếu-chưa-có
	 * (khớp theo NAME), nên chỉ sửa tên trong code không tự áp dụng lại được cho DB đã seed từ
	 * trước; không có bước này, DB cũ giữ nguyên chi nhánh tên cũ VÀ có thêm 1 chi nhánh tên mới —
	 * trùng lặp thay vì đổi tại chỗ. Không làm gì nếu chi nhánh tên cũ không còn tồn tại (đã đổi ở
	 * lần chạy trước, hoặc DB mới tinh chưa từng có).
	 */
	private void migrateBranchIfRenamed(User owner, String oldName, String newName,
			String newAddress, String newPhone, String newDescription) {
		branchRepository.findByOwnerId(owner.getId()).stream()
				.filter(b -> oldName.equals(b.getName()))
				.findFirst()
				.ifPresent(b -> {
					b.setName(newName);
					b.setAddress(newAddress);
					b.setPhone(newPhone);
					b.setDescription(newDescription);
					b.setImageKeys(JsonParseUtil.toJson(List.of(
							SeedImages.branchImageKey(newName, 0),
							SeedImages.branchImageKey(newName, 1))));
					branchRepository.save(b);
					log.info("Migrated branch '{}' -> '{}'", oldName, newName);
				});
	}

	private Branch seedBranch(User owner, String name, String address, String phone, String description) {
		List<Branch> existing = branchRepository.findByOwnerId(owner.getId());
		for (Branch b : existing) {
			if (name.equals(b.getName())) return b;
		}
		List<String> imageKeys = List.of(
				SeedImages.branchImageKey(name, 0),
				SeedImages.branchImageKey(name, 1));
		Branch branch = branchRepository.save(Branch.builder()
				.name(name)
				.address(address)
				.phone(phone)
				.description(description)
				.status(BranchStatus.ACTIVE)
				.owner(owner)
				.imageKeys(JsonParseUtil.toJson(imageKeys))
				.build());
		log.info("Seeded branch: {}", name);
		return branch;
	}

	private void assignManagerToBranch(Branch branch, User manager, User owner) {
		if (branch == null) return;
		if (branchManagerRepository.existsByBranchIdAndManagerId(branch.getId(), manager.getId())) return;
		branchManagerRepository.save(BranchManager.builder()
				.branch(branch)
				.manager(manager)
				.assignedBy(owner)
				.build());
		log.info("Assigned manager {} to branch {}", manager.getEmail(), branch.getName());
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Registration Form Templates
	// ══════════════════════════════════════════════════════════════════════

	private void seedRegistrationFieldCatalog() {
		seedRegistrationField("player_full_name", "Họ và tên", "Nhập họ và tên đầy đủ", "STRING", "TEXT");
		seedRegistrationField("player_phone", "Số điện thoại", "Nhập số điện thoại liên hệ", "PHONE", "PHONE_INPUT");
		seedRegistrationField("player2_full_name", "Họ và tên người chơi 2", "Nhập họ và tên đồng đội", "STRING", "TEXT");
		seedRegistrationField("player2_phone", "Số điện thoại người chơi 2", "Nhập số điện thoại đồng đội", "PHONE", "PHONE_INPUT");
	}

	private void seedRegistrationField(String fieldKey, String label, String description,
			String dataType, String uiComponent) {
		if (registrationFieldRepository.existsById(fieldKey)) return;
		registrationFieldRepository.save(RegistrationFieldDefinition.builder()
				.fieldKey(fieldKey)
				.label(label)
				.description(description)
				.dataType(dataType)
				.uiComponent(uiComponent)
				.isActive(true)
				.build());
	}

	private void seedRegistrationFormTemplates() {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) return;

		RegistrationFormTemplate basic = registrationFormTemplateRepository.findByCode("PLAYER_REG_BASIC")
				.orElseGet(() -> registrationFormTemplateRepository.save(RegistrationFormTemplate.builder()
						.code("PLAYER_REG_BASIC")
						.name("Form đăng ký cơ bản")
						.description("Form đăng ký giải đấu đơn")
						.isActive(true)
						.sortOrder(0)
						.createdBy(owner)
						.build()));
		seedTemplateField(basic, "player_full_name", "Họ và tên", "Nhập họ tên cơ thủ", "Nguyễn Văn A", 1);
		seedTemplateField(basic, "player_phone", "Số điện thoại", "Số điện thoại liên hệ", "0900000000", 2);

		RegistrationFormTemplate doubles = registrationFormTemplateRepository.findByCode("PLAYER_REG_DOUBLES")
				.orElseGet(() -> registrationFormTemplateRepository.save(RegistrationFormTemplate.builder()
						.code("PLAYER_REG_DOUBLES")
						.name("Form đăng ký đôi")
						.description("Form đăng ký giải đôi — 1 người điền thông tin cho cả 2")
						.isActive(true)
						.sortOrder(1)
						.createdBy(owner)
						.build()));
		seedTemplateField(doubles, "player_full_name", "Họ tên người chơi 1", "Họ tên của bạn (đội trưởng)", "Nguyễn Văn A", 1);
		seedTemplateField(doubles, "player_phone", "SĐT người chơi 1", "Số điện thoại của bạn", "0900000000", 2);
		seedTemplateField(doubles, "player2_full_name", "Họ tên người chơi 2", "Họ tên đồng đội", "Trần Văn B", 3);
		seedTemplateField(doubles, "player2_phone", "SĐT người chơi 2", "Số điện thoại đồng đội", "0900000001", 4);
	}

	private void seedTemplateField(RegistrationFormTemplate template, String fieldKey, String labelOverride,
			String descriptionOverride, String placeholder, int sortOrder) {
		if (registrationFormTemplateFieldRepository.findByTemplateIdAndFieldKey(template.getId(), fieldKey)
				.isPresent()) {
			return;
		}
		registrationFormTemplateFieldRepository.save(RegistrationFormTemplateField.builder()
				.templateId(template.getId())
				.fieldKey(fieldKey)
				.labelOverride(labelOverride)
				.descriptionOverride(descriptionOverride)
				.placeholder(placeholder)
				.isRequired(true)
				.sortOrder(sortOrder)
				.build());
	}
}
