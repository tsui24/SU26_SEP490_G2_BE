package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Tạo data mẫu đầy đủ cho DOUBLE_ELIMINATION và GROUP_PLAYOFF.
 *
 * Dependency chain đầy đủ để generate được bracket:
 *   Tournament (REGISTRATION_CLOSED)
 *     ↓
 *   TournamentConfig (seedingMethod)
 *     ↓
 *   TournamentRaceToRule[] (race-to per round key)
 *     ↓
 *   TournamentConfigValue[] (playoff_size, v.v.)
 *     ↓
 *   Participant[] (status=ACTIVE)
 *
 * Chạy sau DataInitializer (@Order(1)) → @Order(2).
 * Idempotent — kiểm tra tồn tại trước khi insert.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class BracketSeedInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentConfigRepository tournamentConfigRepository;
    private final TournamentRaceToRuleRepository raceToRuleRepository;
    private final TournamentConfigValueRepository configValueRepository;
    private final ConfigFieldDefinitionRepository configFieldRepository;
    private final FormatConfigFieldRepository formatConfigFieldRepository;
    private final ParticipantRepository participantRepository;

    /* ═══════════════════════════════════════════════════════════
     *  Entry point
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public void run(String... args) {
        ensurePlayoffSizeFieldExists();
        ensureDEConfigFieldsExist();        // tạo ConfigFieldDefinition cho de_mode + se_phase_size
        ensureFormatConfigFieldsForDE();    // link chúng vào format DOUBLE_ELIMINATION để hiện trên UI

        User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
        if (owner == null) {
            log.warn("BracketSeedInitializer: owner@gmail.com chưa tồn tại — bỏ qua.");
            return;
        }

        // ── DOUBLE_ELIMINATION — FULL_DE (standard) ─────────────────────
        seedDE8(owner);   // 8 người — bracket 8  (không có BYE, trường hợp sạch)
        seedDE10(owner);  // 10 người — bracket 16 (6 BYE + 2 trận thật ở R1)

        // ── DOUBLE_ELIMINATION — CUT_TO_SE (Matchroom style) ────────────
        seedDECutToSE16(owner);   // 16 người → Last 4 SE  (cutoffRound=3, tỉ lệ 1/4)
        seedDECutToSE32(owner);   // 32 người → Last 8 SE  (cutoffRound=3)

        // ── GROUP_PLAYOFF (Vòng tròn + Playoff) ─────────────────────────
        seedGroupPlayoff8(owner);   // 8 người — 7 vòng RR, top 4 vào playoff
        seedGroupPlayoff12(owner);  // 12 người — 11 vòng RR, top 4 vào playoff

        log.info("BracketSeedInitializer hoàn thành — 6 giải test bracket đã sẵn sàng");
    }

    /* ═══════════════════════════════════════════════════════════
     *  DOUBLE ELIMINATION — 8 cơ thủ
     *
     *  bracketSize=8, wTotalRounds=3, lTotalRounds=4
     *
     *  Winners: W-R1(4 trận), W-R2(2 trận), W-Final(1 trận)
     *  Losers:  L-R1(2 trận), L-R2(2 trận), L-R3(1 trận), L-Final(1 trận)
     *  Grand Final: 1 trận
     *  Tổng: 15 trận
     * ═══════════════════════════════════════════════════════════ */

    private void seedDE8(User owner) {
        final String NAME = "BTMS [Test] Double Elimination — 8 Cơ Thủ (8-Ball)";
        if (tournamentExists(NAME)) return;

        Tournament t = createTournament(NAME,
                "Giải test Double Elimination — 8 cơ thủ, bracket gọn, không có BYE. "
                + "Thể thức: Nhánh thắng + Nhánh thua → Chung kết lớn.",
                "8_BALL", "DOUBLE_ELIMINATION", 8,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(4000000),
                "Vô địch 2.000.000đ · Á quân 1.200.000đ · Hạng 3 800.000đ",
                owner);

        createConfig(t, "RANDOM");

        // Race-to rules — khớp với key code sinh ra trong generateDoubleElimination
        // Winners Bracket (3 vòng)
        addRaceToRule(t, "winners_r1", "WINNERS", 5);  // W Vòng 1
        addRaceToRule(t, "winners_r2", "WINNERS", 7);  // W Bán kết
        addRaceToRule(t, "winners_r3", "WINNERS", 7);  // W Chung kết (Winners Final)
        // Losers Bracket (4 vòng)
        addRaceToRule(t, "losers_r1",    "LOSERS", 5);  // L Vòng 1
        addRaceToRule(t, "losers_r2",    "LOSERS", 5);  // L Vòng 2
        addRaceToRule(t, "losers_r3",    "LOSERS", 7);  // L Bán kết
        addRaceToRule(t, "losers_final", "LOSERS", 7);  // L Chung kết
        // Grand Final
        addRaceToRule(t, "grand_final", "GRAND_FINAL", 9);

        List<String> names = List.of(
                "Nguyễn Bảo Long",
                "Trần Đức Minh",
                "Lê Quang Hiếu",
                "Phạm Văn Đức",
                "Hoàng Nhật Nam",
                "Vũ Công Thành",
                "Đặng Trọng Khải",
                "Bùi Minh Khoa"
        );
        createParticipants(t, names);
        log.info("Seeded: {} ({} cơ thủ)", NAME, names.size());
    }

    /* ═══════════════════════════════════════════════════════════
     *  DOUBLE ELIMINATION — 10 cơ thủ
     *
     *  bracketSize=16, wTotalRounds=4, lTotalRounds=6
     *  numByes=6 → W-R1: 6 BYE + 2 trận thật
     *
     *  R2 (Tứ kết winners): 8 người (6 bye-winners + 2 match-winners)
     *  Losers: L-R1(4), L-R2(4), L-R3(2), L-R4(2), L-R5(1), L-Final(1)
     *  Tổng: 7(W) + 14(L) + 1(GF) = 22 trận (+ 6 BYE tự động)
     * ═══════════════════════════════════════════════════════════ */

    private void seedDE10(User owner) {
        final String NAME = "BTMS [Test] Double Elimination — 10 Cơ Thủ (9-Ball)";
        if (tournamentExists(NAME)) return;

        Tournament t = createTournament(NAME,
                "Giải test Double Elimination — 10 cơ thủ trong bracket 16. "
                + "6 BYE ở Vòng 1 Winners, 2 trận thật. Kiểm tra xử lý BYE trong DE.",
                "9_BALL", "DOUBLE_ELIMINATION", 10,
                BigDecimal.valueOf(150000), BigDecimal.valueOf(6000000),
                "Vô địch 3.000.000đ · Á quân 1.800.000đ · Hạng 3-4 600.000đ",
                owner);

        createConfig(t, "RANDOM");

        // Winners Bracket (4 vòng: bracketSize=16 → log2(16)=4)
        addRaceToRule(t, "winners_r1", "WINNERS", 5);  // W Vòng 1 (6 BYE + 2 thật)
        addRaceToRule(t, "winners_r2", "WINNERS", 5);  // W Tứ kết (8 người)
        addRaceToRule(t, "winners_r3", "WINNERS", 7);  // W Bán kết (4 người)
        addRaceToRule(t, "winners_r4", "WINNERS", 7);  // W Chung kết (2 người)
        // Losers Bracket (6 vòng)
        addRaceToRule(t, "losers_r1",    "LOSERS", 5);
        addRaceToRule(t, "losers_r2",    "LOSERS", 5);
        addRaceToRule(t, "losers_r3",    "LOSERS", 5);
        addRaceToRule(t, "losers_r4",    "LOSERS", 7);
        addRaceToRule(t, "losers_r5",    "LOSERS", 7);
        addRaceToRule(t, "losers_final", "LOSERS", 7);
        // Grand Final
        addRaceToRule(t, "grand_final", "GRAND_FINAL", 9);

        List<String> names = List.of(
                "Dương Bảo Khánh",
                "Phan Quang Thắng",
                "Đỗ Minh Dũng",
                "Phạm Hữu Trọng",
                "Ngô Hữu Lộc",
                "Trương Quốc Hải",
                "Nguyễn Nhật Huy",
                "Trần Tiến Đạt",
                "Lê Mạnh Hào",
                "Hoàng Đức Lợi"
        );
        createParticipants(t, names);
        log.info("Seeded: {} ({} cơ thủ)", NAME, names.size());
    }

    /* ═══════════════════════════════════════════════════════════
     *  GROUP_PLAYOFF — 8 cơ thủ, top 4 vào Playoff
     *
     *  Vòng tròn: 7 vòng (n-1=7), mỗi vòng 4 trận → 28 trận RR
     *  Playoff: top 4 → SF (2 trận) + Final (1 trận) = 3 trận playoff
     *  Tổng: 31 trận
     *
     *  Playoff bracket (playoffSize=4, pTotalRounds=2):
     *    PO-R1-M1: Seed1 vs Seed4 → winner → PO-R2-M1 (Final)
     *    PO-R1-M2: Seed2 vs Seed3 → winner → PO-R2-M1 (Final)
     *    Vòng PO-R1 = semi_final (resolveRoundKey(1,2) = "semi_final")
     *    Vòng PO-R2 = final
     * ═══════════════════════════════════════════════════════════ */

    private void seedGroupPlayoff8(User owner) {
        final String NAME = "BTMS [Test] Vòng Tròn — 8 Cơ Thủ (Top 4 Playoff)";
        if (tournamentExists(NAME)) return;

        Tournament t = createTournament(NAME,
                "Giải test Group Playoff — 8 cơ thủ vòng tròn (7 vòng, 28 trận). "
                + "Xếp hạng: Thắng → Hiệu số frame → Số frame thắng. Top 4 vào bán kết.",
                "8_BALL", "GROUP_PLAYOFF", 8,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(3000000),
                "Vô địch 1.500.000đ · Á quân 900.000đ · Hạng 3-4 300.000đ",
                owner);

        createConfig(t, "RANDOM");

        // Group Stage — tất cả trận đều dùng key "group_stage"
        addRaceToRule(t, "group_stage", "GROUP", 5);
        // Playoff — keys từ resolveRoundKey(round, pTotalRounds=2)
        addRaceToRule(t, "semi_final", "PLAYOFF", 7);  // PO-R1 (4 người → 2)
        addRaceToRule(t, "final",      "PLAYOFF", 9);  // PO-R2 (2 người → Champion)

        // playoff_size config: top 4 advance
        addPlayoffSizeConfig(t, 4);

        List<String> names = List.of(
                "Nguyễn Văn Hùng",
                "Trần Minh Tuấn",
                "Lê Hoàng Nam",
                "Phạm Đức Anh",
                "Hoàng Quốc Việt",
                "Vũ Thanh Bình",
                "Phan Trọng Khôi",
                "Trương Xuân Long"
        );
        createParticipants(t, names);
        log.info("Seeded: {} ({} cơ thủ)", NAME, names.size());
    }

    /* ═══════════════════════════════════════════════════════════
     *  GROUP_PLAYOFF — 12 cơ thủ, top 4 vào Playoff
     *
     *  Vòng tròn: 11 vòng (n-1=11), mỗi vòng 6 trận → 66 trận RR
     *    (12 cơ thủ → số chẵn → không cần dummy player)
     *  Xếp hạng: wins → frame_diff → frames_won
     *  Playoff: top 4 → SF (2 trận) + Final (1 trận) = 3 trận
     *  Tổng: 69 trận
     *
     *  Race-to rules giống Group Playoff 8.
     * ═══════════════════════════════════════════════════════════ */

    private void seedGroupPlayoff12(User owner) {
        final String NAME = "BTMS [Test] Vòng Tròn — 12 Cơ Thủ (Top 4 Playoff)";
        if (tournamentExists(NAME)) return;

        Tournament t = createTournament(NAME,
                "Giải test Group Playoff — 12 cơ thủ vòng tròn (11 vòng, 66 trận). "
                + "Case thực tế nhất: số người không phải lũy thừa 2. "
                + "Top 4 theo điểm vào bán kết.",
                "9_BALL", "GROUP_PLAYOFF", 12,
                BigDecimal.valueOf(150000), BigDecimal.valueOf(5000000),
                "Vô địch 2.500.000đ · Á quân 1.500.000đ · Hạng 3-4 500.000đ",
                owner);

        createConfig(t, "RANDOM");

        addRaceToRule(t, "group_stage", "GROUP",   5);
        addRaceToRule(t, "semi_final",  "PLAYOFF",  7);
        addRaceToRule(t, "final",       "PLAYOFF",  9);

        addPlayoffSizeConfig(t, 4);

        List<String> names = List.of(
                "Bùi Hữu Phúc",
                "Đặng Đình Khoa",
                "Đỗ Công Danh",
                "Hồ Quang Minh",
                "Ngô Thành Trung",
                "Nguyễn Bảo Long",
                "Trần Hoàng Vũ",
                "Lê Đức Toàn",
                "Phạm Thanh Tùng",
                "Hoàng Minh Châu",
                "Vũ Đình Kiên",
                "Phan Văn Nghĩa"
        );
        createParticipants(t, names);
        log.info("Seeded: {} ({} cơ thủ)", NAME, names.size());
    }

    /* ═══════════════════════════════════════════════════════════
     *  Helpers — Tournament, Config, RaceToRule, Participant
     * ═══════════════════════════════════════════════════════════ */

    private Tournament createTournament(String name, String description,
            String gameType, String format, int maxParticipants,
            BigDecimal entryFee, BigDecimal prizePool, String prizeDescription,
            User owner) {
        Instant now = Instant.now();
        Tournament t = Tournament.builder()
                .name(name)
                .description(description)
                .gameType(gameType)
                .format(format)
                .participantType("SINGLE")
                .status("REGISTRATION_CLOSED")  // sẵn sàng để generate bracket
                .maxParticipants(maxParticipants)
                .entryFee(entryFee)
                .prizePool(prizePool)
                .prizeDescription(prizeDescription)
                .registrationDeadline(now.minus(1, ChronoUnit.DAYS))
                .startAt(now.plus(2, ChronoUnit.DAYS))
                .endAt(now.plus(4, ChronoUnit.DAYS))
                .isRegister(false)
                .createdBy(owner)
                .build();
        return tournamentRepository.save(t);
    }

    private void createConfig(Tournament t, String seedingMethod) {
        if (tournamentConfigRepository.existsById(t.getId())) return;
        tournamentConfigRepository.save(TournamentConfig.builder()
                .tournament(t)
                .formatCode(t.getFormat())
                .seedingMethod(seedingMethod)
                .build());
    }

    private void addRaceToRule(Tournament t, String roundKey, String bracketPhase, int raceTo) {
        if (raceToRuleRepository.findByTournamentIdAndRoundKey(t.getId(), roundKey).isPresent()) return;
        raceToRuleRepository.save(TournamentRaceToRule.builder()
                .tournament(t)
                .roundKey(roundKey)
                .bracketPhase(bracketPhase)
                .raceTo(raceTo)
                .build());
    }

    private void createParticipants(Tournament t, List<String> displayNames) {
        long existing = participantRepository.countByTournamentIdAndStatus(t.getId(), "ACTIVE");
        if (existing >= displayNames.size()) return;
        for (String name : displayNames) {
            participantRepository.save(Participant.builder()
                    .tournament(t)
                    .registration(null)   // standalone participant (không qua đăng ký online)
                    .participantType("SINGLE")
                    .displayName(name)
                    .status("ACTIVE")
                    .build());
        }
    }

    /* ═══════════════════════════════════════════════════════════
     *  playoff_size ConfigFieldDefinition + TournamentConfigValue
     *
     *  FK chain:
     *    TournamentConfigValue.fieldKey → ConfigFieldDefinition.fieldKey
     *  Nên phải đảm bảo ConfigFieldDefinition "playoff_size" tồn tại
     *  trước khi tạo TournamentConfigValue.
     * ═══════════════════════════════════════════════════════════ */

    /* ═══════════════════════════════════════════════════════════
     *  CUT_TO_SE — 16 cơ thủ → Last 8 SE
     *
     *  16 người, de_mode=CUT_TO_SE, se_phase_size=8
     *  cutoffRound = log2(16/8)+1 = 2
     *  W: R1(8 trận,5) R2(4 trận,7) → 4 survivors
     *  L: L-R1(4 trận,5) L-Final(4 trận,7) → 4 survivors
     *  SE: 8 người → QF(4) SF(2) Final(1)
     * ═══════════════════════════════════════════════════════════ */

    private void seedDECutToSE16(User owner) {
        // Tỉ lệ 1/4 chuẩn: 16 người → Last 4 SE
        // cutoffRound = log2(16/4)+1 = log2(4)+1 = 3
        // W: R1(8 trận,5) R2(4 trận,5) R3(2 trận,7) → 2 W survivors
        // L: lCutoffRounds=4 → R1(4,5) R2(4,5) R3(2,7) Final(2,7) → 2 L survivors
        // SE: 4 người, seTotalRounds=2 → SF(2 trận,7) + Final(1 trận,9)
        final String NAME = "BTMS [Test] Nhánh Thắng/Thua — 16 Cơ Thủ → Last 4 (8-Ball, tỉ lệ 1/4)";
        if (tournamentExists(NAME)) return;

        Tournament t = createTournament(NAME,
                "Giải test CUT_TO_SE chuẩn 1/4 — 16 cơ thủ, 2 mạng. "
                + "Sau cutoffRound=3: 2 từ Nhánh Thắng + 2 từ Nhánh Thua → Last 4 loại trực tiếp.",
                "8_BALL", "DOUBLE_ELIMINATION", 16,
                BigDecimal.valueOf(150000), BigDecimal.valueOf(6000000),
                "Vô địch 3.000.000đ · Á quân 1.800.000đ · Hạng 3-4 600.000đ",
                owner);

        createConfig(t, "RANDOM");

        // DE phase — W bracket (3 vòng, cutoffRound=3)
        addRaceToRule(t, "de_winners_r1", "WINNERS", 5);
        addRaceToRule(t, "de_winners_r2", "WINNERS", 5);
        addRaceToRule(t, "de_winners_r3", "WINNERS", 7); // cutoff (2 người sống sót)
        // DE phase — L bracket (4 vòng, lCutoffRounds=4)
        addRaceToRule(t, "de_losers_r1",    "LOSERS", 5);
        addRaceToRule(t, "de_losers_r2",    "LOSERS", 5);
        addRaceToRule(t, "de_losers_r3",    "LOSERS", 7);
        addRaceToRule(t, "de_losers_final", "LOSERS", 7); // 2 người sống sót
        // SE phase — Last 4 (seSize=4, seTotalRounds=2)
        // resolveSeRoundKey(1,2,4)=se_semi_final  (fromEnd=1)
        // resolveSeRoundKey(2,2,4)=se_final        (fromEnd=0)
        addRaceToRule(t, "se_semi_final", "KNOCKOUT", 7); // SE-R1: 2W vs 2L
        addRaceToRule(t, "se_final",      "KNOCKOUT", 9); // SE-R2: Final

        // CUT_TO_SE config — se_phase_size=4 → 1/4 của 16
        addStringConfig(t, "de_mode", "CUT_TO_SE");
        addIntConfig(t, "se_phase_size", 4);

        List<String> names = List.of(
                "Nguyễn Văn An", "Trần Quốc Bảo", "Lê Minh Cường", "Phạm Anh Dũng",
                "Hoàng Đức Hải", "Vũ Thanh Hùng", "Đặng Văn Khoa", "Bùi Trọng Lâm",
                "Đỗ Quang Long", "Hồ Minh Nam",   "Ngô Văn Phát",  "Dương Hữu Quân",
                "Trương Đình Sơn", "Phan Bá Tài", "Lê Quốc Tuấn",  "Nguyễn Hải Việt"
        );
        createParticipants(t, names);
        log.info("Seeded: {} ({} cơ thủ, CUT_TO_SE→Last4, tỉ lệ 1/4)", NAME, names.size());
    }

    /* ═══════════════════════════════════════════════════════════
     *  CUT_TO_SE — 32 cơ thủ → Last 8 SE (Matchroom style)
     *
     *  32 người, de_mode=CUT_TO_SE, se_phase_size=8
     *  cutoffRound = log2(32/8)+1 = 3
     *  W: R1(16,5) R2(8,5) R3(4,7) → 4 survivors
     *  L: L-R1(8,5) L-R2(8,5) L-R3(4,7) L-Final(4,7) → 4 survivors
     *  SE: 8 người → QF SF Final (same as above)
     * ═══════════════════════════════════════════════════════════ */

    private void seedDECutToSE32(User owner) {
        final String NAME = "BTMS [Test] Nhánh Thắng/Thua — 32 Cơ Thủ → Last 8 (9-Ball, Matchroom Style)";
        if (tournamentExists(NAME)) return;

        Tournament t = createTournament(NAME,
                "Giải test CUT_TO_SE Matchroom — 32 cơ thủ trong DE bracket. "
                + "Sau 3 vòng W: 4 undefeated + 4 từ Nhánh Thua (1 loss) → Last 8 SE.",
                "9_BALL", "DOUBLE_ELIMINATION", 32,
                BigDecimal.valueOf(200000), BigDecimal.valueOf(10000000),
                "Vô địch 5.000.000đ · Á quân 3.000.000đ · Hạng 3-4 1.000.000đ",
                owner);

        createConfig(t, "RANDOM");

        // DE W bracket (3 vòng)
        addRaceToRule(t, "de_winners_r1", "WINNERS", 5);
        addRaceToRule(t, "de_winners_r2", "WINNERS", 5);
        addRaceToRule(t, "de_winners_r3", "WINNERS", 7); // cutoff
        // DE L bracket (4 vòng, lCutoffRounds=4)
        addRaceToRule(t, "de_losers_r1",    "LOSERS", 5);
        addRaceToRule(t, "de_losers_r2",    "LOSERS", 5);
        addRaceToRule(t, "de_losers_r3",    "LOSERS", 7);
        addRaceToRule(t, "de_losers_final", "LOSERS", 7);
        // SE phase — Last 8
        addRaceToRule(t, "se_quarter_final", "KNOCKOUT", 7);
        addRaceToRule(t, "se_semi_final",    "KNOCKOUT", 7);
        addRaceToRule(t, "se_final",         "KNOCKOUT", 9);

        addStringConfig(t, "de_mode", "CUT_TO_SE");
        addIntConfig(t, "se_phase_size", 8);

        List<String> names = List.of(
                "Nguyễn Bảo Long",  "Trần Đức Minh",   "Lê Quang Hiếu",   "Phạm Văn Đức",
                "Hoàng Nhật Nam",   "Vũ Công Thành",   "Đặng Trọng Khải", "Bùi Minh Khoa",
                "Nguyễn Văn Hùng",  "Trần Minh Tuấn",  "Lê Hoàng Nam",    "Phạm Đức Anh",
                "Hoàng Quốc Việt",  "Vũ Thanh Bình",   "Phan Trọng Khôi", "Trương Xuân Long",
                "Bùi Hữu Phúc",    "Đặng Đình Khoa",  "Đỗ Công Danh",    "Hồ Quang Minh",
                "Ngô Thành Trung",  "Dương Bảo Khánh", "Phan Quang Thắng","Đỗ Minh Dũng",
                "Phạm Hữu Trọng",  "Ngô Hữu Lộc",     "Trương Quốc Hải", "Nguyễn Nhật Huy",
                "Trần Tiến Đạt",   "Lê Mạnh Hào",     "Hoàng Đức Lợi",   "Vũ Minh Phát"
        );
        createParticipants(t, names);
        log.info("Seeded: {} ({} cơ thủ, CUT_TO_SE→Last8)", NAME, names.size());
    }

    private void ensurePlayoffSizeFieldExists() {
        if (configFieldRepository.existsById("playoff_size")) return;
        configFieldRepository.save(ConfigFieldDefinition.builder()
                .fieldKey("playoff_size")
                .label("Số cơ thủ vào Playoff")
                .description("Số cơ thủ xếp hạng cao nhất được vào vòng playoff. Phải là lũy thừa 2.")
                .dataType("INT")
                .fieldScope("GROUP")
                .uiComponent("NUMBER_INPUT")
                .minValue(2)
                .maxValue(16)
                .isActive(true)
                .build());
        log.info("Seeded ConfigFieldDefinition: playoff_size");
    }

    private void addPlayoffSizeConfig(Tournament t, int size) {
        addIntConfig(t, "playoff_size", size);
    }

    /** Generic helper — tạo TournamentConfigValue String. */
    private void addStringConfig(Tournament t, String key, String value) {
        TournamentConfigValueId id = new TournamentConfigValueId(t.getId(), key);
        if (configValueRepository.existsById(id)) return;
        ConfigFieldDefinition fieldDef = configFieldRepository.findById(key).orElse(null);
        if (fieldDef == null) { log.warn("ConfigFieldDefinition '{}' chưa tồn tại, bỏ qua.", key); return; }
        configValueRepository.save(TournamentConfigValue.builder()
                .id(id).tournament(t).fieldDefinition(fieldDef).value(value).build());
    }

    /** Generic helper — tạo TournamentConfigValue Integer. */
    private void addIntConfig(Tournament t, String key, int value) {
        addStringConfig(t, key, String.valueOf(value));
    }

    /**
     * Link de_mode + se_phase_size vào format DOUBLE_ELIMINATION
     * qua bảng format_config_fields — để UI config form hiển thị cho owner.
     *
     * Bảng format_config_fields quyết định field nào hiện trong UI
     * khi owner cấu hình giải đấu có format DOUBLE_ELIMINATION.
     */
    private void ensureFormatConfigFieldsForDE() {
        linkFormatConfigField("DOUBLE_ELIMINATION", "de_mode",       "FULL_DE", 90);
        linkFormatConfigField("DOUBLE_ELIMINATION", "se_phase_size", "64",      91);
    }

    private void linkFormatConfigField(String formatCode, String fieldKey,
                                       String defaultValue, int sortOrder) {
        if (formatConfigFieldRepository.existsByFormatCodeAndFieldKey(formatCode, fieldKey)) return;
        formatConfigFieldRepository.save(FormatConfigField.builder()
                .formatCode(formatCode)
                .fieldKey(fieldKey)
                .defaultValue(defaultValue)
                .isRequired(false)
                .isVisibleToOwner(true)
                .sortOrder(sortOrder)
                .build());
        log.info("Seeded FormatConfigField: {} → {}", formatCode, fieldKey);
    }

    /** Đảm bảo ConfigFieldDefinition cho de_mode + se_phase_size tồn tại. */
    private void ensureDEConfigFieldsExist() {
        if (!configFieldRepository.existsById("de_mode")) {
            configFieldRepository.save(ConfigFieldDefinition.builder()
                    .fieldKey("de_mode")
                    .label("Chế độ Double Elimination")
                    .description("FULL_DE: DE đến vô địch | CUT_TO_SE: DE đến cutoff rồi chuyển sang SE")
                    .dataType("ENUM")
                    .fieldScope("KNOCKOUT")
                    .uiComponent("RADIO_GROUP")
                    .isActive(true)
                    .build());
            log.info("Seeded ConfigFieldDefinition: de_mode");
        }
        if (!configFieldRepository.existsById("se_phase_size")) {
            configFieldRepository.save(ConfigFieldDefinition.builder()
                    .fieldKey("se_phase_size")
                    .label("Số người vào Last X (SE phase)")
                    .description("Tổng W+L survivors chuyển vào SE bracket (e.g. 64, 32, 16, 8). Phải là lũy thừa 2.")
                    .dataType("INT")
                    .fieldScope("KNOCKOUT")
                    .uiComponent("NUMBER_INPUT")
                    .minValue(4)
                    .maxValue(256)
                    .isActive(true)
                    .build());
            log.info("Seeded ConfigFieldDefinition: se_phase_size");
        }
    }

    private boolean tournamentExists(String name) {
        return tournamentRepository.findAll().stream()
                .anyMatch(t -> name.equals(t.getName()));
    }
}
