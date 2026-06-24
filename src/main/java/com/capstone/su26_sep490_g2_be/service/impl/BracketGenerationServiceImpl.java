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

@Slf4j
@Service
@RequiredArgsConstructor
public class BracketGenerationServiceImpl implements BracketGenerationService {

    private final TournamentRepository tournamentRepository;
    private final TournamentConfigRepository tournamentConfigRepository;
    private final ParticipantRepository participantRepository;
    private final TournamentStageRepository stageRepository;
    private final MatchRepository matchRepository;
    private final TournamentRaceToRuleService raceToRuleService;

    @Override
    @Transactional
    public DrawResultResponse generate(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // Validate status
        if (!"REGISTRATION_CLOSED".equals(tournament.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }

        // Check no existing bracket
        List<TournamentStage> existingStages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
        if (!existingStages.isEmpty()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        // Load active participants
        List<Participant> participants = participantRepository.findByTournamentIdAndStatus(tournamentId, "ACTIVE");
        if (participants.size() < 2) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION);
        }

        // Load tournament config for seeding method
        TournamentConfig config = tournamentConfigRepository.findById(tournamentId).orElse(null);
        String seedingMethod = config != null ? config.getSeedingMethod() : "RANDOM";

        // Shuffle if RANDOM seeding
        if ("RANDOM".equals(seedingMethod)) {
            Collections.shuffle(participants);
        } else {
            // ELO / MANUAL — use existing seedNo order
            participants.sort(Comparator.comparingInt(p -> p.getSeedNo() != null ? p.getSeedNo() : Integer.MAX_VALUE));
        }

        String format = tournament.getFormat();
        List<TournamentStage> stages;
        List<Match> allMatches;

        switch (format) {
            case "DOUBLE_ELIMINATION" -> {
                var result = generateDoubleElimination(tournament, participants);
                stages = result.stages();
                allMatches = result.matches();
            }
            case "GROUP_PLAYOFF" -> {
                var result = generateGroupPlayoff(tournament, participants);
                stages = result.stages();
                allMatches = result.matches();
            }
            default -> {
                // SINGLE_ELIMINATION (default)
                var result = generateSingleElimination(tournament, participants);
                stages = result.stages();
                allMatches = result.matches();
            }
        }

        // Advance status
        tournament.setStatus("DRAW_DONE");
        tournamentRepository.save(tournament);

        // Build response
        List<StageWithMatchesResponse> stageResponses = stages.stream()
                .map(s -> StageWithMatchesResponse.builder()
                        .id(s.getId())
                        .tournamentId(tournamentId)
                        .name(s.getName())
                        .stageType(s.getStageType())
                        .orderNo(s.getOrderNo())
                        .status(s.getStatus())
                        .matches(matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(s.getId())
                                .stream().map(this::toMatchResponse).toList())
                        .build())
                .toList();

        return DrawResultResponse.builder()
                .tournamentId(tournamentId)
                .tournamentFormat(format)
                .participantsUsed(participants.size())
                .stagesCreated(stages.size())
                .matchesCreated(allMatches.size())
                .newStatus("DRAW_DONE")
                .stages(stageResponses)
                .build();
    }

    /* ═══════════════════════════════════════════════════════════
     *  SINGLE ELIMINATION
     * ═══════════════════════════════════════════════════════════ */

