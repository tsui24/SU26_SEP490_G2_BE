package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.*;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.TournamentRaceToRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /* ═══════════════════════════════════════════════════════════
     *  ENTRY POINT
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public DrawResultResponse generate(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        String currentStatus = tournament.getStatus();
        if (!"REGISTRATION_CLOSED".equals(currentStatus) && !"DRAW_PREVIEW".equals(currentStatus)) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }

        if ("DRAW_PREVIEW".equals(currentStatus)) {
            clearExistingBracket(tournamentId);
        } else {
            if (!stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId).isEmpty()) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
        }

        List<Participant> participants = participantRepository.findByTournamentIdAndStatus(tournamentId, "ACTIVE");
        if (participants.size() < 2) throw new BusinessException(ErrorCode.INVALID_OPERATION);

        TournamentConfig config = tournamentConfigRepository.findById(tournamentId).orElse(null);
        String seedingMethod = config != null ? config.getSeedingMethod() : "RANDOM";

        if ("RANDOM".equals(seedingMethod)) {
            Collections.shuffle(participants);
        } else {
            participants.sort(Comparator.comparingInt(p -> p.getSeedNo() != null ? p.getSeedNo() : Integer.MAX_VALUE));
        }

        String format = tournament.getFormat();
        BracketResult result = switch (format) {
            case "DOUBLE_ELIMINATION" -> generateDoubleElimination(tournament, participants);
            case "GROUP_PLAYOFF"      -> generateGroupPlayoff(tournament, participants);
            default                   -> generateSingleElimination(tournament, participants);
        };

        tournament.setStatus("DRAW_PREVIEW");
        tournamentRepository.save(tournament);

        List<StageWithMatchesResponse> stageResponses = result.stages().stream()
                .map(s -> StageWithMatchesResponse.builder()
                        .id(s.getId()).tournamentId(tournamentId)
                        .name(s.getName()).stageType(s.getStageType())
                        .orderNo(s.getOrderNo()).status(s.getStatus())
                        .matches(matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(s.getId())
                                .stream().map(this::toMatchResponse).toList())
                        .build())
                .toList();

        return DrawResultResponse.builder()
                .tournamentId(tournamentId).tournamentFormat(format)
                .participantsUsed(participants.size()).stagesCreated(result.stages().size())
                .matchesCreated(result.matches().size()).newStatus("DRAW_PREVIEW")
                .stages(stageResponses).build();
    }

    /* ═══════════════════════════════════════════════════════════
     *  SINGLE ELIMINATION
     *
     *  BYE distribution (n=10, bracketSize=16):
     *    numByes = 6  → positions 1-6 are BYE slots (auto-advance)
     *    positions 7-8 are real matches (4 players)
     *    R2 (Tứ kết): 8 slots fully populated ✓
     * ═══════════════════════════════════════════════════════════ */

    private BracketResult generateSingleElimination(Tournament t, List<Participant> participants) {
        int n = participants.size();
        int bracketSize = nextPowerOf2(n);
        int totalRounds = log2(bracketSize);

        TournamentStage stage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Loại trực tiếp").stageType("KNOCKOUT")
                .orderNo(1).status("PENDING").build());

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
                        .status("PENDING").isBye(false).player1Score(0).player2Score(0).build());
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

        // BYE distribution: first numByes positions auto-advance, rest are real matches
        int numByes = bracketSize - n;
        for (int i = 0; i < numByes; i++) {
            Match m = grid[1][i + 1];
            m.setPlayer1(participants.get(i));
            m.setIsBye(true); m.setStatus("BYE"); m.setWinner(m.getPlayer1());
            matchRepository.save(m);
            placeParticipantInMatch(m.getNextMatchWin(), m.getWinSlot(), m.getPlayer1());
        }
        int realIdx = numByes;
        for (int pos = numByes + 1; pos <= bracketSize / 2; pos++) {
            Match m = grid[1][pos];
            if (realIdx < n) m.setPlayer1(participants.get(realIdx++));
            if (realIdx < n) m.setPlayer2(participants.get(realIdx++));
            matchRepository.save(m);
        }

        // Optional third-place match
        if (resolveThirdPlaceEnabled(t.getId(), t.getFormat()) && totalRounds >= 2) {
            int sfRound = totalRounds - 1;
            Match thirdPlace = matchRepository.save(Match.builder()
                    .tournament(t).stage(stage).bracketType("KNOCKOUT")
                    .roundNo(totalRounds).positionNo(2).matchCode("3RD")
                    .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), "third_place"))
                    .status("PENDING").isBye(false).player1Score(0).player2Score(0).build());
            int sfMc = bracketSize >> sfRound;
            if (sfMc >= 2) {
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

    private BracketResult generateDoubleElimination(Tournament t, List<Participant> participants) {
        String deMode = readStringConfig(t.getId(), "de_mode", "FULL_DE");
        if ("CUT_TO_SE".equals(deMode)) {
            return generateCutToSEDE(t, participants);
        }
        return generateFullDoubleElimination(t, participants);
    }

    private BracketResult generateFullDoubleElimination(Tournament t, List<Participant> participants) {
        int n = participants.size();
        int bracketSize = nextPowerOf2(n);
        int wTotalRounds = log2(bracketSize);
        int lTotalRounds = 2 * (wTotalRounds - 1);

        TournamentStage wStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Nhánh thắng").stageType("WINNERS")
                .orderNo(1).status("PENDING").build());
        TournamentStage lStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Nhánh thua").stageType("LOSERS")
                .orderNo(2).status("PENDING").build());
        TournamentStage gfStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Chung kết lớn").stageType("GRAND_FINAL")
                .orderNo(3).status("PENDING").build());

        // ── Grand Final ───────────────────────────────────────
        Match grandFinal = matchRepository.save(Match.builder()
                .tournament(t).stage(gfStage).bracketType("GRAND_FINAL")
                .roundNo(1).positionNo(1).matchCode("GF")
                .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), "grand_final"))
                .status("PENDING").isBye(false).player1Score(0).player2Score(0).build());

        // ── Winners bracket ───────────────────────────────────
        // wGrid[round][pos] 1-indexed; max pos = bracketSize/2
        Match[][] wGrid = new Match[wTotalRounds + 1][(bracketSize >> 1) + 1];

        for (int wr = 1; wr <= wTotalRounds; wr++) {
            int mc = bracketSize >> wr;
            for (int pos = 1; pos <= mc; pos++) {
                wGrid[wr][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(wStage).bracketType("WINNERS")
                        .roundNo(wr).positionNo(pos)
                        .matchCode("W-R%d-M%d".formatted(wr, pos))
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), "winners_r" + wr))
                        .status("PENDING").isBye(false).player1Score(0).player2Score(0).build());
            }
        }

        // Win links within Winners (same as single elimination)
        for (int wr = 1; wr < wTotalRounds; wr++) {
            int mc = bracketSize >> wr;
            for (int pos = 1; pos <= mc; pos++) {
                int pp = (pos + 1) / 2;
                String slot = (pos % 2 == 1) ? "player1" : "player2";
                wGrid[wr][pos].setNextMatchWin(wGrid[wr + 1][pp]);
                wGrid[wr][pos].setWinSlot(slot);
                matchRepository.save(wGrid[wr][pos]);
            }
        }
        // Winners Final → GF player1
        wGrid[wTotalRounds][1].setNextMatchWin(grandFinal);
        wGrid[wTotalRounds][1].setWinSlot("player1");
        matchRepository.save(wGrid[wTotalRounds][1]);

        // ── Losers bracket ────────────────────────────────────
        // lGrid[lr][pos] 1-indexed; max pos = bracketSize/4
        int maxLPos = Math.max(1, bracketSize >> 2) + 1;
        Match[][] lGrid = new Match[lTotalRounds + 1][maxLPos + 1];

        for (int lr = 1; lr <= lTotalRounds; lr++) {
            int mc = losersMatchCount(bracketSize, lr);
            String rk = (lr == lTotalRounds) ? "losers_final" : "losers_r" + lr;
            for (int pos = 1; pos <= mc; pos++) {
                lGrid[lr][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(lStage).bracketType("LOSERS")
                        .roundNo(lr).positionNo(pos)
                        .matchCode("L-R%d-M%d".formatted(lr, pos))
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), rk))
                        .status("PENDING").isBye(false).player1Score(0).player2Score(0).build());
            }
        }

        // Win links within Losers
        for (int lr = 1; lr < lTotalRounds; lr++) {
            int mc = losersMatchCount(bracketSize, lr);
            for (int pos = 1; pos <= mc; pos++) {
                if (lr % 2 == 1) {
                    // Odd → Even: same position, player1 (player2 comes from W drop later)
                    lGrid[lr][pos].setNextMatchWin(lGrid[lr + 1][pos]);
                    lGrid[lr][pos].setWinSlot("player1");
                } else {
                    // Even → Odd: compression ⌈pos/2⌉
                    int pp = (pos + 1) / 2;
                    String slot = (pos % 2 == 1) ? "player1" : "player2";
                    lGrid[lr][pos].setNextMatchWin(lGrid[lr + 1][pp]);
                    lGrid[lr][pos].setWinSlot(slot);
                }
                matchRepository.save(lGrid[lr][pos]);
            }
        }
        // Losers Final → GF player2
        lGrid[lTotalRounds][1].setNextMatchWin(grandFinal);
        lGrid[lTotalRounds][1].setWinSlot("player2");
        matchRepository.save(lGrid[lTotalRounds][1]);

        // ── Assign participants to W-R1 ───────────────────────
        int numByes = bracketSize - n;

        // BYE slots (top positions)
        for (int i = 0; i < numByes; i++) {
            Match m = wGrid[1][i + 1];
            m.setPlayer1(participants.get(i));
            m.setIsBye(true); m.setStatus("BYE"); m.setWinner(m.getPlayer1());
            matchRepository.save(m);
            placeParticipantInMatch(m.getNextMatchWin(), m.getWinSlot(), m.getPlayer1());
        }
        // Real match slots
        int realIdx = numByes;
        for (int pos = numByes + 1; pos <= bracketSize / 2; pos++) {
            Match m = wGrid[1][pos];
            if (realIdx < n) m.setPlayer1(participants.get(realIdx++));
            if (realIdx < n) m.setPlayer2(participants.get(realIdx++));
            matchRepository.save(m);
        }

        // ── Wire Winners → Losers drops ───────────────────────

        // W-R1 (B/2 matches) → L-R1 (B/4 matches)
        // 2 adjacent W-R1 losers share one L-R1 match: (2k-1)→P1, (2k)→P2
        int lr1Mc = losersMatchCount(bracketSize, 1);
        for (int lPos = 1; lPos <= lr1Mc; lPos++) {
            int wP1 = 2 * lPos - 1;
            int wP2 = 2 * lPos;
            if (wGrid[1][wP1] != null && !Boolean.TRUE.equals(wGrid[1][wP1].getIsBye())) {
                wGrid[1][wP1].setNextMatchLose(lGrid[1][lPos]);
                wGrid[1][wP1].setLoseSlot("player1");
                matchRepository.save(wGrid[1][wP1]);
            }
            if (wGrid[1][wP2] != null && !Boolean.TRUE.equals(wGrid[1][wP2].getIsBye())) {
                wGrid[1][wP2].setNextMatchLose(lGrid[1][lPos]);
                wGrid[1][wP2].setLoseSlot("player2");
                matchRepository.save(wGrid[1][wP2]);
            }
        }

        // W-R(wr≥2) M(pos) loser → L-R(2*(wr-1)) M(pos) as player2
        for (int wr = 2; wr <= wTotalRounds; wr++) {
            int lRound = 2 * (wr - 1);
            int mc = bracketSize >> wr;
            for (int pos = 1; pos <= mc; pos++) {
                if (wGrid[wr][pos] != null && lGrid[lRound][pos] != null) {
                    wGrid[wr][pos].setNextMatchLose(lGrid[lRound][pos]);
                    wGrid[wr][pos].setLoseSlot("player2");
                    matchRepository.save(wGrid[wr][pos]);
                }
            }
        }

        List<TournamentStage> allStages = List.of(wStage, lStage, gfStage);
        List<Match> allMatches = new ArrayList<>();
        allStages.forEach(s -> allMatches.addAll(
                matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(s.getId())));
        return new BracketResult(allStages, allMatches);
    }

    /* ═══════════════════════════════════════════════════════════
     *  GROUP_PLAYOFF (Round-Robin League + Knockout Playoff)
     *
     *  Phase 1 — GROUP stage (Round Robin):
     *    Circle method for n players → n-1 rounds × ⌊n/2⌋ matches/round
     *    matchCode: "GS-R{round}-M{pos}"
     *
     *  Phase 2 — PLAYOFF stage (blank, populated after group stage):
     *    Top K players (playoff_size config, default 4) advance
     *    Single elimination bracket from standings
     *    matchCode: "PO-R{round}-M{pos}"
     *
     *  Advancement to PLAYOFF:
     *    Owner triggers POST /owner/tournaments/{id}/generate-playoff
     *    after all group matches are COMPLETED.
     * ═══════════════════════════════════════════════════════════ */

    private BracketResult generateGroupPlayoff(Tournament t, List<Participant> participants) {
        int n = participants.size();
        int playoffSize = readIntConfig(t.getId(), "playoff_size", 4);
        // playoffSize must be a power of 2 and <= n
        playoffSize = Math.min(playoffSize, nextPowerOf2(n));
        if (playoffSize < 2) playoffSize = 2;

        // ── GROUP stage (Round-Robin, circle method) ──────────
        TournamentStage groupStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Vòng tròn").stageType("GROUP")
                .orderNo(1).status("PENDING").build());

        // Circle-method schedule: totalRounds = n (odd) or n-1 (even)
        List<Participant> roster = new ArrayList<>(participants);
        boolean addedDummy = false;
        if (n % 2 != 0) {
            roster.add(null); // dummy bye player
            addedDummy = true;
        }
        int sz = roster.size(); // guaranteed even
        int totalGroupRounds = sz - 1;

        List<Match> groupMatches = new ArrayList<>();
        int[] ring = new int[sz]; // index into roster; ring[0] is fixed
        for (int i = 0; i < sz; i++) ring[i] = i;

        int raceTo = safeResolveRaceTo(t.getId(), t.getFormat(), "group_stage");
        for (int round = 1; round <= totalGroupRounds; round++) {
            int posNo = 0;
            for (int k = 0; k < sz / 2; k++) {
                Participant p1 = roster.get(ring[k]);
                Participant p2 = roster.get(ring[sz - 1 - k]);
                if (p1 == null || p2 == null) continue; // skip dummy bye pairs
                posNo++;
                Match m = matchRepository.save(Match.builder()
                        .tournament(t).stage(groupStage).bracketType("GROUP")
                        .roundNo(round).positionNo(posNo)
                        .matchCode("GS-R%d-M%d".formatted(round, posNo))
                        .raceTo(raceTo).status("PENDING").isBye(false)
                        .player1(p1).player2(p2)
                        .player1Score(0).player2Score(0).build());
                groupMatches.add(m);
            }
            // Rotate ring[1..sz-1] clockwise by 1
            int last = ring[sz - 1];
            System.arraycopy(ring, 1, ring, 2, sz - 2);
            ring[1] = last;
        }

        // ── PLAYOFF stage (blank — filled after group stage) ──
        TournamentStage playoffStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Vòng playoff").stageType("PLAYOFF")
                .orderNo(2).status("PENDING").build());

        int pTotalRounds = log2(nextPowerOf2(playoffSize));
        Match[][] pGrid = new Match[pTotalRounds + 1][(playoffSize / 2) + 1];
        List<Match> playoffMatches = new ArrayList<>();

        for (int pr = 1; pr <= pTotalRounds; pr++) {
            int mc = nextPowerOf2(playoffSize) >> pr;
            String rk = resolveRoundKey(pr, pTotalRounds, false);
            for (int pos = 1; pos <= mc; pos++) {
                Match m = matchRepository.save(Match.builder()
                        .tournament(t).stage(playoffStage).bracketType("PLAYOFF")
                        .roundNo(pr).positionNo(pos)
                        .matchCode("PO-R%d-M%d".formatted(pr, pos))
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), rk))
                        .status("PENDING").isBye(false).player1Score(0).player2Score(0).build());
                pGrid[pr][pos] = m;
                playoffMatches.add(m);
            }
        }

        // Link playoff win-advancement
        for (int pr = 1; pr < pTotalRounds; pr++) {
            int mc = nextPowerOf2(playoffSize) >> pr;
            for (int pos = 1; pos <= mc; pos++) {
                int pp = (pos + 1) / 2;
                String slot = (pos % 2 == 1) ? "player1" : "player2";
                pGrid[pr][pos].setNextMatchWin(pGrid[pr + 1][pp]);
                pGrid[pr][pos].setWinSlot(slot);
                matchRepository.save(pGrid[pr][pos]);
            }
        }

        List<TournamentStage> stages = List.of(groupStage, playoffStage);
        List<Match> allMatches = new ArrayList<>(groupMatches);
        allMatches.addAll(playoffMatches);
        return new BracketResult(stages, allMatches);
    }

    /* ═══════════════════════════════════════════════════════════
     *  LEAGUE STANDINGS
     *  Sort: wins DESC → frameDiff DESC → framesWon DESC
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional(readOnly = true)
    public List<StandingsEntryResponse> getLeagueStandings(Long tournamentId) {
        // Collect all GROUP stage matches
        List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
        List<Long> groupStageIds = stages.stream()
                .filter(s -> "GROUP".equals(s.getStageType()))
                .map(TournamentStage::getId)
                .toList();

        if (groupStageIds.isEmpty()) return List.of();

        List<Participant> participants = participantRepository.findByTournamentIdAndStatus(tournamentId, "ACTIVE");

        // Accumulate stats per participant
        record Stats(int wins, int losses, int framesWon, int framesLost) {}
        Map<Long, Stats> statsMap = new HashMap<>();
        for (Participant p : participants) {
            statsMap.put(p.getId(), new Stats(0, 0, 0, 0));
        }

        for (Long stageId : groupStageIds) {
            List<Match> matches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stageId);
            for (Match m : matches) {
                if (!"COMPLETED".equals(m.getStatus()) && !"WALKOVER".equals(m.getStatus())) continue;
                if (m.getWinner() == null || m.getLoser() == null) continue;

                Long wId = m.getWinner().getId();
                Long lId = m.getLoser().getId();
                int p1s = m.getPlayer1Score(), p2s = m.getPlayer2Score();
                int winnerFrames = m.getWinner().getId().equals(m.getPlayer1() != null ? m.getPlayer1().getId() : -1L) ? p1s : p2s;
                int loserFrames  = winnerFrames == p1s ? p2s : p1s;

                statsMap.computeIfPresent(wId, (k, s) -> new Stats(s.wins()+1, s.losses(), s.framesWon()+winnerFrames, s.framesLost()+loserFrames));
                statsMap.computeIfPresent(lId, (k, s) -> new Stats(s.wins(), s.losses()+1, s.framesWon()+loserFrames, s.framesLost()+winnerFrames));
            }
        }

        int playoffSize = readIntConfig(tournamentId, "playoff_size", 4);

        // Build sorted list
        Map<Long, Participant> ptcpMap = participants.stream().collect(Collectors.toMap(Participant::getId, p -> p));
        List<Map.Entry<Long, Stats>> sorted = new ArrayList<>(statsMap.entrySet());
        sorted.sort(Comparator
                .comparingInt((Map.Entry<Long, Stats> e) -> e.getValue().wins()).reversed()
                .thenComparingInt(e -> -(e.getValue().framesWon() - e.getValue().framesLost()))
                .thenComparingInt(e -> -e.getValue().framesWon()));

        List<StandingsEntryResponse> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<Long, Stats> entry = sorted.get(i);
            Stats s = entry.getValue();
            Participant p = ptcpMap.get(entry.getKey());
            result.add(StandingsEntryResponse.builder()
                    .rank(i + 1)
                    .participantId(entry.getKey())
                    .displayName(p != null ? p.getDisplayName() : "?")
                    .wins(s.wins()).losses(s.losses())
                    .matchesPlayed(s.wins() + s.losses())
                    .framesWon(s.framesWon()).framesLost(s.framesLost())
                    .frameDiff(s.framesWon() - s.framesLost())
                    .advancesToPlayoff(i < playoffSize)
                    .build());
        }
        return result;
    }

    /* ═══════════════════════════════════════════════════════════
     *  POPULATE PLAYOFF — fill blank playoff bracket from standings
     *  Owner triggers after all group matches are COMPLETED.
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public void populateLeaguePlayoff(Long tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
        TournamentStage groupStage = stages.stream()
                .filter(s -> "GROUP".equals(s.getStageType())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OPERATION));
        TournamentStage playoffStage = stages.stream()
                .filter(s -> "PLAYOFF".equals(s.getStageType())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OPERATION));

        // All group matches must be finished
        List<Match> groupMatches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(groupStage.getId());
        boolean allDone = groupMatches.stream()
                .allMatch(m -> "COMPLETED".equals(m.getStatus()) || "WALKOVER".equals(m.getStatus())
                            || "BYE".equals(m.getStatus()));
        if (!allDone) throw new BusinessException(ErrorCode.INVALID_OPERATION);

        // Get standings, take top K
        List<StandingsEntryResponse> standings = getLeagueStandings(tournamentId);
        int playoffSize = readIntConfig(tournamentId, "playoff_size", 4);
        List<StandingsEntryResponse> advancers = standings.stream()
                .filter(StandingsEntryResponse::getAdvancesToPlayoff)
                .limit(playoffSize)
                .toList();

        if (advancers.size() < 2) throw new BusinessException(ErrorCode.INVALID_OPERATION);

        // Get playoff R1 matches sorted by position
        List<Match> poR1 = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(playoffStage.getId())
                .stream().filter(m -> m.getRoundNo() == 1).toList();

        // Assign top-seeded to R1 in standard bracket order:
        // Seed 1 vs Seed K, Seed 2 vs Seed K-1, etc. (alternating top/bottom)
        Map<Long, Participant> ptcpMap = participantRepository.findByTournamentIdAndStatus(tournamentId, "ACTIVE")
                .stream().collect(Collectors.toMap(Participant::getId, p -> p));

        int lo = 0, hi = advancers.size() - 1;
        for (Match m : poR1) {
            if (lo > hi) break;
            Participant p1 = ptcpMap.get(advancers.get(lo).getParticipantId());
            Participant p2 = (lo < hi) ? ptcpMap.get(advancers.get(hi).getParticipantId()) : null;
            m.setPlayer1(p1);
            m.setPlayer2(p2);
            if (p2 == null) { m.setIsBye(true); m.setStatus("BYE"); m.setWinner(p1); }
            matchRepository.save(m);
            if (p2 == null) placeParticipantInMatch(m.getNextMatchWin(), m.getWinSlot(), p1);
            lo++;
            if (lo <= hi) hi--;
        }

        groupStage.setStatus("COMPLETED");
        stageRepository.save(groupStage);
    }

    /* ═══════════════════════════════════════════════════════════
     *  CONFIRM DRAW — DRAW_PREVIEW → DRAW_DONE
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public void confirmDraw(Long tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"DRAW_PREVIEW".equals(t.getStatus())) throw new BusinessException(ErrorCode.INVALID_OPERATION);
        t.setStatus("DRAW_DONE");
        tournamentRepository.save(t);
    }

    /* ═══════════════════════════════════════════════════════════
     *  SWAP PLAYERS — rearrange R1 seeding in DRAW_PREVIEW
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public void swapPlayers(Long tournamentId, Long matchId1, String slot1, Long matchId2, String slot2) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"DRAW_PREVIEW".equals(t.getStatus())) throw new BusinessException(ErrorCode.INVALID_OPERATION);

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
        return MatchResponse.builder()
                .id(m.getId()).matchCode(m.getMatchCode())
                .tournamentId(m.getTournament().getId())
                .stageId(stageId).stageName(stageName).stageType(stageType)
                .bracketType(m.getBracketType())
                .roundNo(m.getRoundNo()).positionNo(m.getPositionNo())
                .raceTo(m.getRaceTo()).status(m.getStatus()).isBye(m.getIsBye())
                .scheduledAt(m.getScheduledAt())
                .player1(brief(m.getPlayer1())).player2(brief(m.getPlayer2()))
                .player1Score(m.getPlayer1Score()).player2Score(m.getPlayer2Score())
                .winner(brief(m.getWinner())).loser(brief(m.getLoser()))
                .nextMatchWinId(m.getNextMatchWin()  != null ? m.getNextMatchWin().getId()  : null)
                .nextMatchLoseId(m.getNextMatchLose() != null ? m.getNextMatchLose().getId() : null)
                .winSlot(m.getWinSlot()).loseSlot(m.getLoseSlot())
                .build();
    }

    private ParticipantBriefResponse brief(Participant p) {
        if (p == null) return null;
        return ParticipantBriefResponse.builder()
                .id(p.getId()).displayName(p.getDisplayName()).seedNo(p.getSeedNo()).build();
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

    private Participant getSlot(Match m, String slot) { return "player1".equals(slot) ? m.getPlayer1() : m.getPlayer2(); }
    private void setSlot(Match m, String slot, Participant p) { if ("player1".equals(slot)) m.setPlayer1(p); else m.setPlayer2(p); }

    private void updateByeStatus(Match m) {
        if (m.getPlayer1() != null && m.getPlayer2() == null) {
            m.setIsBye(true); m.setStatus("BYE"); m.setWinner(m.getPlayer1());
        } else {
            m.setIsBye(false); m.setStatus("PENDING"); m.setWinner(null);
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

    private int safeResolveRaceTo(Long tid, String format, String roundKey) {
        try { return raceToRuleService.resolveRaceTo(tid, format, roundKey); }
        catch (Exception e) {
            try { return raceToRuleService.resolveRaceTo(tid, format, "round_1"); }
            catch (Exception e2) { return 7; }
        }
    }

    private boolean resolveThirdPlaceEnabled(Long tid, String format) {
        return true; // default enabled; read from config if needed
    }

    private int readIntConfig(Long tournamentId, String key, int defaultVal) {
        return configValueRepository.findByIdTournamentIdAndIdFieldKey(tournamentId, key)
                .map(cv -> { try { return Integer.parseInt(cv.getValue()); } catch (Exception e) { return defaultVal; } })
                .orElse(defaultVal);
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
        int wAllRounds  = log2(bracketSize);

        int seSize = nextPowerOf2(Math.max(2, readIntConfig(t.getId(), "se_phase_size", 64)));

        // cutoffRound: số vòng W bracket trước khi chuyển sang SE
        // seSize = bracketSize / 2^(cutoffRound-1)  →  cutoffRound = log2(bracketSize/seSize)+1
        int cutoffRound = log2(bracketSize / Math.max(seSize, 2)) + 1;

        // Validate — fallback về FULL_DE nếu config không hợp lệ
        if (cutoffRound < 2 || seSize < 4 || seSize >= bracketSize || cutoffRound > wAllRounds) {
            log.warn("CUT_TO_SE invalid config (cutoffRound={}, seSize={}, bracketSize={}), fallback FULL_DE",
                     cutoffRound, seSize, bracketSize);
            return generateFullDoubleElimination(t, participants);
        }

        int lCutoffRounds = 2 * (cutoffRound - 1); // số vòng L bracket

        // ── Stages ───────────────────────────────────────────────────
        TournamentStage wStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Nhánh thắng").stageType("WINNERS")
                .orderNo(1).status("PENDING").build());

        TournamentStage lStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Nhánh thua").stageType("LOSERS")
                .orderNo(2).status("PENDING").build());

        TournamentStage seStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Last " + seSize + " — Loại trực tiếp").stageType("FINAL_BRACKET")
                .orderNo(3).status("PENDING").build());

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
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), "de_winners_r" + wr))
                        .status("PENDING").isBye(false).player1Score(0).player2Score(0).build());
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
            String rk = (lr == lCutoffRounds) ? "de_losers_final" : "de_losers_r" + lr;
            for (int pos = 1; pos <= mc; pos++) {
                lGrid[lr][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(lStage).bracketType("LOSERS")
                        .roundNo(lr).positionNo(pos)
                        .matchCode("L-R%d-M%d".formatted(lr, pos))
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), rk))
                        .status("PENDING").isBye(false).player1Score(0).player2Score(0).build());
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
            String rk = resolveSeRoundKey(sr, seTotalRounds, seSize);
            for (int pos = 1; pos <= mc; pos++) {
                seGrid[sr][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(seStage).bracketType("KNOCKOUT")
                        .roundNo(sr).positionNo(pos)
                        .matchCode("SE-R%d-M%d".formatted(sr, pos))
                        .raceTo(safeResolveRaceTo(t.getId(), t.getFormat(), rk))
                        .status("PENDING").isBye(false).player1Score(0).player2Score(0).build());
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

        // ── Assign participants to W-R1 ──────────────────────────────
        int numByes = bracketSize - n;
        for (int i = 0; i < numByes; i++) {
            Match m = wGrid[1][i + 1];
            m.setPlayer1(participants.get(i));
            m.setIsBye(true); m.setStatus("BYE"); m.setWinner(m.getPlayer1());
            matchRepository.save(m);
            placeParticipantInMatch(m.getNextMatchWin(), m.getWinSlot(), m.getPlayer1());
        }
        int realIdx = numByes;
        for (int pos = numByes + 1; pos <= bracketSize / 2; pos++) {
            Match m = wGrid[1][pos];
            if (realIdx < n) m.setPlayer1(participants.get(realIdx++));
            if (realIdx < n) m.setPlayer2(participants.get(realIdx++));
            matchRepository.save(m);
        }

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
    public void populateFinalBracket(Long tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"DRAW_DONE".equals(t.getStatus())) throw new BusinessException(ErrorCode.INVALID_OPERATION);

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
                          && !"BYE".equals(m.getStatus()))
                .sorted(Comparator.comparing(Match::getPositionNo)).toList();

        boolean wDone = wFinal.stream()
                .allMatch(m -> "COMPLETED".equals(m.getStatus()) || "WALKOVER".equals(m.getStatus()));
        if (!wDone) throw new BusinessException(ErrorCode.INVALID_OPERATION);

        List<Participant> wSurvivors = wFinal.stream()
                .map(Match::getWinner).filter(Objects::nonNull).collect(Collectors.toList());

        List<Participant> lSurvivors = new ArrayList<>();
        if (lStage != null) {
            List<Match> lMatches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(lStage.getId());
            int lLastRound = lMatches.stream().mapToInt(Match::getRoundNo).max().orElse(0);
            List<Match> lFinal = lMatches.stream()
                    .filter(m -> m.getRoundNo() == lLastRound && !"BYE".equals(m.getStatus()))
                    .sorted(Comparator.comparing(Match::getPositionNo)).toList();
            boolean lDone = lFinal.stream()
                    .allMatch(m -> "COMPLETED".equals(m.getStatus()) || "WALKOVER".equals(m.getStatus()));
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
                m.setIsBye(true); m.setStatus("BYE"); m.setWinner(m.getPlayer1());
                placeParticipantInMatch(m.getNextMatchWin(), m.getWinSlot(), m.getPlayer1());
            }
            matchRepository.save(m);
        }

        // Đánh dấu DE stages hoàn thành
        wStage.setStatus("COMPLETED"); stageRepository.save(wStage);
        if (lStage != null) { lStage.setStatus("COMPLETED"); stageRepository.save(lStage); }

        t.setStatus("FINAL_BRACKET_READY");
        tournamentRepository.save(t);
    }

    /* ═══════════════════════════════════════════════════════════
     *  ELIMINATE BOTTOM — Progressive elimination cho GROUP_PLAYOFF
     *
     *  Giữ keepCount người xếp hạng cao nhất.
     *  Người bị loại → INACTIVE.
     *  Match PENDING với người bị loại → tự động WALKOVER cho đối thủ.
     * ═══════════════════════════════════════════════════════════ */

    @Override
    @Transactional
    public void eliminateBottomParticipants(Long tournamentId, int keepCount) {
        List<StandingsEntryResponse> standings = getLeagueStandings(tournamentId);
        if (keepCount >= standings.size()) return;

        Set<Long> eliminatedIds = standings.stream()
                .filter(s -> s.getRank() > keepCount)
                .map(StandingsEntryResponse::getParticipantId)
                .collect(Collectors.toSet());

        // Đánh dấu INACTIVE
        List<Participant> toElim = participantRepository.findAllById(eliminatedIds);
        toElim.forEach(p -> p.setStatus("INACTIVE"));
        participantRepository.saveAll(toElim);

        // Auto-WALKOVER tất cả PENDING matches có người bị loại
        List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
        for (TournamentStage stage : stages) {
            if (!"GROUP".equals(stage.getStageType())) continue;
            matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stage.getId()).stream()
                    .filter(m -> "PENDING".equals(m.getStatus()))
                    .forEach(m -> {
                        boolean p1Elim = m.getPlayer1() != null && eliminatedIds.contains(m.getPlayer1().getId());
                        boolean p2Elim = m.getPlayer2() != null && eliminatedIds.contains(m.getPlayer2().getId());
                        if (p1Elim && p2Elim) {
                            m.setStatus("BYE"); m.setIsBye(true);
                        } else if (p1Elim && m.getPlayer2() != null) {
                            m.setStatus("WALKOVER"); m.setWinner(m.getPlayer2()); m.setLoser(m.getPlayer1());
                        } else if (p2Elim && m.getPlayer1() != null) {
                            m.setStatus("WALKOVER"); m.setWinner(m.getPlayer1()); m.setLoser(m.getPlayer2());
                        }
                        matchRepository.save(m);
                    });
        }
        log.info("Eliminated {} participants (tournamentId={}), kept top {}", eliminatedIds.size(), tournamentId, keepCount);
    }

    /* ═══════════════════════════════════════════════════════════
     *  CUT_TO_SE Helpers
     * ═══════════════════════════════════════════════════════════ */

    /** SE round key dùng để lookup race-to rule. */
    private String resolveSeRoundKey(int round, int totalRounds, int seSize) {
        int fromEnd = totalRounds - round;
        return switch (fromEnd) {
            case 0 -> "se_final";
            case 1 -> "se_semi_final";
            case 2 -> "se_quarter_final";
            default -> "se_last_" + (seSize >> (round - 1));
            // round=1, seSize=64 → se_last_64
            // round=2, seSize=64 → se_last_32
        };
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
