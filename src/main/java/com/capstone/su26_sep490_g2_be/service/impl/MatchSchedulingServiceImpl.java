package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentStage;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.MatchSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thuật toán xếp lịch:
 * - Ước lượng thời lượng 1 trận = làm-tròn-lên-5( 1.5 × raceTo × phút/ván ), phút/ván theo loại bi.
 * - Greedy list-scheduling trên N bàn, xử lý theo stage tăng dần orderNo.
 *   + Stage có feeder liên kết chéo (DE) → chạy theo DAG feeder (không ép tuần tự).
 *   + Stage KHÔNG có feeder chéo (vòng bảng / vòng tròn loại dần / playoff) → bắt đầu sau khi
 *     mọi trận các stage trước ước lượng xong (chạy tuần tự).
 * - Giờ chỉ LÙI: mỗi trận không được xếp sớm hơn giờ đang có (baseline). Trận đã khóa/đang đấu/đã
 *   xong được giữ nguyên và coi là "chỗ đã đặt" chiếm bàn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchSchedulingServiceImpl implements MatchSchedulingService {

	private static final int DEFAULT_MINUTES_PER_RACK = 7;
	private static final double AVG_RACKS_FACTOR = 1.5;   // số ván TB ≈ 1.5 × raceTo
	private static final int TURNAROUND_MINUTES = 10;     // nghỉ giữa 2 trận cùng bàn

	private final TournamentRepository tournamentRepository;
	private final MatchRepository matchRepository;

	@Override
	@Transactional
	public void reschedule(Long tournamentId) {
		try {
			doReschedule(tournamentId);
		} catch (Exception e) {
			// Xếp lịch chỉ là tiện ích — không được làm hỏng luồng bốc thăm / hoàn thành trận.
			log.warn("Reschedule failed for tournament {}: {}", tournamentId, e.getMessage());
		}
	}

	private void doReschedule(Long tournamentId) {
		Tournament t = tournamentRepository.findById(tournamentId).orElse(null);
		if (t == null) return;

		int tableCount = (t.getTableCount() != null && t.getTableCount() > 0) ? t.getTableCount() : 1;
		int minsPerRack = minutesPerRack(t.getGameType());

		Instant now = Instant.now();
		Instant anchor = (t.getStartAt() != null && t.getStartAt().isAfter(now)) ? t.getStartAt() : now;

		List<Match> matches = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournamentId);
		if (matches.isEmpty()) return;

		Map<Long, Match> byId = new HashMap<>();
		Map<Long, Long> stageOf = new HashMap<>();
		for (Match m : matches) {
			byId.put(m.getId(), m);
			stageOf.put(m.getId(), m.getStage() != null ? m.getStage().getId() : null);
		}

		// feeders[matchId] = danh sách trận nguồn (predecessor)
		Map<Long, List<Long>> feeders = new HashMap<>();
		for (Match m : matches) {
			Long win = m.getNextMatchWin() != null ? m.getNextMatchWin().getId() : null;
			Long lose = m.getNextMatchLose() != null ? m.getNextMatchLose().getId() : null;
			if (win != null) feeders.computeIfAbsent(win, k -> new ArrayList<>()).add(m.getId());
			if (lose != null) feeders.computeIfAbsent(lose, k -> new ArrayList<>()).add(m.getId());
		}

		// Nhóm match theo stage, sắp stage theo orderNo
		List<TournamentStage> stages = matches.stream()
				.map(Match::getStage)
				.filter(java.util.Objects::nonNull)
				.collect(java.util.stream.Collectors.toMap(TournamentStage::getId, s -> s, (a, b) -> a))
				.values().stream()
				.sorted(Comparator.comparingInt(s -> s.getOrderNo() != null ? s.getOrderNo() : 0))
				.toList();

		Instant[] tableFree = new Instant[tableCount + 1];
		for (int i = 1; i <= tableCount; i++) tableFree[i] = anchor;

		Map<Long, Instant> endAt = new HashMap<>();
		List<Match> changed = new ArrayList<>();
		Instant globalMaxEnd = anchor;

		for (TournamentStage stage : stages) {
			List<Match> stageMatches = matches.stream()
					.filter(m -> m.getStage() != null && stage.getId().equals(m.getStage().getId()))
					.toList();

			boolean hasCrossFeeder = stageMatches.stream().anyMatch(m ->
					feeders.getOrDefault(m.getId(), List.of()).stream()
							.anyMatch(fid -> !stage.getId().equals(stageOf.get(fid))));
			Instant stageFloor = hasCrossFeeder ? anchor : globalMaxEnd;

			// 1) Trận cố định: đã xong / đang đấu / đã khóa → giữ nguyên, chiếm bàn, ghi endAt
			for (Match m : stageMatches) {
				if (isResolved(m)) {
					Instant end = m.getUpdatedAt() != null ? m.getUpdatedAt() : stageFloor;
					endAt.put(m.getId(), end);
				} else if (MatchStatus.IN_PROGRESS.getValue().equals(m.getStatus())) {
					Instant start = m.getScheduledAt() != null ? m.getScheduledAt() : now;
					Instant end = laterOf(start.plus(Duration.ofMinutes(durationMinutes(m, minsPerRack))), now);
					endAt.put(m.getId(), end);
					reserveTable(tableFree, m.getTableNo(), end);
				} else if (Boolean.TRUE.equals(m.getScheduleLocked())) {
					Instant end = m.getEstimatedEndAt() != null
							? m.getEstimatedEndAt()
							: (m.getScheduledAt() != null
								? m.getScheduledAt().plus(Duration.ofMinutes(durationMinutes(m, minsPerRack)))
								: stageFloor);
					endAt.put(m.getId(), end);
					reserveTable(tableFree, m.getTableNo(), end);
				}
			}

			// 2) Trận PENDING chưa khóa: xếp theo DAG feeder (worklist)
			boolean progress = true;
			while (progress) {
				progress = false;
				List<Match> ready = new ArrayList<>();
				for (Match m : stageMatches) {
					if (endAt.containsKey(m.getId())) continue;                 // đã xử lý
					if (!isSchedulable(m)) continue;                            // fixed đã xử lý ở trên
					List<Long> fs = feeders.getOrDefault(m.getId(), List.of());
					if (fs.stream().allMatch(endAt::containsKey)) ready.add(m);
				}
				if (ready.isEmpty()) break;
				ready.sort(Comparator
						.comparing((Match m) -> readyTime(m, feeders, endAt, stageFloor))
						.thenComparingInt(m -> m.getRoundNo() != null ? m.getRoundNo() : 0)
						.thenComparingInt(m -> m.getPositionNo() != null ? m.getPositionNo() : 0));
				for (Match m : ready) {
					progress = true;
					if (isByeMatch(m)) {
						endAt.put(m.getId(), readyTime(m, feeders, endAt, stageFloor));
						continue; // BYE không chiếm bàn / không có giờ
					}
					Instant ready0 = readyTime(m, feeders, endAt, stageFloor);
					Instant floor = m.getScheduledAt() != null ? m.getScheduledAt() : stageFloor; // chỉ lùi
					int table = earliestTable(tableFree, tableCount);
					Instant start = maxOf(ready0, tableFree[table], floor);
					Instant end = start.plus(Duration.ofMinutes(durationMinutes(m, minsPerRack)));

					m.setTableNo(table);
					m.setScheduledAt(start);
					m.setEstimatedEndAt(end);
					changed.add(m);

					tableFree[table] = end.plus(Duration.ofMinutes(TURNAROUND_MINUTES));
					endAt.put(m.getId(), end);
				}
			}

			for (Match m : stageMatches) {
				Instant end = endAt.get(m.getId());
				if (end != null && end.isAfter(globalMaxEnd)) globalMaxEnd = end;
			}
		}

		if (!changed.isEmpty()) {
			matchRepository.saveAll(changed);
		}
	}

	/* ── Helpers ── */

	private Instant readyTime(Match m, Map<Long, List<Long>> feeders, Map<Long, Instant> endAt, Instant floor) {
		Instant t = floor;
		for (Long fid : feeders.getOrDefault(m.getId(), List.of())) {
			Instant fe = endAt.get(fid);
			if (fe != null && fe.isAfter(t)) t = fe;
		}
		return t;
	}

	private int earliestTable(Instant[] tableFree, int tableCount) {
		int best = 1;
		for (int i = 2; i <= tableCount; i++) {
			if (tableFree[i].isBefore(tableFree[best])) best = i;
		}
		return best;
	}

	private void reserveTable(Instant[] tableFree, Integer tableNo, Instant end) {
		if (tableNo == null || tableNo < 1 || tableNo >= tableFree.length) return;
		Instant next = end.plus(Duration.ofMinutes(TURNAROUND_MINUTES));
		if (next.isAfter(tableFree[tableNo])) tableFree[tableNo] = next;
	}

	private boolean isResolved(Match m) {
		return MatchStatus.COMPLETED.getValue().equals(m.getStatus())
				|| MatchStatus.WALKOVER.getValue().equals(m.getStatus())
				|| MatchStatus.BYE.getValue().equals(m.getStatus());
	}

	/** Trận PENDING chưa khóa (đối tượng của auto-scheduler). */
	private boolean isSchedulable(Match m) {
		return MatchStatus.PENDING.getValue().equals(m.getStatus())
				&& !Boolean.TRUE.equals(m.getScheduleLocked());
	}

	private boolean isByeMatch(Match m) {
		return Boolean.TRUE.equals(m.getIsBye()) || MatchStatus.BYE.getValue().equals(m.getStatus());
	}

	private long durationMinutes(Match m, int minsPerRack) {
		if (isByeMatch(m)) return 0;
		int raceTo = m.getRaceTo() != null ? m.getRaceTo() : 5;
		double raw = AVG_RACKS_FACTOR * raceTo * minsPerRack;
		long mins = (long) Math.ceil(raw);
		return ((mins + 4) / 5) * 5; // làm tròn lên bội số 5 phút
	}

	@Override
	public long estimateDurationMinutes(Match m) {
		String gameType = null;
		try { gameType = m.getTournament() != null ? m.getTournament().getGameType() : null; } catch (Exception ignored) {}
		return durationMinutes(m, minutesPerRack(gameType));
	}

	private int minutesPerRack(String gameType) {
		if (gameType == null) return DEFAULT_MINUTES_PER_RACK;
		return switch (gameType) {
			case "9_BALL" -> 6;
			case "10_BALL" -> 7;
			case "8_BALL" -> 8;
			default -> DEFAULT_MINUTES_PER_RACK;
		};
	}

	private Instant maxOf(Instant a, Instant b, Instant c) {
		Instant m = a.isAfter(b) ? a : b;
		return m.isAfter(c) ? m : c;
	}

	private Instant laterOf(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}
}