    private BracketResult generateSingleElimination(Tournament t, List<Participant> participants) {
        int n = participants.size();
        int bracketSize = nextPowerOf2(n);
        int totalRounds = (int) (Math.log(bracketSize) / Math.log(2));

        TournamentStage stage = stageRepository.save(TournamentStage.builder()
                .tournament(t)
                .name("Loại trực tiếp")
                .stageType("KNOCKOUT")
                .orderNo(1)
                .status("PENDING")
                .build());

        // grid[round][position] — 1-indexed
        Match[][] grid = new Match[totalRounds + 1][(bracketSize / 2) + 1];

        // Create empty matches
        for (int round = 1; round <= totalRounds; round++) {
            int matchCount = bracketSize / (int) Math.pow(2, round);
            for (int pos = 1; pos <= matchCount; pos++) {
                String roundKey = resolveRoundKey(round, totalRounds, false);
                int raceTo = safeResolveRaceTo(t.getId(), t.getFormat(), roundKey);
                String code = "R%d-M%d".formatted(round, pos);

                Match m = matchRepository.save(Match.builder()
                        .tournament(t)
                        .stage(stage)
                        .bracketType("KNOCKOUT")
                        .roundNo(round)
                        .positionNo(pos)
                        .matchCode(code)
                        .raceTo(raceTo)
                        .status("PENDING")
                        .isBye(false)
                        .player1Score(0)
                        .player2Score(0)
                        .build());
                grid[round][pos] = m;
            }
        }

        // Link win advancement
        for (int round = 1; round < totalRounds; round++) {
            int matchCount = bracketSize / (int) Math.pow(2, round);
            for (int pos = 1; pos <= matchCount; pos++) {
                int parentPos = (pos + 1) / 2;
                String slot = (pos % 2 == 1) ? "player1" : "player2";
                Match m = grid[round][pos];
                m.setNextMatchWin(grid[round + 1][parentPos]);
                m.setWinSlot(slot);
                matchRepository.save(m);
            }
        }

        // Assign participants to Round 1
        List<Match> autoAdvanced = new ArrayList<>();
        for (int pos = 1; pos <= bracketSize / 2; pos++) {
            int p1idx = 2 * pos - 2;
            int p2idx = 2 * pos - 1;
            Match m = grid[1][pos];

            if (p1idx < n) m.setPlayer1(participants.get(p1idx));
            if (p2idx < n) m.setPlayer2(participants.get(p2idx));

            // BYE match: only player1 exists
            if (m.getPlayer1() != null && m.getPlayer2() == null) {
                m.setIsBye(true);
                m.setStatus("BYE");
                m.setWinner(m.getPlayer1());
                matchRepository.save(m);
                // Auto-advance
                placeParticipantInMatch(m.getNextMatchWin(), m.getWinSlot(), m.getPlayer1());
                autoAdvanced.add(m);
            } else {
                matchRepository.save(m);
            }
        }

        // Third place match (optional)
        boolean thirdPlaceNeeded = resolveThirdPlaceEnabled(t.getId(), t.getFormat());
        if (thirdPlaceNeeded && totalRounds >= 2) {
            int sfRound = totalRounds - 1;
            String tpCode = "3RD";
            int raceTo = safeResolveRaceTo(t.getId(), t.getFormat(), "third_place");
            Match thirdPlace = matchRepository.save(Match.builder()
                    .tournament(t)
                    .stage(stage)
                    .bracketType("KNOCKOUT")
                    .roundNo(totalRounds)
                    .positionNo(2)
                    .matchCode(tpCode)
                    .raceTo(raceTo)
                    .status("PENDING")
                    .isBye(false)
                    .player1Score(0)
                    .player2Score(0)
                    .build());

            int sfMatchCount = bracketSize / (int) Math.pow(2, sfRound);
            if (sfMatchCount >= 2) {
                grid[sfRound][1].setNextMatchLose(thirdPlace);
                grid[sfRound][1].setLoseSlot("player1");
                grid[sfRound][2].setNextMatchLose(thirdPlace);
                grid[sfRound][2].setLoseSlot("player2");
                matchRepository.save(grid[sfRound][1]);
                matchRepository.save(grid[sfRound][2]);
            }
        }

        List<Match> allMatches = matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(stage.getId());
        return new BracketResult(List.of(stage), allMatches);
    }

    /* ═══════════════════════════════════════════════════════════
     *  DOUBLE ELIMINATION
     * ═══════════════════════════════════════════════════════════ */

    private BracketResult generateDoubleElimination(Tournament t, List<Participant> participants) {
        int n = participants.size();
        int bracketSize = nextPowerOf2(n);
        int wTotalRounds = (int) (Math.log(bracketSize) / Math.log(2));

        // Winners stage
        TournamentStage wStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Nhánh thắng").stageType("WINNERS")
                .orderNo(1).status("PENDING").build());

