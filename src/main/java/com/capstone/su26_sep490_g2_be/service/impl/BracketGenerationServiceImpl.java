package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.BilliardRank;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.SeedingMethod;
import com.capstone.su26_sep490_g2_be.enums.TournamentStageStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.repository.*;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.MatchSchedulingService;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.service.TournamentAuditService;
import com.capstone.su26_sep490_g2_be.service.TournamentRaceToRuleService;
import com.capstone.su26_sep490_g2_be.util.AvatarUrlResolver;
import com.capstone.su26_sep490_g2_be.util.DoubleEliminationBracketMath;
import com.capstone.su26_sep490_g2_be.util.ProgressiveSurvivorsUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class BracketGenerationServiceImpl implements BracketGenerationService {

    private final TournamentRepository tournamentRepository;
    private final TournamentConfigRepository tournamentConfigRepository;
    private final TournamentConfigValueRepository configValueRepository;
    private final ParticipantRepository participantRepository;
    private final TournamentStageRepository stageRepository;
    private final MatchRepository matchRepository;
    private final MatchScoreEventRepository scoreEventRepository;
    private final TournamentRaceToRuleService raceToRuleService;
    private final TournamentAuditService tournamentAuditService;
    private final ApplicationEventPublisher eventPublisher;
    private final MailContextBuilder mailContextBuilder;
    private final MatchSchedulingService matchSchedulingService;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;

    /** Owner thao tác được bracket của mọi giải (1 chuỗi); Manager chỉ giải thuộc chi nhánh được cấp quyền. */
    private void assertActorCanAccessTournament(Long actorUserId, Tournament tournament) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
        Branch branch = tournament.getBranch();
        Long branchId = branch != null ? branch.getId() : null;
        if (!branchAccessService.canActorAccessBranch(actor, branchId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
    }

    /* ═══════════════════════════════════════════════════════════
     *  ENTRY POINT
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public DrawResultResponse generate(Long tournamentId, Long actorUserId) {
        // PESSIMISTIC_WRITE — hai request "Bốc thăm" bắn gần như đồng thời phải xếp hàng thay vì
        // cùng đọc "chưa có stage" rồi cùng insert, gây race condition ở check-then-act bên dưới:
        // request thua trước đây rơi vào exception DB-level không map được, trả 500 thay vì 409 sạch.
        Tournament tournament = tournamentRepository.findByIdWithLock(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        assertActorCanAccessTournament(actorUserId, tournament);

        String currentStatus = tournament.getStatus();
        if (!TournamentStatus.REGISTRATION_CLOSED.getValue().equals(currentStatus)
                && !TournamentStatus.DRAW_PREVIEW.getValue().equals(currentStatus)) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }

        if (TournamentStatus.DRAW_PREVIEW.getValue().equals(currentStatus)) {
            clearExistingBracket(tournamentId);
        } else {
            if (!stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId).isEmpty()) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
        }

        List<Participant> participants = participantRepository.findByTournamentIdAndStatus(tournamentId, ParticipantStatus.ACTIVE.getValue());
        if (participants.size() < 2) throw new BusinessException(ErrorCode.TOURNAMENT_NOT_ENOUGH_PARTICIPANTS);

        TournamentConfig config = tournamentConfigRepository.findById(tournamentId).orElse(null);
        String seedingMethod = config != null ? config.getSeedingMethod() : SeedingMethod.RANDOM.name();

        if (SeedingMethod.SEED.name().equals(seedingMethod)) {
            validateSeedNoContiguous(participants);
        }

        participants = resolveSeedRankOrder(seedingMethod, participants);

        // Liệt kê tường minh từng thể thức có sinh bracket thật — không dùng default rơi về Loại
        // trực tiếp cho MỌI format lạ, kể cả GROUP_PLAYOFF hay bất kỳ mã format nào chưa từng được
        // cài đặt logic sinh bracket. Trước đây default fallback khiến Owner tưởng đang chạy đúng
        // thể thức đã chọn nhưng thực ra luôn ra bracket Loại trực tiếp — sai âm thầm, không lỗi.
        String format = tournament.getFormat();
        BracketResult result = switch (format) {
            case "SINGLE_ELIMINATION"       -> generateSingleElimination(tournament, participants);
            case "DOUBLE_ELIMINATION"       -> generateDoubleElimination(tournament, participants);
            case "PROGRESSIVE_ROUND_ROBIN"  -> generateProgressiveRoundRobin(tournament, participants);
            default                         -> throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED_FOR_DRAW);
        };

        tournament.setStatus(TournamentStatus.DRAW_PREVIEW.getValue());
        tournamentRepository.save(tournament);
        tournamentAuditService.recordChange(tournament, currentStatus, TournamentStatus.DRAW_PREVIEW.getValue(),
                actorUserId, "Bốc thăm — sinh bracket");

        // Auto gán bàn + ước lượng giờ cho toàn bộ bracket
        matchSchedulingService.reschedule(tournamentId);

        List<StageWithMatchesResponse> stageResponses = result.stages().stream()
                .map(s -> StageWithMatchesResponse.builder()
                        .id(s.getId()).tournamentId(tournamentId)
                        .name(s.getName()).stageType(s.getStageType())
                        .orderNo(s.getOrderNo()).status(s.getStatus())
                        .peRoundNo(s.getPeRoundNo()).peActiveCount(s.getPeActiveCount())
                        .peEliminateCount(s.getPeEliminateCount())
                        .matches(matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(s.getId())
                                .stream().map(this::toMatchResponse).toList())
                        .build())
                .toList();

        return DrawResultResponse.builder()
                .tournamentId(tournamentId).tournamentFormat(format)
                .participantsUsed(participants.size()).stagesCreated(result.stages().size())
                .matchesCreated(result.matches().size()).newStatus(TournamentStatus.DRAW_PREVIEW.getValue())
                .stages(stageResponses).build();
    }

    /* ═══════════════════════════════════════════════════════════
     *  SINGLE ELIMINATION
     *
     *  Vòng 1 được xếp qua assignSeededRound1() theo standardSeedOrder() — bracketSize - n slot
     *  "ảo" (rank > n) tự động thành BYE, và luôn rơi vào seed cao nhất trước (VD n=10,
     *  bracketSize=16 → seed 1-6 nhận BYE, seed 7-10 thi đấu thật ở R1) thay vì cố định theo vị trí.
     * ═══════════════════════════════════════════════════════════ */

    private BracketResult generateSingleElimination(Tournament t, List<Participant> participants) {
        int n = participants.size();
        int bracketSize = nextPowerOf2(n);
        int totalRounds = log2(bracketSize);

        TournamentStage stage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Loại trực tiếp").stageType("KNOCKOUT")
                .orderNo(1).status(TournamentStageStatus.PENDING.getValue()).build());

        // grid[round][pos] — 1-indexed
        Match[][] grid = new Match[totalRounds + 1][(bracketSize / 2) + 1];

        // Create all empty matches
        for (int round = 1; round <= totalRounds; round++) {
            int mc = bracketSize >> round;
            for (int pos = 1; pos <= mc; pos++) {
                String rk = resolveRoundKey(round, totalRounds, false);
                grid[round][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(stage).bracketType("KNOCKOUT")
                        .roundNo(round).positionNo(pos)
                        .matchCode("R%d-M%d".formatted(round, pos))
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), rk))
                        .status(MatchStatus.PENDING.getValue()).isBye(false).player1Score(0).player2Score(0).build());
            }
        }

        // Link win advancement: pos → parentPos = ceil(pos/2), slot = player1 if odd else player2
        for (int round = 1; round < totalRounds; round++) {
            int mc = bracketSize >> round;
            for (int pos = 1; pos <= mc; pos++) {
                int pp   = (pos + 1) / 2;
                String slot = (pos % 2 == 1) ? "player1" : "player2";
                grid[round][pos].setNextMatchWin(grid[round + 1][pp]);
                grid[round][pos].setWinSlot(slot);
                matchRepository.save(grid[round][pos]);
            }
        }

        // Xếp vòng 1 theo thuật toán seeding chuẩn (seed cao được ưu tiên BYE, seed 1&2 tách 2 nửa...)
        assignSeededRound1(grid[1], participants, bracketSize);

        // Optional third-place match — CHỈ tạo khi cả 2 "bán kết" đều là trận thật (đủ 2 người).
        // Nếu 1 trong 2 là BYE (n lẻ dồn hết byes vào R1, và với bracketSize=4 thì R1 chính là vòng
        // bán kết), trận đó không có "người thua" thật, khiến trận 3RD vĩnh viễn thiếu 1 người —
        // không ai start/complete được và chặn luôn việc kết thúc giải (phải walkover thủ công mới
        // gỡ). Bỏ qua tạo trận 3RD trong trường hợp này; TournamentResultServiceImpl đã có sẵn
        // fallback xếp đồng hạng 3-4 cho người thua bán kết khi không có trận 3RD.
        if (resolveThirdPlaceEnabled(t.getId(), t.getFormat()) && totalRounds >= 2) {
            int sfRound = totalRounds - 1;
            int sfMc = bracketSize >> sfRound;
            boolean bothSemisPlayable = sfMc >= 2
                    && !Boolean.TRUE.equals(grid[sfRound][1].getIsBye())
                    && !Boolean.TRUE.equals(grid[sfRound][2].getIsBye());
            if (bothSemisPlayable) {
                Match thirdPlace = matchRepository.save(Match.builder()
                        .tournament(t).stage(stage).bracketType("KNOCKOUT")
                        .roundNo(totalRounds).positionNo(2).matchCode("3RD")
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), "third_place"))
                        .status(MatchStatus.PENDING.getValue()).isBye(false).player1Score(0).player2Score(0).build());
                grid[sfRound][1].setNextMatchLose(thirdPlace); grid[sfRound][1].setLoseSlot("player1");
                grid[sfRound][2].setNextMatchLose(thirdPlace); grid[sfRound][2].setLoseSlot("player2");
                matchRepository.save(grid[sfRound][1]);
                matchRepository.save(grid[sfRound][2]);
            }
        }

        return new BracketResult(List.of(stage),
                matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stage.getId()));
    }

    /* ═══════════════════════════════════════════════════════════
     *  DOUBLE ELIMINATION  (rewritten)
     *
     *  For bracketSize B, wTotalRounds = log2(B):
     *
     *  LOSERS match count per round lr:
     *    mc(lr) = B >> (ceil(lr/2) + 1)   where ceil(lr/2) = (lr+1)/2 in integer math
     *    B=8: L-R1=2, L-R2=2, L-R3=1, L-R4=1
     *    B=16: L-R1=4, L-R2=4, L-R3=2, L-R4=2, L-R5=1, L-R6=1
     *
     *  WIN-ADVANCEMENT parity within losers:
     *    Odd lr  (1,3,5...) → Even lr+1:  same position,  player1 slot
     *                         (player2 will be filled by W drop)
     *    Even lr (2,4,6...) → Odd  lr+1:  compress pos→⌈pos/2⌉, odd=P1 even=P2
     *
     *  WINNERS → LOSERS drops:
     *    W-R1 M(2k-1) loser → L-R1 M(k) as player1
     *    W-R1 M(2k)   loser → L-R1 M(k) as player2
     *    W-R(wr≥2) M(pos) loser → L-R(2*(wr-1)) M(pos) as player2
     * ═══════════════════════════════════════════════════════════ */

    /**
     * DOUBLE_ELIMINATION luôn cắt về loại trực tiếp khi còn {@code se_phase_size} người (xem
     * {@link #generateCutToSEDE}) — không còn chế độ "đánh loại kép tới tận vô địch" (2 người cuối
     * cùng của 2 nhánh gặp nhau ở 1 trận chung kết lớn duy nhất): thực tế không giải nào tổ chức theo
     * kiểu đó, và bracket-reset của trận chung kết lớn kiểu cũ cũng chưa từng được cài đặt đúng.
     */
    private BracketResult generateDoubleElimination(Tournament t, List<Participant> participants) {
        return generateCutToSEDE(t, participants);
    }

    /* ═══════════════════════════════════════════════════════════
     *  PROGRESSIVE_ROUND_ROBIN (Vòng tròn loại dần nhiều giai đoạn + Playoff)
     *
     *  Tại draw: sinh SẴN TOÀN BỘ khung — tất cả GĐ League (round-robin) + bracket Playoff.
     *    - GĐ 1 điền người thật ngay.
     *    - GĐ 2..N và Playoff để TRỐNG (placeholder) theo lịch circle-method deterministic.
     *  Người dùng thấy trọn lộ trình GĐ1 → ... → Chung kết ngay từ bốc thăm.
     *
     *  Sau mỗi GĐ, owner gọi advanceProgressiveStage:
     *    - Tính standings RIÊNG của GĐ (có head-to-head)
     *    - Loại người ngoài top-survivors → participant INACTIVE
     *    - ĐIỀN người đi tiếp vào các ô placeholder của GĐ kế (hoặc seeding vào Playoff)
     *
     *  Lịch RR đã tạo sẵn (empty) và lịch điền người dùng CHUNG một schedule deterministic
     *  (roundRobinSchedule) nên (round, positionNo) khớp tuyệt đối giữa 2 bước.
     * ═══════════════════════════════════════════════════════════ */

    private BracketResult generateProgressiveRoundRobin(Tournament t, List<Participant> participants) {
        int n = participants.size();
        List<Integer> survivors = parseProgressiveSurvivors(readStringConfig(t.getId(), "pe_survivors_per_stage", "10,6,4"));
        int numLeague = survivors.size();

        // Cấu hình pe_survivors_per_stage được validate lúc LƯU CONFIG dựa trên maxParticipants
        // (số slot tối đa của giải, VD 16) — không phải số người ĐĂNG KÝ THẬT lúc bốc thăm.
        // Nếu số người tham gia thực tế ít hơn giả định của config (VD chỉ 8 người đăng ký
        // nhưng config "10,6,4" được thiết kế cho 16), phải chặn lại ở đây thay vì sinh ra
        // các giai đoạn sau với số lượng ảo lớn hơn số người thật.
        List<String> turnoutErrors = ProgressiveSurvivorsUtil.validate(survivors, n, survivors.get(numLeague - 1));
        if (!turnoutErrors.isEmpty()) {
            throw new BusinessException(ErrorCode.PROGRESSIVE_CONFIG_INVALID,
                    "Cấu hình \"Số người đi tiếp mỗi giai đoạn\" (" + survivors
                            + ") không phù hợp với số người tham gia thực tế (" + n
                            + " người). Vui lòng chỉnh lại cấu hình cho khớp số người đã đăng ký trước khi bốc thăm: "
                            + String.join("; ", turnoutErrors));
        }

        validateProgressiveSurvivorsAgainstHeadcount(survivors, n);
        int raceToLeague = safeResolveRaceTo(t.getId(), t.getFormat(), "league_stage");

        List<TournamentStage> stages = new ArrayList<>();
        List<Match> allMatches = new ArrayList<>();

        for (int i = 1; i <= numLeague; i++) {
            int playerCount = (i == 1) ? n : survivors.get(i - 2);      // GĐ1: toàn bộ; GĐ sau: survivors GĐ trước
            int keepAfter = survivors.get(i - 1);                        // số người còn lại SAU GĐ này
            int elim = Math.max(0, playerCount - keepAfter);
            TournamentStage stage = stageRepository.save(TournamentStage.builder()
                    .tournament(t).name("Vòng tròn — Giai đoạn " + i).stageType("PROGRESSIVE_ROUND")
                    .orderNo(i).status(TournamentStageStatus.PENDING.getValue())
                    .peRoundNo(i).peActiveCount(playerCount).peEliminateCount(elim).build());
            List<Participant> roster = (i == 1) ? participants : null;   // GĐ1 điền thật; GĐ sau placeholder
            allMatches.addAll(buildRoundRobin(t, stage, playerCount, roster, "L" + i, raceToLeague));
            stages.add(stage);
        }

        // Bracket Playoff rỗng — điền người khi advance từ GĐ League cuối
        int playoffSize = survivors.get(numLeague - 1);
        TournamentStage playoff = buildEmptyPlayoffBracket(t, numLeague + 1, playoffSize);
        stages.add(playoff);
        allMatches.addAll(matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(playoff.getId()));

        return new BracketResult(stages, allMatches);
    }

    /**
     * Lịch vòng tròn 1 lượt (mỗi cặp gặp đúng 1 lần) bằng circle method cho {@code count} vị trí.
     * Trả về danh sách {round, positionNo, idxA, idxB} — idx là chỉ số vị trí 0..count-1.
     * Nhóm lẻ thêm dummy (index == count) → cặp gặp dummy bị bỏ (người đó nghỉ vòng đó).
     * Deterministic: cùng {@code count} luôn cho cùng cấu trúc (round, positionNo).
     */
    private List<int[]> roundRobinSchedule(int count) {
        List<int[]> schedule = new ArrayList<>();
        int sz = count % 2 == 0 ? count : count + 1;
        boolean hasDummy = sz != count;
        int[] ring = new int[sz];
        for (int i = 0; i < sz; i++) ring[i] = i;
        int totalRounds = sz - 1;
        for (int round = 1; round <= totalRounds; round++) {
            int posNo = 0;
            for (int k = 0; k < sz / 2; k++) {
                int a = ring[k], b = ring[sz - 1 - k];
                if (hasDummy && (a == count || b == count)) continue; // idx == count là dummy
                posNo++;
                schedule.add(new int[] {round, posNo, a, b});
            }
            int last = ring[sz - 1];
            System.arraycopy(ring, 1, ring, 2, sz - 2);
            ring[1] = last;
        }
        return schedule;
    }

    /**
     * Tạo match vòng tròn theo schedule. {@code orderedPlayers} = danh sách người theo vị trí
     * (null → tạo match TRỐNG placeholder). Match KHÔNG có next-match links.
     */
    private List<Match> buildRoundRobin(Tournament t, TournamentStage stage, int count,
                                        List<Participant> orderedPlayers, String codePrefix, int raceTo) {
        List<Match> matches = new ArrayList<>();
        for (int[] s : roundRobinSchedule(count)) {
            int round = s[0], pos = s[1], a = s[2], b = s[3];
            Participant p1 = orderedPlayers != null ? orderedPlayers.get(a) : null;
            Participant p2 = orderedPlayers != null ? orderedPlayers.get(b) : null;
            Match m = matchRepository.save(Match.builder()
                    .tournament(t).stage(stage).bracketType("PROGRESSIVE_ROUND")
                    .roundNo(round).positionNo(pos)
                    .matchCode("%s-R%d-M%d".formatted(codePrefix, round, pos))
                    .raceTo(raceTo).status(MatchStatus.PENDING.getValue()).isBye(false)
                    .player1(p1).player2(p2).player1Score(0).player2Score(0).build());
            matches.add(m);
        }
        return matches;
    }

    /** Điền người vào các ô placeholder RR đã tạo sẵn (khớp theo round+positionNo của cùng schedule). */
    private void fillRoundRobinPlayers(TournamentStage stage, int count, List<Participant> orderedPlayers) {
        Map<String, Match> byKey = new HashMap<>();
        for (Match m : matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stage.getId())) {
            byKey.put(m.getRoundNo() + "-" + m.getPositionNo(), m);
        }
        for (int[] s : roundRobinSchedule(count)) {
            Match m = byKey.get(s[0] + "-" + s[1]);
            if (m == null) continue;
            m.setPlayer1(orderedPlayers.get(s[2]));
            m.setPlayer2(orderedPlayers.get(s[3]));
            matchRepository.save(m);
        }
    }

    /** Tạo bracket Playoff single-elimination RỖNG (chưa có người), có next-match links. */
    private TournamentStage buildEmptyPlayoffBracket(Tournament t, int orderNo, int playoffSize) {
        int bracketSize = nextPowerOf2(playoffSize);
        int totalRounds = log2(bracketSize);

        TournamentStage stage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Playoff").stageType("PROGRESSIVE_PLAYOFF")
                .orderNo(orderNo).status(TournamentStageStatus.PENDING.getValue()).build());

        Match[][] grid = new Match[totalRounds + 1][(bracketSize / 2) + 1];
        int raceTo = safeResolveRaceTo(t.getId(), t.getFormat(), "playoff");
        for (int round = 1; round <= totalRounds; round++) {
            int mc = bracketSize >> round;
            for (int pos = 1; pos <= mc; pos++) {
                grid[round][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(stage).bracketType("PROGRESSIVE_PLAYOFF")
                        .roundNo(round).positionNo(pos)
                        .matchCode("PO-R%d-M%d".formatted(round, pos))
                        .raceTo(raceTo)
                        .status(MatchStatus.PENDING.getValue()).isBye(false).player1Score(0).player2Score(0).build());
            }
        }
        for (int round = 1; round < totalRounds; round++) {
            int mc = bracketSize >> round;
            for (int pos = 1; pos <= mc; pos++) {
                int pp = (pos + 1) / 2;
                String slot = (pos % 2 == 1) ? "player1" : "player2";
                grid[round][pos].setNextMatchWin(grid[round + 1][pp]);
                grid[round][pos].setWinSlot(slot);
                matchRepository.save(grid[round][pos]);
            }
        }
        return stage;
    }

    @Override
    @Transactional
    public void advanceProgressiveStage(Long tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!TournamentStatus.IN_PROGRESS.getValue().equals(t.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }

        List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
        // GĐ hiện tại = GĐ League chưa COMPLETED có orderNo NHỎ NHẤT (đang thi đấu)
        TournamentStage current = stages.stream()
                .filter(s -> "PROGRESSIVE_ROUND".equals(s.getStageType()))
                .filter(s -> !TournamentStageStatus.COMPLETED.getValue().equals(s.getStatus()))
                .min(Comparator.comparingInt(TournamentStage::getOrderNo))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OPERATION));

        List<Match> curMatches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(current.getId());
        boolean allDone = !curMatches.isEmpty() && curMatches.stream().allMatch(this::isFinishedMatch);
        if (!allDone) throw new BusinessException(ErrorCode.PROGRESSIVE_STAGE_NOT_FINISHED);

        List<Integer> survivors = parseProgressiveSurvivors(
                readStringConfig(tournamentId, "pe_survivors_per_stage", "10,6,4"));
        int numLeague = survivors.size();
        int stageIndex = current.getPeRoundNo() != null ? current.getPeRoundNo() : 1; // 1-based
        int keep = survivors.get(Math.min(stageIndex - 1, survivors.size() - 1));

        List<StandingsEntryResponse> standings = computeStageStandings(current.getId());

        List<Long> advancerIds = standings.stream()
                .limit(keep).map(StandingsEntryResponse::getParticipantId).toList();
        Set<Long> advancerSet = new HashSet<>(advancerIds);
        List<Long> eliminatedIds = standings.stream()
                .map(StandingsEntryResponse::getParticipantId)
                .filter(id -> !advancerSet.contains(id))
                .toList();

        List<Participant> elim = participantRepository.findAllById(eliminatedIds);
        elim.forEach(p -> p.setStatus(ParticipantStatus.INACTIVE.getValue()));
        participantRepository.saveAll(elim);

        current.setStatus(TournamentStageStatus.COMPLETED.getValue());
        stageRepository.save(current);

        // Người đi tiếp theo đúng thứ hạng (rank order) để điền vào GĐ kế / seeding Playoff
        Map<Long, Participant> pmap = participantRepository.findAllById(advancerIds).stream()
                .collect(Collectors.toMap(Participant::getId, p -> p));
        List<Participant> advancers = advancerIds.stream()
                .map(pmap::get).filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        if (stageIndex < numLeague) {
            // Điền người vào GĐ League kế tiếp (đã tạo sẵn placeholder)
            TournamentStage next = stages.stream()
                    .filter(s -> "PROGRESSIVE_ROUND".equals(s.getStageType()))
                    .filter(s -> s.getPeRoundNo() != null && s.getPeRoundNo() == stageIndex + 1)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OPERATION));
            fillRoundRobinPlayers(next, keep, advancers);
        } else {
            // GĐ League cuối → điền seeding vào bracket Playoff đã tạo sẵn
            TournamentStage playoff = stages.stream()
                    .filter(s -> "PROGRESSIVE_PLAYOFF".equals(s.getStageType()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OPERATION));
            fillProgressivePlayoff(playoff, advancers);
        }

        // Người mới được điền vào GĐ kế → xếp lại bàn/giờ
        matchSchedulingService.reschedule(tournamentId);
    }

    /** Điền người vào bracket Playoff đã tạo sẵn — seeding chuẩn Hạng1 vs Hạng cuối... */
    private void fillProgressivePlayoff(TournamentStage playoffStage, List<Participant> advancers) {
        int playoffSize = advancers.size();
        int bracketSize = nextPowerOf2(playoffSize);

        Match[] r1Grid = new Match[(bracketSize / 2) + 1];
        matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(playoffStage.getId()).stream()
                .filter(m -> m.getRoundNo() == 1)
                .forEach(m -> r1Grid[m.getPositionNo()] = m);

        assignSeededRound1(r1Grid, new ArrayList<>(advancers), bracketSize);
    }

    /**
     * Bảng xếp hạng RIÊNG của một giai đoạn (theo stageId) — dùng cho PROGRESSIVE_ROUND_ROBIN.
     * Người chơi lấy từ player của các trận trong stage (không dùng participant ACTIVE toàn giải,
     * vì người đã bị loại giờ INACTIVE).
     *
     * <p><b>Thứ tự phân định:</b>
     * <ol>
     *   <li>Số trận thắng</li>
     *   <li>Hiệu số ván (ván thắng − ván thua), tính trên toàn giai đoạn</li>
     *   <li>Đối đầu trực tiếp giữa những người còn hoà</li>
     *   <li>Tổng số ván thắng</li>
     *   <li>ID participant — chốt cuối để kết quả luôn tất định</li>
     * </ol>
     *
     * <p>Frontend phải hiển thị theo đúng thứ tự này (xem {@code MatchesTab.computeStandings}),
     * nếu lệch thì khán giả thấy một bảng còn hệ thống loại người theo bảng khác.
     */
    @Override
    @Transactional(readOnly = true)
    public List<StandingsEntryResponse> computeStageStandings(Long stageId) {
        TournamentStage stage = stageRepository.findById(stageId).orElse(null);
        List<Match> matches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stageId);

        Map<Long, Participant> players = new LinkedHashMap<>();
        for (Match m : matches) {
            if (m.getPlayer1() != null) players.putIfAbsent(m.getPlayer1().getId(), m.getPlayer1());
            if (m.getPlayer2() != null) players.putIfAbsent(m.getPlayer2().getId(), m.getPlayer2());
        }
        if (players.isEmpty()) return List.of();

        // stat[id] = { wins, losses, racksWon, racksLost }
        Map<Long, int[]> stat = new HashMap<>();
        players.keySet().forEach(id -> stat.put(id, new int[4]));
        for (Match m : matches) {
            if (!isFinishedMatch(m) || m.getWinner() == null || m.getLoser() == null) continue;
            Long w = m.getWinner().getId(), l = m.getLoser().getId();
            int p1s = m.getPlayer1Score() != null ? m.getPlayer1Score() : 0;
            int p2s = m.getPlayer2Score() != null ? m.getPlayer2Score() : 0;
            Long p1id = m.getPlayer1() != null ? m.getPlayer1().getId() : -1L;
            int winnerFrames = w.equals(p1id) ? p1s : p2s;
            int loserFrames = winnerFrames == p1s ? p2s : p1s;
            int[] ws = stat.get(w); if (ws != null) { ws[0]++; ws[2] += winnerFrames; ws[3] += loserFrames; }
            int[] ls = stat.get(l); if (ls != null) { ls[1]++; ls[2] += loserFrames; ls[3] += winnerFrames; }
        }

        List<Long> ids = new ArrayList<>(players.keySet());
        ids.sort((a, b) -> {
            int[] sa = stat.get(a), sb = stat.get(b);
            if (sa[0] != sb[0]) return Integer.compare(sb[0], sa[0]);            // 1. wins desc
            int da = sa[2] - sa[3], db = sb[2] - sb[3];
            if (da != db) return Integer.compare(db, da);                        // 2. rackDiff desc
            return 0;                                                            // 3+ do resolveHeadToHead xử lý
        });
        resolveHeadToHead(ids, stat, matches);

        int advanceCount = (stage != null && stage.getPeActiveCount() != null && stage.getPeEliminateCount() != null)
                ? stage.getPeActiveCount() - stage.getPeEliminateCount() : -1;

        List<StandingsEntryResponse> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            int[] s = stat.get(id);
            Participant p = players.get(id);
            result.add(StandingsEntryResponse.builder()
                    .rank(i + 1)
                    .participantId(id)
                    .displayName(p != null ? p.getDisplayName() : "?")
                    .wins(s[0]).losses(s[1])
                    .matchesPlayed(s[0] + s[1])
                    .framesWon(s[2]).framesLost(s[3])
                    .frameDiff(s[2] - s[3])
                    .advancesToPlayoff(advanceCount < 0 ? null : (i < advanceCount))
                    .build());
        }
        return result;
    }

    /**
     * Phân định các nhóm còn hoà sau 2 tiêu chí đầu (số trận thắng, hiệu số ván).
     *
     * <p>Thứ tự trong nhóm: <b>đối đầu trực tiếp → số ván thắng → id</b>.
     * Đối đầu trực tiếp đứng trên số ván thắng theo điều lệ giải: khi hai cơ thủ ngang nhau
     * về thành tích chung thì kết quả gặp nhau trực tiếp có tiếng nói trước.
     *
     * <p>Nhóm hoà vòng (A thắng B, B thắng C, C thắng A) sẽ cùng số trận thắng đối đầu → tự động
     * rơi xuống tiêu chí kế tiếp. Bậc cuối dùng id nên kết quả luôn tất định.
     */
    private void resolveHeadToHead(List<Long> ids, Map<Long, int[]> stat, List<Match> matches) {
        int i = 0;
        while (i < ids.size()) {
            int j = i + 1;
            while (j < ids.size() && samePrimary(stat.get(ids.get(i)), stat.get(ids.get(j)))) j++;
            if (j - i > 1) {
                List<Long> group = new ArrayList<>(ids.subList(i, j));
                Set<Long> groupSet = new HashSet<>(group);
                Map<Long, Integer> h2h = headToHeadWins(matches, groupSet);
                group.sort((a, b) -> {
                    int ha = h2h.getOrDefault(a, 0), hb = h2h.getOrDefault(b, 0);
                    if (ha != hb) return Integer.compare(hb, ha);                // 3. đối đầu trực tiếp
                    int wa = stat.get(a)[2], wb = stat.get(b)[2];
                    if (wa != wb) return Integer.compare(wb, wa);                // 4. số ván thắng
                    return Long.compare(a, b);                                   // 5. chốt tất định
                });
                for (int k = i; k < j; k++) ids.set(k, group.get(k - i));
            }
            i = j;
        }
    }

    /**
     * Hai cơ thủ được coi là còn hoà (cần xét tiếp) khi bằng nhau ở 2 tiêu chí đầu.
     * KHÔNG tính số ván thắng ở đây — nó đã bị đẩy xuống sau đối đầu trực tiếp.
     */
    private boolean samePrimary(int[] a, int[] b) {
        return a[0] == b[0] && (a[2] - a[3]) == (b[2] - b[3]);
    }

    private Map<Long, Integer> headToHeadWins(List<Match> matches, Set<Long> groupIds) {
        Map<Long, Integer> h2h = new HashMap<>();
        groupIds.forEach(id -> h2h.put(id, 0));
        for (Match m : matches) {
            if (!isFinishedMatch(m) || m.getWinner() == null || m.getLoser() == null) continue;
            Long w = m.getWinner().getId(), l = m.getLoser().getId();
            if (groupIds.contains(w) && groupIds.contains(l)) h2h.merge(w, 1, Integer::sum);
        }
        return h2h;
    }

    private boolean isFinishedMatch(Match m) {
        return MatchStatus.COMPLETED.getValue().equals(m.getStatus())
                || MatchStatus.WALKOVER.getValue().equals(m.getStatus())
                || MatchStatus.BYE.getValue().equals(m.getStatus());
    }

    /* ═══════════════════════════════════════════════════════════
     *  CANCEL DRAW — DRAW_PREVIEW → REGISTRATION_CLOSED
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public void cancelDraw(Long tournamentId, Long actorUserId) {
        // Cùng khoá PESSIMISTIC_WRITE với generate(): "huỷ" và "bốc lại" bắn gần nhau phải xếp
        // hàng, không được xen nhau giữa lúc một bên đọc trạng thái và bên kia đang xoá.
        Tournament t = tournamentRepository.findByIdWithLock(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        assertActorCanAccessTournament(actorUserId, t);

        String currentStatus = t.getStatus();
        if (!TournamentStatus.DRAW_PREVIEW.getValue().equals(currentStatus)) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }

        clearExistingBracket(tournamentId);

        t.setStatus(TournamentStatus.REGISTRATION_CLOSED.getValue());
        tournamentRepository.save(t);
        tournamentAuditService.recordChange(t, currentStatus, TournamentStatus.REGISTRATION_CLOSED.getValue(),
                actorUserId, "Huỷ bốc thăm — xoá bracket nháp");
    }

    /* ═══════════════════════════════════════════════════════════
     *  CONFIRM DRAW — DRAW_PREVIEW → DRAW_DONE
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public void confirmDraw(Long tournamentId, Long actorUserId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        assertActorCanAccessTournament(actorUserId, t);
        if (!TournamentStatus.DRAW_PREVIEW.getValue().equals(t.getStatus())) throw new BusinessException(ErrorCode.INVALID_OPERATION);

        assertNoEmptyRound1Match(tournamentId);

        String previousStatus = t.getStatus();
        t.setStatus(TournamentStatus.DRAW_DONE.getValue());
        tournamentRepository.save(t);
        tournamentAuditService.recordChange(t, previousStatus, TournamentStatus.DRAW_DONE.getValue(),
                actorUserId, "Xác nhận bracket");

        Map<String, Object> variables = new HashMap<>(mailContextBuilder.systemContext());
        mailContextBuilder.putTournament(variables, t);
        eventPublisher.publishEvent(MailDomainEvent.builder()
                .eventType(EmailEventType.TOURNAMENT_DRAW_COMPLETED)
                .tournamentId(t.getId())
                .variables(variables)
                .entityKey("TOURNAMENT-DRAW-" + t.getId())
                .build());
    }

    /**
     * Lưới an toàn trước khi chốt bracket: không cho phép tồn tại trận vòng 1 mà cả 2 ô đều trống —
     * trận đó không ai đá được, cũng không phải BYE nên không tự phân định, khiến giải không bao giờ
     * kết thúc được.
     *
     * <p><b>Chỉ xét stage đã có người.</b> Nhiều stage cố ý để TRỐNG lúc bốc thăm và được điền sau:
     * nhánh thua của Loại kép (điền bằng người thua từ nhánh thắng), bracket Playoff của Vòng tròn
     * loại dần, các giai đoạn League phía sau. Nếu vòng 1 của một stage trống hoàn toàn thì đó là
     * placeholder hợp lệ → bỏ qua. Chỉ khi stage đã có người mà vẫn lẫn trận rỗng mới là bất thường.
     *
     * <p>Trận BYE (đúng 1 cơ thủ) luôn hợp lệ — giải có sĩ số không phải lũy thừa 2 buộc phải có BYE.
     */
    private void assertNoEmptyRound1Match(Long tournamentId) {
        Map<Long, List<Match>> round1ByStage = matchRepository
                .findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournamentId).stream()
                .filter(m -> m.getStage() != null && m.getRoundNo() != null && m.getRoundNo() == 1)
                .collect(Collectors.groupingBy(m -> m.getStage().getId()));

        for (List<Match> stageRound1 : round1ByStage.values()) {
            boolean stageHasPlayers = stageRound1.stream()
                    .anyMatch(m -> m.getPlayer1() != null || m.getPlayer2() != null);
            if (!stageHasPlayers) {
                continue; // placeholder chờ điền — hợp lệ
            }
            boolean hasEmptyMatch = stageRound1.stream()
                    .anyMatch(m -> m.getPlayer1() == null && m.getPlayer2() == null);
            if (hasEmptyMatch) {
                throw new BusinessException(ErrorCode.DRAW_BRACKET_HAS_EMPTY_MATCH);
            }
        }
    }

    /* ═══════════════════════════════════════════════════════════
     *  SWAP PLAYERS — rearrange R1 seeding in DRAW_PREVIEW
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public void swapPlayers(Long tournamentId, Long matchId1, String slot1, Long matchId2, String slot2) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!TournamentStatus.DRAW_PREVIEW.getValue().equals(t.getStatus())) throw new BusinessException(ErrorCode.INVALID_OPERATION);

        Match m1 = matchRepository.findByIdWithDetails(matchId1)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Match m2 = matchRepository.findByIdWithDetails(matchId2)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!m1.getTournament().getId().equals(tournamentId) ||
            !m2.getTournament().getId().equals(tournamentId))
            throw new BusinessException(ErrorCode.INVALID_OPERATION);

        if (m1.getRoundNo() != 1 || m2.getRoundNo() != 1)
            throw new BusinessException(ErrorCode.INVALID_OPERATION);

        String type1 = m1.getStage().getStageType();
        String type2 = m2.getStage().getStageType();
        if ("LOSERS".equals(type1) || "GRAND_FINAL".equals(type1) ||
            "LOSERS".equals(type2) || "GRAND_FINAL".equals(type2))
            throw new BusinessException(ErrorCode.INVALID_OPERATION);

        Participant p1 = getSlot(m1, slot1);
        Participant p2 = getSlot(m2, slot2);

        // Đổi chỗ 2 ô đều trống là vô nghĩa và chỉ tạo ra trận rỗng không ai đá được
        if (p1 == null && p2 == null) {
            throw new BusinessException(ErrorCode.DRAW_SWAP_BOTH_SLOTS_EMPTY);
        }

        setSlot(m1, slot1, p2);
        setSlot(m2, slot2, p1);
        updateByeStatus(m1);
        updateByeStatus(m2);
        matchRepository.save(m1);
        matchRepository.save(m2);
        refreshBracketFromR1(tournamentId);
    }

    /* ═══════════════════════════════════════════════════════════
     *  Read-only helpers
     * ═══════════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<StageWithMatchesResponse> getStagesWithMatches(Long tournamentId) {
        return stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId).stream()
                .map(s -> StageWithMatchesResponse.builder()
                        .id(s.getId()).tournamentId(tournamentId)
                        .name(s.getName()).stageType(s.getStageType())
                        .orderNo(s.getOrderNo()).status(s.getStatus())
                        .peRoundNo(s.getPeRoundNo()).peActiveCount(s.getPeActiveCount())
                        .peEliminateCount(s.getPeEliminateCount())
                        .matches(matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(s.getId())
                                .stream().map(this::toMatchResponse).toList())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getMatchesForTournament(Long tournamentId) {
        return matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournamentId)
                .stream().map(this::toMatchResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getMatchesForPlayer(Long userId) {
        List<Participant> myPtcp = participantRepository.findByRegistrationUserId(userId);
        return myPtcp.stream()
                .flatMap(p -> matchRepository.findByParticipantId(p.getId()).stream())
                .distinct().map(this::toMatchResponse).toList();
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatchResponseById(Long matchId) {
        Match m = matchRepository.findByIdWithDetails(matchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return toMatchResponse(m);
    }

    public MatchResponse toMatchResponse(Match m) {
        Long stageId = null; String stageName = null; String stageType = null;
        try { stageId = m.getStage().getId(); stageName = m.getStage().getName(); stageType = m.getStage().getStageType(); }
        catch (Exception ignored) {}
        String tournamentName = null;
        try { tournamentName = m.getTournament().getName(); }
        catch (Exception ignored) {}
        return MatchResponse.builder()
                .id(m.getId()).matchCode(m.getMatchCode())
                .tournamentId(m.getTournament().getId())
                .tournamentName(tournamentName)
                .stageId(stageId).stageName(stageName).stageType(stageType)
                .bracketType(m.getBracketType())
                .roundNo(m.getRoundNo()).positionNo(m.getPositionNo())
                .raceTo(m.getRaceTo()).status(m.getStatus()).isBye(m.getIsBye())
                .scheduledAt(m.getScheduledAt())
                .estimatedEndAt(m.getEstimatedEndAt())
                .scheduleLocked(m.getScheduleLocked())
                .player1(brief(m.getPlayer1())).player2(brief(m.getPlayer2()))
                .player1Score(m.getPlayer1Score()).player2Score(m.getPlayer2Score())
                .winner(brief(m.getWinner())).loser(brief(m.getLoser()))
                .nextMatchWinId(m.getNextMatchWin()  != null ? m.getNextMatchWin().getId()  : null)
                .nextMatchLoseId(m.getNextMatchLose() != null ? m.getNextMatchLose().getId() : null)
                .winSlot(m.getWinSlot()).loseSlot(m.getLoseSlot())
                .tableNo(m.getTableNo())
                .tableId(m.getTable() != null ? m.getTable().getId() : null)
                .tableName(m.getTable() != null ? m.getTable().getName() : null)
                .tableNumber(m.getTable() != null ? m.getTable().getTableNumber() : null)
                .assignedStaff(staffBrief(m.getAssignedStaff()))
                .build();
    }

    private StaffBriefResponse staffBrief(User u) {
        if (u == null) return null;
        String name = null;
        try {
            if (u.getProfile() != null) {
                if (u.getProfile().getDisplayName() != null && !u.getProfile().getDisplayName().isBlank()) {
                    name = u.getProfile().getDisplayName();
                } else if (u.getProfile().getFullName() != null && !u.getProfile().getFullName().isBlank()) {
                    name = u.getProfile().getFullName();
                }
            }
        } catch (Exception ignored) {}
        if (name == null) name = u.getEmail();
        return StaffBriefResponse.builder().id(u.getId()).displayName(name).build();
    }

    private ParticipantBriefResponse brief(Participant p) {
        if (p == null) return null;
        return ParticipantBriefResponse.builder()
                .id(p.getId()).displayName(p.getDisplayName())
                .avatarUrl(AvatarUrlResolver.resolveForList(
                        p.getAvtarUrl(), minioStorageService, minioProperties.getBucket()))
                .build();
    }

    /* ═══════════════════════════════════════════════════════════
     *  Private bracket helpers
     * ═══════════════════════════════════════════════════════════ */

    /** Losers bracket match count for round lr:  B >> (⌈lr/2⌉ + 1) */
    private int losersMatchCount(int bracketSize, int lr) {
        return Math.max(1, bracketSize >> (((lr + 1) / 2) + 1));
    }

    private void clearExistingBracket(Long tournamentId) {
        List<Match> allMatches = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournamentId);
        if (allMatches.isEmpty()) return;

        scoreEventRepository.deleteByMatchIdIn(allMatches.stream().map(Match::getId).collect(Collectors.toList()));

        for (Match m : allMatches) {
            m.setNextMatchWin(null); m.setNextMatchLose(null);
            m.setPlayer1(null); m.setPlayer2(null); m.setWinner(null); m.setLoser(null);
        }
        matchRepository.saveAll(allMatches);
        matchRepository.flush();
        matchRepository.deleteAll(allMatches);
        matchRepository.flush();

        stageRepository.deleteAll(stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId));
        stageRepository.flush();
    }

    private void refreshBracketFromR1(Long tournamentId) {
        List<Match> all = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournamentId);
        List<Match> higher = all.stream().filter(m -> m.getRoundNo() > 1).collect(Collectors.toList());
        higher.forEach(m -> { m.setPlayer1(null); m.setPlayer2(null); });
        matchRepository.saveAll(higher);
        matchRepository.flush();
        all.stream()
           .filter(m -> m.getRoundNo() == 1 && Boolean.TRUE.equals(m.getIsBye()) && m.getPlayer1() != null)
           .forEach(m -> placeParticipantInMatch(m.getNextMatchWin(), m.getWinSlot(), m.getPlayer1()));
    }

    private void placeParticipantInMatch(Match next, String slot, Participant p) {
        if (next == null || p == null) return;
        Match r = matchRepository.findById(next.getId()).orElse(next);
        if ("player1".equals(slot)) r.setPlayer1(p); else r.setPlayer2(p);
        matchRepository.save(r);
    }

    /**
     * Sắp participants theo "seed rank" (index 0 = hạng 1 — mạnh nhất, được ưu tiên BYE khi có).
     *
     * <ul>
     *   <li><b>RANK</b> — xếp theo {@link BilliardRank} của cơ thủ, xem {@link #resolveRankOrder}.</li>
     *   <li><b>SEED</b> — xếp theo {@code seedNo} BQT tự nhập, xem {@link #resolveSeedNoOrder}.</li>
     *   <li><b>RANDOM</b> (và mọi giá trị lạ) — xáo toàn bộ, bốc thăm thuần.</li>
     * </ul>
     */
    private List<Participant> resolveSeedRankOrder(String seedingMethod, List<Participant> participants) {
        if (SeedingMethod.RANK.name().equals(seedingMethod)) {
            return resolveRankOrder(participants);
        }
        if (SeedingMethod.SEED.name().equals(seedingMethod)) {
            return resolveSeedNoOrder(participants);
        }
        List<Participant> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);
        return shuffled;
    }

    /**
     * Xếp thứ tự hạt giống theo {@code seedNo} BQT tự nhập (1 = mạnh nhất, tăng dần).
     *
     * <p>Cho phép seed MỘT PHẦN: người có seedNo xếp trước theo đúng số, người KHÔNG có seedNo bị
     * xáo ngẫu nhiên và xếp SAU toàn bộ nhóm đã seed — giống hệt cách nhóm chưa xếp hạng được xử lý
     * ở {@link #resolveRankOrder}, xem {@link #appendUnrankedAfterRanked} để biết vì sao không rải
     * ngẫu nhiên xen kẽ (sẽ phá vỡ việc tách nhánh của các hạt giống đầu).
     */
    /**
     * Chặn bốc thăm nếu nhóm đã gán {@code seedNo} không liên tục 1..K (K = số người đã seed).
     *
     * <p><b>Vì sao bắt buộc liên tục:</b> {@link #resolveSeedNoOrder} chỉ dùng seedNo để SẮP THỨ
     * TỰ (index trong danh sách sau khi sort), KHÔNG dùng trực tiếp giá trị seedNo làm rank —
     * nên về mặt kỹ thuật seed {1, 7} vẫn chạy được (tương đương seed {1, 2}), không sinh lỗi hệ
     * thống. Nhưng để trống ở giữa (VD gán 1 và 7, bỏ trống 2-6) gần như luôn là BQT nhập nhầm/
     * nhập dở dang — im lặng chấp nhận sẽ khiến hạt giống 7 bị xử lý như hạt giống 2 (mạnh hơn
     * thực tế rất nhiều), sai lệch nghiêm trọng so với ý định. Chặn cứng ở bước bốc thăm thay vì
     * lúc gán để Owner vẫn seed dần dần trong lúc build roster mà không bị chặn giữa chừng.
     */
    private void validateSeedNoContiguous(List<Participant> participants) {
        Set<Integer> seedNos = participants.stream()
                .map(Participant::getSeedNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        if (seedNos.isEmpty()) {
            return; // Chưa ai được seed — coi như bốc thăm ngẫu nhiên hoàn toàn, không lỗi.
        }
        List<Integer> missing = new ArrayList<>();
        for (int i = 1; i <= seedNos.size(); i++) {
            if (!seedNos.contains(i)) {
                missing.add(i);
            }
        }
        if (!missing.isEmpty()) {
            String missingStr = missing.stream().map(String::valueOf).collect(Collectors.joining(", "));
            String assignedStr = seedNos.stream().map(String::valueOf).collect(Collectors.joining(", "));
            throw new BusinessException(ErrorCode.PARTICIPANT_SEED_NOT_CONTIGUOUS,
                    "Số hạt giống đang gán không liên tục — thiếu số: " + missingStr
                            + " (đã gán: " + assignedStr + "). Hãy gán đủ từ 1 đến " + seedNos.size()
                            + ", hoặc sửa/xoá bớt hạt giống cho khớp.");
        }
    }

    private List<Participant> resolveSeedNoOrder(List<Participant> participants) {
        List<Participant> seeded = participants.stream()
                .filter(p -> p.getSeedNo() != null)
                .sorted(Comparator.comparingInt(Participant::getSeedNo))
                .collect(Collectors.toCollection(ArrayList::new));
        List<Participant> unseeded = participants.stream()
                .filter(p -> p.getSeedNo() == null)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(unseeded);
        return appendUnrankedAfterRanked(seeded, unseeded);
    }

    /**
     * Xếp thứ tự hạt giống theo hạng cơ thủ (hệ bi-a Việt Nam: CN → A → … → L).
     *
     * <ol>
     *   <li>Người ĐÃ có hạng: sắp theo bậc hạng, <b>xáo ngẫu nhiên trong cùng một bậc</b> — trùng
     *       hạng là chuyện bình thường nên không có tiêu chí phụ, cùng hạng thì bốc thăm.
     *       Muốn can thiệp tay thì dùng chức năng đổi chỗ ở màn Bốc thăm.</li>
     *   <li>Người CHƯA có hạng: xáo ngẫu nhiên rồi xếp sau toàn bộ nhóm có hạng, để nhóm có hạng
     *       giữ trọn các hạt giống đầu — xem {@link #appendUnrankedAfterRanked}.</li>
     * </ol>
     */
    private List<Participant> resolveRankOrder(List<Participant> participants) {
        List<Participant> ranked = new ArrayList<>();
        List<Participant> unranked = new ArrayList<>();
        for (Participant p : participants) {
            if (BilliardRank.fromNullable(p.getBilliardRank()).isRanked()) {
                ranked.add(p);
            } else {
                unranked.add(p);
            }
        }

        // Xáo trước rồi mới sort ổn định theo bậc → trong cùng bậc thứ tự là ngẫu nhiên
        Collections.shuffle(ranked);
        ranked.sort(Comparator.comparingInt(p -> BilliardRank.fromNullable(p.getBilliardRank()).ordinal()));

        Collections.shuffle(unranked);
        return appendUnrankedAfterRanked(ranked, unranked);
    }

    /**
     * Ghép nhóm chưa xếp hạng vào sau danh sách đã sắp theo hạng.
     *
     * <p>Nhóm CÓ hạng giữ trọn các hạt giống đầu theo đúng bậc; nhóm CHƯA xếp hạng (đã xáo ngẫu
     * nhiên từ trước) xếp sau.
     *
     * <p><b>Vì sao không rải ngẫu nhiên nhóm chưa xếp hạng ra toàn bảng:</b> {@code standardSeedOrder}
     * ghép vị trí 1 với vị trí n, 2 với n-1... nên nó chỉ tách được các cơ thủ mạnh khi họ thực sự
     * nằm ở các hạt giống ĐẦU. Nếu người chưa xếp hạng chiếm mất vị trí đầu, cơ thủ hạng cao bị đẩy
     * xuống giữa bảng và có thể gặp nhau ngay vòng 1 — đã tái hiện được: một giải 16 người (2 CN,
     * 2 A, 4 B, 8 chưa xếp hạng) cho ra cặp CN vs A ở vòng 1. Rải ngẫu nhiên và bảo toàn việc tách
     * nhánh là hai mục tiêu xung đột; ở đây ưu tiên tách nhánh.
     */
    private List<Participant> appendUnrankedAfterRanked(List<Participant> ranked, List<Participant> unranked) {
        List<Participant> merged = new ArrayList<>(ranked.size() + unranked.size());
        merged.addAll(ranked);
        merged.addAll(unranked);
        return merged;
    }

    /**
     * Thứ tự seeding chuẩn cho 1 nhánh {@code size} slot (lũy thừa 2) — thuật toán đệ quy dùng ở
     * mọi giải đấu có hạt giống thật (tennis, NCAA...): đảm bảo seed 1 & 2 luôn ở 2 nửa nhánh khác
     * nhau, seed 1-4 ở 4 phần tư khác nhau, v.v. Trả về danh sách 1..size theo đúng thứ tự slot
     * (2 phần tử liên tiếp = 1 cặp đấu vòng 1).
     */
    private static List<Integer> standardSeedOrder(int size) {
        if (size == 1) return new ArrayList<>(List.of(1));
        List<Integer> prev = standardSeedOrder(size / 2);
        List<Integer> result = new ArrayList<>(size);
        for (int s : prev) {
            result.add(s);
            result.add(size + 1 - s);
        }
        return result;
    }

    /**
     * Xếp {@code participants} (đã ở seed rank order — xem {@link #resolveSeedRankOrder}) vào vòng 1
     * theo {@link #standardSeedOrder}. Rank > số người thực (do bracketSize > n) là slot "ảo" → BYE
     * cho đối thủ còn lại, và BYE luôn rơi vào seed cao nhất trước — đúng quy ước giải đấu thật.
     */
    private void assignSeededRound1(Match[] r1Grid, List<Participant> participants, int bracketSize) {
        int n = participants.size();
        List<Integer> order = standardSeedOrder(bracketSize);
        for (int slot = 0; slot < bracketSize; slot += 2) {
            int pos = (slot / 2) + 1;
            int rankP1 = order.get(slot);
            int rankP2 = order.get(slot + 1);
            Participant p1 = rankP1 <= n ? participants.get(rankP1 - 1) : null;
            Participant p2 = rankP2 <= n ? participants.get(rankP2 - 1) : null;

            Match m = r1Grid[pos];
            m.setPlayer1(p1);
            m.setPlayer2(p2);
            Participant byeWinner = (p1 != null && p2 == null) ? p1 : (p2 != null && p1 == null) ? p2 : null;
            if (byeWinner != null) {
                m.setIsBye(true);
                m.setStatus(MatchStatus.BYE.getValue());
                m.setWinner(byeWinner);
            }
            matchRepository.save(m);
            if (byeWinner != null) {
                placeParticipantInMatch(m.getNextMatchWin(), m.getWinSlot(), byeWinner);
            }
        }
    }

    /**
     * Kiểm tra tường minh tại tầng service thay vì coi mọi giá trị khác {@code "player1"} là
     * {@code "player2"} — hiện tại {@link com.capstone.su26_sep490_g2_be.dto.request.SwapPlayersRequest}
     * đã chặn giá trị rác bằng {@code @Pattern}, nhưng service không nên phụ thuộc hoàn toàn vào một
     * validation ở tầng DTO; một caller khác trong tương lai bỏ qua DTO đó vẫn phải được chặn ở đây.
     */
    private void assertValidSlot(String slot) {
        if (!"player1".equals(slot) && !"player2".equals(slot)) {
            throw new BusinessException(ErrorCode.DRAW_SWAP_INVALID_SLOT);
        }
    }

    private Participant getSlot(Match m, String slot) {
        assertValidSlot(slot);
        return "player1".equals(slot) ? m.getPlayer1() : m.getPlayer2();
    }

    private void setSlot(Match m, String slot, Participant p) {
        assertValidSlot(slot);
        if ("player1".equals(slot)) m.setPlayer1(p); else m.setPlayer2(p);
    }

    /**
     * Tính lại trạng thái BYE sau khi {@link #swapPlayers} đổi chỗ — nhận diện BYE ở CẢ HAI phía
     * giống {@link #assignSeededRound1}, không chỉ khi người nằm ở {@code player1}.
     *
     * <p><b>Chuẩn hoá:</b> nếu trận chỉ còn 1 người thì luôn dồn về {@code player1}. Toàn bộ code
     * phía sau đều ngầm dựa vào bất biến "trận BYE có người ở player1, ô trống là player2" —
     * {@link #refreshBracketFromR1} chỉ đẩy {@code m.getPlayer1()} lên vòng sau, và FE cũng khoá ô
     * trống dựa trên {@code slot === "player2"}. Để người còn lại nằm ở {@code player2} sẽ khiến
     * cơ thủ đó mất suất miễn trận và trận kẹt vĩnh viễn ở PENDING.
     */
    private void updateByeStatus(Match m) {
        Participant p1 = m.getPlayer1();
        Participant p2 = m.getPlayer2();

        if (p1 == null && p2 != null) {
            m.setPlayer1(p2);
            m.setPlayer2(null);
            p1 = p2;
            p2 = null;
        }

        if (p1 != null && p2 == null) {
            m.setIsBye(true); m.setStatus(MatchStatus.BYE.getValue()); m.setWinner(p1);
        } else {
            m.setIsBye(false); m.setStatus(MatchStatus.PENDING.getValue()); m.setWinner(null);
        }
    }

    /* ═══════════════════════════════════════════════════════════
     *  Math / Config helpers
     * ═══════════════════════════════════════════════════════════ */

    private int nextPowerOf2(int n) { int p = 1; while (p < n) p <<= 1; return p; }
    private int log2(int n) { return 31 - Integer.numberOfLeadingZeros(n); }

    private String resolveRoundKey(int round, int totalRounds, boolean isLosers) {
        return switch (totalRounds - round) {
            case 0 -> "final";
            case 1 -> "semi_final";
            case 2 -> "quarter_final";
            default -> "round_1";
        };
    }

    /**
     * Round-key cho nhánh thắng DOUBLE_ELIMINATION — tính theo khoảng cách tới vòng cuối (giống
     * {@link #resolveRoundKey}) thay vì đánh số cứng "winners_r{n}". Seed data chỉ có
     * winners_r1/winners_qf/winners_sf/winners_final nên trước đây mọi vòng ngoài winners_r1 đều
     * tra không ra và âm thầm rơi về race-to=7 hardcode — xem {@code safeResolveRaceTo}.
     */
    private String resolveWinnersRoundKey(int wr, int wTotalRounds) {
        return DoubleEliminationBracketMath.resolveWinnersRoundKey(wr, wTotalRounds);
    }

    /**
     * Round-key cho nhánh thua DOUBLE_ELIMINATION — seed data chỉ có losers_r1/r2/r3/losers_final
     * (đủ cho bracket ≤ 8 người); với bracket lớn hơn (lTotalRounds > 4), các vòng giữa dư ra được
     * gộp về losers_r3 thay vì đánh số losers_r4/r5... (không tồn tại trong seed data → trước đây
     * âm thầm rơi về race-to=7 hardcode).
     */
    private String resolveLosersRoundKey(int lr, int lTotalRounds) {
        return DoubleEliminationBracketMath.resolveLosersRoundKey(lr, lTotalRounds);
    }

    private int safeResolveRaceTo(Long tid, String format, String roundKey) {
        try { return raceToRuleService.resolveRaceTo(tid, format, roundKey); }
        catch (Exception e) {
            try { return raceToRuleService.resolveRaceTo(tid, format, "round_1"); }
            catch (Exception e2) { return 7; }
        }
    }

    private boolean resolveThirdPlaceEnabled(Long tid, String format) {
        // Đọc config third_place_match (mặc định bật nếu chưa cấu hình)
        return Boolean.parseBoolean(readStringConfig(tid, "third_place_match", "true"));
    }

    private int readIntConfig(Long tournamentId, String key, int defaultVal) {
        return configValueRepository.findByIdTournamentIdAndIdFieldKey(tournamentId, key)
                .map(cv -> { try { return Integer.parseInt(cv.getValue()); } catch (Exception e) { return defaultVal; } })
                .orElse(defaultVal);
    }

    /**
     * Parse cấu hình {@code pe_survivors_per_stage} — bọc lỗi định dạng CSV thành BusinessException
     * thay vì để {@link IllegalArgumentException} thô rơi xuống handler chung (500 không rõ nghĩa).
     */
    private List<Integer> parseProgressiveSurvivors(String csv) {
        try {
            return ProgressiveSurvivorsUtil.parse(csv);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PROGRESSIVE_CONFIG_INVALID, e.getMessage());
        }
    }

    /**
     * Đối chiếu {@code pe_survivors_per_stage} với SỐ NGƯỜI THỰC TẾ đăng ký — chặn ngay lúc bốc
     * thăm nếu cấu hình (vd mặc định "10,6,4", thiết kế cho giải ~16 người) không khớp headcount
     * thật (vd chỉ 5 người đăng ký). Không chặn ở đây thì lỗi chỉ lộ ra giữa giải, khi
     * advanceProgressiveStage/fillRoundRobinPlayers cố điền người thật vào nhiều ô placeholder hơn
     * số người thật đang có → IndexOutOfBoundsException.
     */
    private void validateProgressiveSurvivorsAgainstHeadcount(List<Integer> survivors, int actualHeadcount) {
        int playoffSize = survivors.get(survivors.size() - 1);
        List<String> errors = ProgressiveSurvivorsUtil.validate(survivors, actualHeadcount, playoffSize);
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.PROGRESSIVE_CONFIG_INVALID, String.join("; ", errors));
        }
    }

    /* ═══════════════════════════════════════════════════════════
     *  CUT_TO_SE — Double Elimination với cutoff → Single Elimination
     *
     *  Matchroom-style: tất cả N người chơi trong DE (2 mạng).
     *  Sau cutoffRound vòng của W bracket, còn seSize người tồn tại
     *  (seSize/2 từ W + seSize/2 từ L) → thi đấu loại trực tiếp (Last X).
     *
     *  Công thức:
     *    cutoffRound = log2(bracketSize / seSize) + 1
     *    lCutoffRounds = 2 * (cutoffRound - 1)
     *    W survivors = bracketSize / 2^cutoffRound = seSize/2
     *    L survivors = bracketSize / 2^cutoffRound = seSize/2
     *    Total → SE = seSize ✓
     *
     *  Seeding vào SE:
     *    SE-M(k): P1 = W survivor rank(k), P2 = L survivor rank(seSize/2 - k + 1)
     *    → W1 vs L(last), W2 vs L(second-to-last)...
     *    → W bracket survivors KHÔNG gặp nhau cho đến SF/Final
     * ═══════════════════════════════════════════════════════════ */

    private BracketResult generateCutToSEDE(Tournament t, List<Participant> participants) {
        int n          = participants.size();
        int bracketSize = nextPowerOf2(n);

        int seSize = nextPowerOf2(Math.max(2, readIntConfig(t.getId(), "se_phase_size", 8)));

        // Kẹp seSize vào khoảng hợp lệ thay vì từ chối config — luôn phải còn ít nhất 1 vòng W
        // trước khi cắt (seSize <= bracketSize/2), nên số người cấu hình lớn hơn thực tế (vd default
        // cho giải nhỏ) không làm bốc thăm thất bại, chỉ tự động thu hẹp về mức khả thi lớn nhất.
        int maxValidSeSize = Math.max(2, bracketSize / 2);
        if (seSize > maxValidSeSize) {
            log.info("CUT_TO_SE: se_phase_size={} vượt quá bracketSize={}, kẹp về {}",
                    seSize, bracketSize, maxValidSeSize);
            seSize = maxValidSeSize;
        }

        // cutoffRound: số vòng W bracket trước khi chuyển sang SE
        // seSize = bracketSize / 2^(cutoffRound-1)  →  cutoffRound = log2(bracketSize/seSize)+1
        int cutoffRound = log2(bracketSize / seSize) + 1;
        int lCutoffRounds = 2 * (cutoffRound - 1); // số vòng L bracket

        // Nhãn vòng đấu (winners_qf/sf/final, losers_r1/r2/r3/final) PHẢI tính theo tổng số vòng
        // TỰ NHIÊN của bracketSize đầy đủ, KHÔNG phải theo cutoffRound/lCutoffRounds (số vòng thật
        // sự được sinh ra sau khi cắt sớm) — nếu không, cắt sớm (se_phase_size nhỏ) sẽ luôn gọi vòng
        // áp chót là "Bán kết"/"NTh Chung kết nhánh" dù vòng đó có tới 4+ trận (thực chất là Tứ kết/
        // Vòng 2), vì resolveWinnersRoundKey/resolveLosersRoundKey chỉ đếm "còn cách chung kết mấy
        // vòng" chứ không biết kích thước bracket gốc.
        int wNaturalRounds = log2(bracketSize);
        int lNaturalRounds = 2 * (wNaturalRounds - 1);

        // ── Stages ───────────────────────────────────────────────────
        TournamentStage wStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Nhánh thắng").stageType("WINNERS")
                .orderNo(1).status(TournamentStageStatus.PENDING.getValue()).build());

        TournamentStage lStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Nhánh thua").stageType("LOSERS")
                .orderNo(2).status(TournamentStageStatus.PENDING.getValue()).build());

        TournamentStage seStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Last " + seSize + " — Loại trực tiếp").stageType("FINAL_BRACKET")
                .orderNo(3).status(TournamentStageStatus.PENDING.getValue()).build());

        // ── W Bracket (rounds 1..cutoffRound) ───────────────────────
        int maxWPos = (bracketSize >> 1) + 2;
        Match[][] wGrid = new Match[cutoffRound + 1][maxWPos];

        for (int wr = 1; wr <= cutoffRound; wr++) {
            int mc = bracketSize >> wr;
            for (int pos = 1; pos <= mc; pos++) {
                wGrid[wr][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(wStage).bracketType("WINNERS")
                        .roundNo(wr).positionNo(pos)
                        .matchCode("W-R%d-M%d".formatted(wr, pos))
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), resolveWinnersRoundKey(wr, wNaturalRounds)))
                        .status(MatchStatus.PENDING.getValue()).isBye(false).player1Score(0).player2Score(0).build());
            }
        }
        // W win-advancement (rounds 1..cutoffRound-1, last round survivors → SE)
        for (int wr = 1; wr < cutoffRound; wr++) {
            int mc = bracketSize >> wr;
            for (int pos = 1; pos <= mc; pos++) {
                int pp   = (pos + 1) / 2;
                String s = (pos % 2 == 1) ? "player1" : "player2";
                wGrid[wr][pos].setNextMatchWin(wGrid[wr + 1][pp]);
                wGrid[wr][pos].setWinSlot(s);
                matchRepository.save(wGrid[wr][pos]);
            }
        }

        // ── L Bracket (rounds 1..lCutoffRounds) ─────────────────────
        int maxLPos = Math.max(1, bracketSize >> 2) + 2;
        Match[][] lGrid = new Match[lCutoffRounds + 1][maxLPos];

        for (int lr = 1; lr <= lCutoffRounds; lr++) {
            int mc  = losersMatchCount(bracketSize, lr);
            String rk = resolveLosersRoundKey(lr, lNaturalRounds);
            for (int pos = 1; pos <= mc; pos++) {
                lGrid[lr][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(lStage).bracketType("LOSERS")
                        .roundNo(lr).positionNo(pos)
                        .matchCode("L-R%d-M%d".formatted(lr, pos))
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), rk))
                        .status(MatchStatus.PENDING.getValue()).isBye(false).player1Score(0).player2Score(0).build());
            }
        }
        // L win-advancement (same parity logic as FULL_DE)
        for (int lr = 1; lr < lCutoffRounds; lr++) {
            int mc = losersMatchCount(bracketSize, lr);
            for (int pos = 1; pos <= mc; pos++) {
                if (lr % 2 == 1) {
                    // Odd → Even: same position, player1 (P2 = W drop)
                    lGrid[lr][pos].setNextMatchWin(lGrid[lr + 1][pos]);
                    lGrid[lr][pos].setWinSlot("player1");
                } else {
                    // Even → Odd: compress ⌈pos/2⌉
                    int pp   = (pos + 1) / 2;
                    String s = (pos % 2 == 1) ? "player1" : "player2";
                    lGrid[lr][pos].setNextMatchWin(lGrid[lr + 1][pp]);
                    lGrid[lr][pos].setWinSlot(s);
                }
                matchRepository.save(lGrid[lr][pos]);
            }
        }
        // L-R(lCutoffRounds) winners → SE (populated by populateFinalBracket, no link now)

        // ── SE Bracket — blank, populated later ─────────────────────
        int seTotalRounds = log2(seSize);
        int maxSEPos      = (seSize >> 1) + 2;
        Match[][] seGrid  = new Match[seTotalRounds + 1][maxSEPos];

        for (int sr = 1; sr <= seTotalRounds; sr++) {
            int mc  = seSize >> sr;
            String rk = resolveSeRoundKey(sr, seTotalRounds);
            for (int pos = 1; pos <= mc; pos++) {
                seGrid[sr][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(seStage).bracketType("KNOCKOUT")
                        .roundNo(sr).positionNo(pos)
                        .matchCode("SE-R%d-M%d".formatted(sr, pos))
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), rk))
                        .status(MatchStatus.PENDING.getValue()).isBye(false).player1Score(0).player2Score(0).build());
            }
        }
        // SE win-advancement
        for (int sr = 1; sr < seTotalRounds; sr++) {
            int mc = seSize >> sr;
            for (int pos = 1; pos <= mc; pos++) {
                int pp   = (pos + 1) / 2;
                String s = (pos % 2 == 1) ? "player1" : "player2";
                seGrid[sr][pos].setNextMatchWin(seGrid[sr + 1][pp]);
                seGrid[sr][pos].setWinSlot(s);
                matchRepository.save(seGrid[sr][pos]);
            }
        }

        // ── Assign participants to W-R1 (seeding chuẩn — xem assignSeededRound1) ──
        assignSeededRound1(wGrid[1], participants, bracketSize);

        // ── W → L drops (after participant assignment, skip BYEs) ────
        // W-R1 (bracketSize/2 matches) → L-R1 (bracketSize/4 matches)
        // 2 W-R1 losers per L-R1 match: (2k-1)→P1, (2k)→P2
        int lr1Mc = losersMatchCount(bracketSize, 1);
        for (int lPos = 1; lPos <= lr1Mc; lPos++) {
            int wP1 = 2 * lPos - 1, wP2 = 2 * lPos;
            wireWtoL(wGrid, 1, wP1, lGrid, 1, lPos, "player1");
            wireWtoL(wGrid, 1, wP2, lGrid, 1, lPos, "player2");
        }
        // W-R(wr≥2) M(pos) loser → L-R(2*(wr-1)) M(pos) as player2
        for (int wr = 2; wr <= cutoffRound; wr++) {
            int lRound = 2 * (wr - 1);
            int mc     = bracketSize >> wr;
            for (int pos = 1; pos <= mc; pos++) {
                wireWtoL(wGrid, wr, pos, lGrid, lRound, pos, "player2");
            }
        }

        // Collect
        List<TournamentStage> stages = List.of(wStage, lStage, seStage);
        List<Match> allMatches = new ArrayList<>();
        stages.forEach(s -> allMatches.addAll(
                matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(s.getId())));
        return new BracketResult(stages, allMatches);
    }

    /* ═══════════════════════════════════════════════════════════
     *  POPULATE FINAL BRACKET
     *
     *  Gọi sau khi tất cả DE rounds (W cutoff + L cutoff) hoàn thành.
     *  Điền 32 W survivors + 32 L survivors vào SE-R1 theo snake seeding:
     *    SE-M(k): P1=W[k], P2=L[seSize/2 - k - 1]
     *    → W1 vs L(last), W2 vs L(second-to-last)...
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public void populateFinalBracket(Long tournamentId, Long actorUserId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        assertActorCanAccessTournament(actorUserId, t);
        if (!TournamentStatus.DRAW_DONE.getValue().equals(t.getStatus())) throw new BusinessException(ErrorCode.INVALID_OPERATION);

        List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);

        TournamentStage wStage = stages.stream().filter(s -> "WINNERS".equals(s.getStageType()))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OPERATION));
        TournamentStage lStage = stages.stream().filter(s -> "LOSERS".equals(s.getStageType()))
                .findFirst().orElse(null);
        TournamentStage seStage = stages.stream().filter(s -> "FINAL_BRACKET".equals(s.getStageType()))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OPERATION));

        // Xác định vòng cuối của W và L bracket
        List<Match> wMatches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(wStage.getId());
        int wLastRound = wMatches.stream().mapToInt(Match::getRoundNo).max().orElse(0);

        List<Match> wFinal = wMatches.stream()
                .filter(m -> m.getRoundNo() == wLastRound
                          && !MatchStatus.BYE.getValue().equals(m.getStatus()))
                .sorted(Comparator.comparing(Match::getPositionNo)).toList();

        boolean wDone = wFinal.stream()
                .allMatch(m -> MatchStatus.COMPLETED.getValue().equals(m.getStatus())
                        || MatchStatus.WALKOVER.getValue().equals(m.getStatus()));
        if (!wDone) throw new BusinessException(ErrorCode.INVALID_OPERATION);

        List<Participant> wSurvivors = wFinal.stream()
                .map(Match::getWinner).filter(Objects::nonNull).collect(Collectors.toList());

        List<Participant> lSurvivors = new ArrayList<>();
        if (lStage != null) {
            List<Match> lMatches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(lStage.getId());
            int lLastRound = lMatches.stream().mapToInt(Match::getRoundNo).max().orElse(0);
            List<Match> lFinal = lMatches.stream()
                    .filter(m -> m.getRoundNo() == lLastRound && !MatchStatus.BYE.getValue().equals(m.getStatus()))
                    .sorted(Comparator.comparing(Match::getPositionNo)).toList();
            boolean lDone = lFinal.stream()
                    .allMatch(m -> MatchStatus.COMPLETED.getValue().equals(m.getStatus())
                        || MatchStatus.WALKOVER.getValue().equals(m.getStatus()));
            if (!lDone) throw new BusinessException(ErrorCode.INVALID_OPERATION);
            lSurvivors = lFinal.stream()
                    .map(Match::getWinner).filter(Objects::nonNull).collect(Collectors.toList());
        }

        // SE R1 matches (đang blank)
        List<Match> seR1 = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(seStage.getId())
                .stream().filter(m -> m.getRoundNo() == 1)
                .sorted(Comparator.comparing(Match::getPositionNo)).toList();

        int half = seR1.size(); // = seSize/2
        for (int k = 0; k < half; k++) {
            Match m = seR1.get(k);
            if (k < wSurvivors.size())                 m.setPlayer1(wSurvivors.get(k));
            if ((half - 1 - k) < lSurvivors.size())   m.setPlayer2(lSurvivors.get(half - 1 - k));
            // Xử lý BYE nếu một bên không có người (edge case)
            if (m.getPlayer1() != null && m.getPlayer2() == null) {
                m.setIsBye(true); m.setStatus(MatchStatus.BYE.getValue()); m.setWinner(m.getPlayer1());
                placeParticipantInMatch(m.getNextMatchWin(), m.getWinSlot(), m.getPlayer1());
            }
            matchRepository.save(m);
        }

        // Đánh dấu DE stages hoàn thành
        wStage.setStatus(TournamentStageStatus.COMPLETED.getValue()); stageRepository.save(wStage);
        if (lStage != null) { lStage.setStatus(TournamentStageStatus.COMPLETED.getValue()); stageRepository.save(lStage); }

        String previousStatus = t.getStatus();
        t.setStatus(TournamentStatus.FINAL_BRACKET_READY.getValue());
        tournamentRepository.save(t);
        tournamentAuditService.recordChange(t, previousStatus, TournamentStatus.FINAL_BRACKET_READY.getValue(),
                actorUserId, "Điền bracket loại trực tiếp (CUT_TO_SE)");
    }

    /* ═══════════════════════════════════════════════════════════
     *  CUT_TO_SE Helpers
     * ═══════════════════════════════════════════════════════════ */

    /** SE round key dùng để lookup race-to rule. */
    /**
     * Round-key cho bracket SE (CUT_TO_SE) — trước đây vòng "default" sinh key phụ thuộc kích
     * thước ("se_last_64", "se_last_32"...) nên KHÔNG BAO GIỜ khớp seed data (mỗi size lại ra 1
     * key khác nhau, không thể seed hết) → luôn fallback race-to=7. Đổi về key ổn định, hữu hạn
     * giống {@link #resolveRoundKey}/{@link #resolveWinnersRoundKey} để seed data thực sự áp dụng
     * được cho mọi kích thước bracket.
     */
    private String resolveSeRoundKey(int round, int totalRounds) {
        return DoubleEliminationBracketMath.resolveSeRoundKey(round, totalRounds);
    }

    /** Wire W→L drop — bỏ qua nếu W match là BYE. */
    private void wireWtoL(Match[][] wGrid, int wr, int wPos,
                           Match[][] lGrid, int lr, int lPos, String loseSlot) {
        if (wPos >= wGrid[wr].length || wGrid[wr][wPos] == null) return;
        if (lPos >= lGrid[lr].length || lGrid[lr][lPos] == null) return;
        if (Boolean.TRUE.equals(wGrid[wr][wPos].getIsBye())) return; // BYE không có loser
        wGrid[wr][wPos].setNextMatchLose(lGrid[lr][lPos]);
        wGrid[wr][wPos].setLoseSlot(loseSlot);
        matchRepository.save(wGrid[wr][wPos]);
    }

    /** Đọc String config từ tournament_config_values. */
    private String readStringConfig(Long tournamentId, String key, String defaultVal) {
        return configValueRepository.findByIdTournamentIdAndIdFieldKey(tournamentId, key)
                .map(TournamentConfigValue::getValue)
                .orElse(defaultVal);
    }

    private record BracketResult(List<TournamentStage> stages, List<Match> matches) {}
}
