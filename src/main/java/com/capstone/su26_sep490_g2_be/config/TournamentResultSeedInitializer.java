package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRankingResponse;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentResultRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.TournamentResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sinh dữ liệu mẫu cho bảng tournament_results dựa trên kết quả bracket thực tế.
 *
 * <p>Chạy sau tất cả initializer khác (@Order 1-3).
 * Idempotent — bỏ qua giải đã có bản ghi kết quả.
 *
 * <p>Phân phối điểm và giải thưởng:
 * <ul>
 *   <li>#1  → 100 điểm, 40% prizePool</li>
 *   <li>#2  → 70  điểm, 20% prizePool</li>
 *   <li>#3-4  → 50  điểm, 15% prizePool (chia đều)</li>
 *   <li>#5-8  → 30  điểm, 10% prizePool (chia đều)</li>
 *   <li>#9-16 → 15  điểm, 8%  prizePool (chia đều)</li>
 *   <li>#17-32 → 8  điểm, 5%  prizePool (chia đều)</li>
 *   <li>#33-64 → 3  điểm, 2%  prizePool (chia đều)</li>
 * </ul>
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class TournamentResultSeedInitializer implements CommandLineRunner {

    private final TournamentRepository tournamentRepository;
    private final ParticipantRepository participantRepository;
    private final TournamentResultRepository resultRepository;
    private final TournamentResultService tournamentResultService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        User recorder = userRepository.findByEmail("owner@gmail.com").orElse(null);

        List<Tournament> completed = tournamentRepository.findAll().stream()
                .filter(t -> TournamentStatus.COMPLETED.getValue().equals(t.getStatus()))
                .toList();

        int totalSeeded = 0;
        for (Tournament tournament : completed) {
            totalSeeded += seedResultsForTournament(tournament, recorder);
        }

        if (totalSeeded > 0) {
            log.info("TournamentResultSeedInitializer: tạo {} bản ghi kết quả cho {} giải COMPLETED.",
                    totalSeeded, completed.size());
        }
    }

    private int seedResultsForTournament(Tournament tournament, User recorder) {
        // Idempotent — bỏ qua nếu đã có kết quả
        if (!resultRepository.findByTournamentIdOrderByFinalRankAsc(tournament.getId()).isEmpty()) {
            return 0;
        }

        // Tính xếp hạng từ bracket
        TournamentRankingResponse ranking;
        try {
            ranking = tournamentResultService.getRankings(tournament.getId());
        } catch (Exception e) {
            log.warn("TournamentResultSeedInitializer: không tính được xếp hạng cho '{}' — {}",
                    tournament.getName(), e.getMessage());
            return 0;
        }

        List<TournamentRankingEntryResponse> entries = ranking.getEntries();
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        // Map participantId → Participant để tạo FK
        Map<Long, Participant> participantMap = participantRepository
                .findByTournamentId(tournament.getId()).stream()
                .collect(Collectors.toMap(Participant::getId, p -> p, (a, b) -> a));

        BigDecimal prizePool = tournament.getPrizePool() != null
                ? tournament.getPrizePool() : BigDecimal.ZERO;
        Instant recordedAt = Instant.now();

        int count = 0;
        int currentRank = 0;

        for (TournamentRankingEntryResponse entry : entries) {
            // Gán hạng tuần tự không trùng trong cùng nhóm (#3-4 → 3 rồi 4)
            currentRank = Math.max(entry.getRankFrom(), currentRank + 1);

            Long participantId = entry.getParticipantId();
            if (participantId == null) continue;

            Participant participant = participantMap.get(participantId);
            if (participant == null) continue;

            // Bỏ qua nếu đã tồn tại (an toàn khi chạy lại)
            if (resultRepository.existsByTournamentIdAndParticipantId(tournament.getId(), participantId)) {
                continue;
            }

            BigDecimal prize = computePrize(prizePool, entry.getRankFrom(), entry.getRankTo());
            int points = computePoints(entry.getRankFrom());

            resultRepository.save(TournamentResult.builder()
                    .tournament(tournament)
                    .participant(participant)
                    .finalRank(currentRank)
                    .prizeAmount(prize)
                    .pointsEarned(points)
                    .note(entry.getNote())
                    .recordedAt(recordedAt)
                    .recordedBy(recorder)
                    .build());
            count++;
        }

        if (count > 0) {
            log.info("TournamentResultSeedInitializer: '{}' → {} kết quả.", tournament.getName(), count);
        }
        return count;
    }

    /**
     * Phân chia giải thưởng đều cho tất cả cơ thủ trong cùng nhóm hạng.
     * Ví dụ: nhóm #3-4 (rankFrom=3, rankTo=4) → mỗi người nhận 15%/2 = 7.5% prizePool.
     */
    private BigDecimal computePrize(BigDecimal pool, int rankFrom, int rankTo) {
        if (pool.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        int groupSize = Math.max(1, rankTo - rankFrom + 1);
        double groupPct = switch (rankFrom) {
            case 1  -> 0.40;
            case 2  -> 0.20;
            case 3  -> 0.15;
            case 5  -> 0.10;
            case 9  -> 0.08;
            case 17 -> 0.05;
            case 33 -> 0.02;
            default -> 0.0;
        };
        if (groupPct == 0) return BigDecimal.ZERO;
        return pool.multiply(BigDecimal.valueOf(groupPct / groupSize))
                .setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Điểm xếp hạng theo vị trí cuối cùng trong giải.
     * Dùng rankFrom để xác định nhóm — mọi người trong cùng nhóm nhận điểm bằng nhau.
     */
    private int computePoints(int rankFrom) {
        if (rankFrom == 1)  return 100;
        if (rankFrom == 2)  return 70;
        if (rankFrom <= 4)  return 50;
        if (rankFrom <= 8)  return 30;
        if (rankFrom <= 16) return 15;
        if (rankFrom <= 32) return 8;
        return 3;
    }
}