        // Losers stage
        TournamentStage lStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Nhánh thua").stageType("LOSERS")
                .orderNo(2).status("PENDING").build());

        // Grand final stage
        TournamentStage gfStage = stageRepository.save(TournamentStage.builder()
                .tournament(t).name("Chung kết lớn").stageType("GRAND_FINAL")
                .orderNo(3).status("PENDING").build());

        // Build winners bracket
        Match[][] wGrid = new Match[wTotalRounds + 1][(bracketSize / 2) + 1];
        for (int round = 1; round <= wTotalRounds; round++) {
            int mc = bracketSize / (int) Math.pow(2, round);
            for (int pos = 1; pos <= mc; pos++) {
                String rk = "winners_r" + round;
                if (round == wTotalRounds) rk = "winners_sf"; // Winners final == WRF
                int raceTo = safeResolveRaceTo(t.getId(), t.getFormat(), rk);
                wGrid[round][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(wStage).bracketType("WINNERS")
                        .roundNo(round).positionNo(pos).matchCode("W-R%d-M%d".formatted(round, pos))
                        .raceTo(raceTo).status("PENDING").isBye(false)
                        .player1Score(0).player2Score(0).build());
            }
        }

        // Link winners win-advancement
        for (int round = 1; round < wTotalRounds; round++) {
            int mc = bracketSize / (int) Math.pow(2, round);
            for (int pos = 1; pos <= mc; pos++) {
                int pp = (pos + 1) / 2;
                String slot = (pos % 2 == 1) ? "player1" : "player2";
                wGrid[round][pos].setNextMatchWin(wGrid[round + 1][pp]);
                wGrid[round][pos].setWinSlot(slot);
                matchRepository.save(wGrid[round][pos]);
            }
        }

        // Build losers bracket
        // Structure: DE with bracketSize=8 has 6 rounds in losers (2 per winners round drop)
        int lTotalRounds = 2 * (wTotalRounds - 1);
        Match[][] lGrid = new Match[lTotalRounds + 1][(bracketSize / 4) + 1];
        for (int lr = 1; lr <= lTotalRounds; lr++) {
            int mc = Math.max(1, bracketSize / (int) Math.pow(2, (lr / 2) + 2));
            String rk = "losers_r" + lr;
            if (lr == lTotalRounds) rk = "losers_final";
            int raceTo = safeResolveRaceTo(t.getId(), t.getFormat(), rk);
            for (int pos = 1; pos <= mc; pos++) {
                lGrid[lr][pos] = matchRepository.save(Match.builder()
                        .tournament(t).stage(lStage).bracketType("LOSERS")
                        .roundNo(lr).positionNo(pos).matchCode("L-R%d-M%d".formatted(lr, pos))
                        .raceTo(raceTo).status("PENDING").isBye(false)
                        .player1Score(0).player2Score(0).build());
            }
        }

        // Link losers bracket win-advancement (within losers)
        for (int lr = 1; lr < lTotalRounds; lr++) {
            int nextMc = Math.max(1, bracketSize / (int) Math.pow(2, ((lr + 1) / 2) + 2));
            int curMc = Math.max(1, bracketSize / (int) Math.pow(2, (lr / 2) + 2));
            for (int pos = 1; pos <= curMc; pos++) {
                if (lGrid[lr][pos] == null || lGrid[lr + 1] == null) continue;
                if (lr % 2 == 0) {
                    // Even losers rounds: pos → same as pos (compressed)
                    int pp = Math.min(pos, nextMc);
                    String slot = (pos % 2 == 1) ? "player1" : "player2";
                    if (lGrid[lr][pos] != null && lGrid[lr + 1][pp] != null) {
                        lGrid[lr][pos].setNextMatchWin(lGrid[lr + 1][pp]);
                        lGrid[lr][pos].setWinSlot(slot);
                        matchRepository.save(lGrid[lr][pos]);
                    }
                } else {
                    // Odd: pos → ceil(pos/2)
                    int pp = (pos + 1) / 2;
                    pp = Math.min(pp, nextMc);
                    String slot = (pos % 2 == 1) ? "player1" : "player2";
                    if (lGrid[lr][pos] != null && lGrid[lr + 1][pp] != null) {
                        lGrid[lr][pos].setNextMatchWin(lGrid[lr + 1][pp]);
                        lGrid[lr][pos].setWinSlot(slot);
                        matchRepository.save(lGrid[lr][pos]);
                    }
                }
            }
        }

        // Wire losers from winners bracket → losers bracket round 1
        int wr1mc = bracketSize / 2;
        int lr1mc = Math.max(1, bracketSize / (int) Math.pow(2, 2));
        for (int pos = 1; pos <= wr1mc && pos <= lr1mc; pos++) {
            if (wGrid[1][pos] != null && lGrid[1][pos] != null) {
                wGrid[1][pos].setNextMatchLose(lGrid[1][pos]);
                wGrid[1][pos].setLoseSlot("player2");
                matchRepository.save(wGrid[1][pos]);
            }
        }

        // Grand Final
        int gfRaceTo = safeResolveRaceTo(t.getId(), t.getFormat(), "grand_final");
        Match grandFinal = matchRepository.save(Match.builder()
                .tournament(t).stage(gfStage).bracketType("GRAND_FINAL")
                .roundNo(1).positionNo(1).matchCode("GF")
                .raceTo(gfRaceTo).status("PENDING").isBye(false)
                .player1Score(0).player2Score(0).build());

        // Wire: winner of losers final → GF player2
        if (lGrid[lTotalRounds][1] != null) {
            lGrid[lTotalRounds][1].setNextMatchWin(grandFinal);
            lGrid[lTotalRounds][1].setWinSlot("player2");
            matchRepository.save(lGrid[lTotalRounds][1]);
        }
        // Wire: winner of winners final → GF player1
        wGrid[wTotalRounds][1].setNextMatchWin(grandFinal);
        wGrid[wTotalRounds][1].setWinSlot("player1");
        matchRepository.save(wGrid[wTotalRounds][1]);

        // Assign participants to Winners Round 1
        for (int pos = 1; pos <= bracketSize / 2; pos++) {
            int p1idx = 2 * pos - 2;
            int p2idx = 2 * pos - 1;
            Match m = wGrid[1][pos];
            if (p1idx < n) m.setPlayer1(participants.get(p1idx));
            if (p2idx < n) m.setPlayer2(participants.get(p2idx));
            if (m.getPlayer1() != null && m.getPlayer2() == null) {
                m.setIsBye(true);
                m.setStatus("BYE");
                m.setWinner(m.getPlayer1());
                matchRepository.save(m);
                placeParticipantInMatch(m.getNextMatchWin(), m.getWinSlot(), m.getPlayer1());
            } else {
                matchRepository.save(m);
            }
        }

        List<TournamentStage> allStages = List.of(wStage, lStage, gfStage);
        List<Match> allMatches = new ArrayList<>();
        allStages.forEach(s -> allMatches.addAll(
                matchRepository.findByStageIdOrderByRoundNoAscPositionNoAsc(s.getId())));
        return new BracketResult(allStages, allMatches);
    }

    /* ═══════════════════════════════════════════════════════════
     *  GROUP PLAYOFF (skeleton)
     * ═══════════════════════════════════════════════════════════ */

    private BracketResult generateGroupPlayoff(Tournament t, List<Participant> participants) {
        // Phase 1: Group stage (Round Robin within groups)
        // Phase 2: Playoff (single elimination from group winners)
        // For now, just build the group stage as a round-robin per group
        // Full implementation is format-specific and more complex

        // Placeholder: fall back to single elimination for now
        log.warn("GROUP_PLAYOFF bracket generation falls back to single elimination");
        return generateSingleElimination(t, participants);
    }

    /* ═══════════════════════════════════════════════════════════
     *  Helpers
     * ═══════════════════════════════════════════════════════════ */

    private void placeParticipantInMatch(Match nextMatch, String slot, Participant participant) {
        if (nextMatch == null || participant == null) return;
        Match refreshed = matchRepository.findById(nextMatch.getId()).orElse(nextMatch);
        if ("player1".equals(slot)) {
            refreshed.setPlayer1(participant);
        } else {
            refreshed.setPlayer2(participant);
        }
        matchRepository.save(refreshed);
    }

    private int nextPowerOf2(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    private String resolveRoundKey(int round, int totalRounds, boolean isLosersBracket) {
        int fromEnd = totalRounds - round;
        return switch (fromEnd) {
            case 0 -> "final";
            case 1 -> "semi_final";
            case 2 -> "quarter_final";
            default -> "round_1";
        };
    }

    private int safeResolveRaceTo(Long tournamentId, String formatCode, String roundKey) {
        try {
            return raceToRuleService.resolveRaceTo(tournamentId, formatCode, roundKey);
        } catch (Exception e) {
            try {
                return raceToRuleService.resolveRaceTo(tournamentId, formatCode, "round_1");
            } catch (Exception e2) {
                return 7; // default race-to
            }
        }
    }

    private boolean resolveThirdPlaceEnabled(Long tournamentId, String formatCode) {
        try {
            return true; // Default enabled; could read from TournamentConfigValue
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<StageWithMatchesResponse> getStagesWithMatches(Long tournamentId) {
        List<TournamentStage> stages = stageRepository.findByTournamentIdOrderByOrderNoAsc(tournamentId);
        return stages.stream()
                .map(s -> StageWithMatchesResponse.builder()
                        .id(s.getId())
                        .tournamentId(tournamentId)
                        .name(s.getName())
                        .stageType(s.getStageType())
                        .orderNo(s.getOrderNo())
                        .status(s.getStatus())
                        .matches(matchRepository
                                .findByStageIdOrderByRoundNoAscPositionNoAsc(s.getId())
                                .stream().map(this::toMatchResponse).toList())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getMatchesForTournament(Long tournamentId) {
        return matchRepository
                .findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournamentId)
                .stream().map(this::toMatchResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getMatchesForPlayer(Long userId) {
        // Use dedicated JOIN FETCH query — avoids findAll() + in-memory filter
        List<Participant> myParticipants = participantRepository.findByRegistrationUserId(userId);
        return myParticipants.stream()
                .flatMap(p -> matchRepository.findByParticipantId(p.getId()).stream())
                .distinct()
                .map(this::toMatchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatchResponseById(Long matchId) {
        Match m = matchRepository.findByIdWithDetails(matchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return toMatchResponse(m);
    }

    public MatchResponse toMatchResponse(Match m) {
        // stage may be a proxy — access getId() only (safe, doesn't need initialization)
        Long stageId   = null;
        String stageName = null;
        String stageType = null;
        try {
            stageId   = m.getStage().getId();
            stageName = m.getStage().getName();
            stageType = m.getStage().getStageType();
        } catch (Exception ignored) { /* lazy proxy not init — omit */ }

        return MatchResponse.builder()
                .id(m.getId())
                .matchCode(m.getMatchCode())
                .tournamentId(m.getTournament().getId())
                .stageId(stageId)
                .stageName(stageName)
                .stageType(stageType)
                .bracketType(m.getBracketType())
                .roundNo(m.getRoundNo())
                .positionNo(m.getPositionNo())
                .raceTo(m.getRaceTo())
                .status(m.getStatus())
                .isBye(m.getIsBye())
                .scheduledAt(m.getScheduledAt())
                .player1(brief(m.getPlayer1()))
                .player2(brief(m.getPlayer2()))
                .player1Score(m.getPlayer1Score())
                .player2Score(m.getPlayer2Score())
                .winner(brief(m.getWinner()))
                .loser(brief(m.getLoser()))
                .nextMatchWinId(m.getNextMatchWin() != null ? m.getNextMatchWin().getId() : null)
                .nextMatchLoseId(m.getNextMatchLose() != null ? m.getNextMatchLose().getId() : null)
                .winSlot(m.getWinSlot())
                .loseSlot(m.getLoseSlot())
                .build();
    }

    private ParticipantBriefResponse brief(Participant p) {
        if (p == null) return null;
        return ParticipantBriefResponse.builder()
                .id(p.getId())
                .displayName(p.getDisplayName())
                .seedNo(p.getSeedNo())
                .build();
    }

    private record BracketResult(List<TournamentStage> stages, List<Match> matches) {}
}
