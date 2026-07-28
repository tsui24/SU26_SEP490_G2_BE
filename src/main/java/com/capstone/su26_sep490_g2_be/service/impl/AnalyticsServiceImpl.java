package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.AnalyticsOverviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.GameTypeBreakdownItem;
import com.capstone.su26_sep490_g2_be.dto.response.LabeledAmountItem;
import com.capstone.su26_sep490_g2_be.dto.response.MatchStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportItem;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PaymentHistoryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerGrowthResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerLeaderboardItem;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RevenueBreakdownResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SocialEngagementResponse;
import com.capstone.su26_sep490_g2_be.dto.response.StatusCountItem;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentAnalyticsDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentPerformanceItem;
import com.capstone.su26_sep490_g2_be.dto.response.TransactionStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TrendPointResponse;
import com.capstone.su26_sep490_g2_be.entity.FacebookPost;
import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.FacebookPostRepository;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentFormatDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentResultRepository;
import com.capstone.su26_sep490_g2_be.service.AnalyticsService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final int DEFAULT_RANGE_MONTHS = 12;
	private static final int TOP_N = 10;
	private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM");
	private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MM/yyyy");
	private static final String UNKNOWN_BRANCH_LABEL = "Không rõ chi nhánh";

	private final TournamentRepository tournamentRepository;
	private final RegistrationRepository registrationRepository;
	private final ParticipantRepository participantRepository;
	private final PaymentRepository paymentRepository;
	private final MatchRepository matchRepository;
	private final TournamentResultRepository tournamentResultRepository;
	private final FacebookPostRepository facebookPostRepository;
	private final GameTypeDefinitionRepository gameTypeDefinitionRepository;
	private final TournamentFormatDefinitionRepository tournamentFormatDefinitionRepository;

	@Override
	public AnalyticsOverviewResponse buildOverview(Long ownerId, Instant fromParam, Instant toParam, List<Long> branchIds) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Tournament> allTournaments = ownerTournaments(ownerId, branchIds);
		List<Long> tournamentIds = ids(allTournaments);
		Map<Long, Tournament> tournamentsById = allTournaments.stream()
				.collect(Collectors.toMap(Tournament::getId, Function.identity()));

		List<Payment> payments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn);
		List<Payment> successInRange = successPaymentsInRange(payments, from, to);
		BigDecimal totalRevenue = sumAmount(successInRange);

		Duration span = Duration.between(from, to);
		Instant prevFrom = from.minus(span);
		List<Payment> successPrevPeriod = successPaymentsInRange(payments, prevFrom, from);
		BigDecimal prevRevenue = sumAmount(successPrevPeriod);
		Double growthPct = growthPct(totalRevenue, prevRevenue);

		List<Tournament> tournamentsInRange = tournamentsInRange(allTournaments, from, to);
		List<Participant> participants = scoped(tournamentIds, participantRepository::findByTournamentIdIn);
		Map<Long, Long> activeParticipantsByTournament = participants.stream()
				.filter(p -> ParticipantStatus.ACTIVE.getValue().equals(p.getStatus()))
				.collect(Collectors.groupingBy(p -> p.getTournament().getId(), Collectors.counting()));

		Double avgFillRate = tournamentsInRange.isEmpty() ? 0.0 : tournamentsInRange.stream()
				.filter(t -> t.getMaxParticipants() != null && t.getMaxParticipants() > 0)
				.mapToDouble(t -> activeParticipantsByTournament.getOrDefault(t.getId(), 0L) * 100.0 / t.getMaxParticipants())
				.average()
				.orElse(0.0);

		List<Registration> registrations = scoped(tournamentIds, registrationRepository::findByTournamentIdIn);
		long uniquePlayers = registrations.stream()
				.filter(r -> inRange(r.getCreatedAt(), from, to))
				.map(Registration::getUser)
				.filter(Objects::nonNull)
				.map(u -> u.getId())
				.distinct()
				.count();

		Map<Long, BigDecimal> revenueByTournament = revenueByTournament(successInRange);
		Map.Entry<Long, BigDecimal> topEntry = revenueByTournament.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.orElse(null);
		String topTournamentName = topEntry != null && tournamentsById.containsKey(topEntry.getKey())
				? tournamentsById.get(topEntry.getKey()).getName() : null;
		BigDecimal topTournamentRevenue = topEntry != null ? topEntry.getValue() : BigDecimal.ZERO;

		Map<String, BigDecimal> revenueByBranchName = new LinkedHashMap<>();
		for (Map.Entry<Long, BigDecimal> e : revenueByTournament.entrySet()) {
			Tournament t = tournamentsById.get(e.getKey());
			String label = branchLabel(t);
			revenueByBranchName.merge(label, e.getValue(), BigDecimal::add);
		}
		String topBranchName = revenueByBranchName.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(null);
		long branchCount = allTournaments.stream()
				.map(Tournament::getBranch)
				.filter(Objects::nonNull)
				.map(b -> b.getId())
				.distinct()
				.count();

		return AnalyticsOverviewResponse.builder()
				.totalRevenue(totalRevenue)
				.revenuePrevPeriod(prevRevenue)
				.revenueGrowthPct(growthPct)
				.totalTournaments(tournamentsInRange.size())
				.avgFillRatePct(round1(avgFillRate))
				.totalUniquePlayers(uniquePlayers)
				.topTournamentName(topTournamentName)
				.topTournamentRevenue(topTournamentRevenue)
				.topBranchName(topBranchName)
				.branchCount((int) branchCount)
				.build();
	}

	@Override
	public RevenueBreakdownResponse buildRevenueBreakdown(Long ownerId, Instant fromParam, Instant toParam, String granularity, List<Long> branchIds) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Tournament> allTournaments = ownerTournaments(ownerId, branchIds);
		List<Long> tournamentIds = ids(allTournaments);
		Map<Long, Tournament> tournamentsById = allTournaments.stream()
				.collect(Collectors.toMap(Tournament::getId, Function.identity()));

		List<Payment> payments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn);
		List<Payment> successInRange = successPaymentsInRange(payments, from, to);

		List<TrendPointResponse> trend = buildTrend(successInRange, from, to, granularity);

		Map<Long, BigDecimal> byTournamentAmount = revenueByTournament(successInRange);
		Map<Long, Long> byTournamentCount = successInRange.stream()
				.filter(p -> p.getRegistration() != null && p.getRegistration().getTournament() != null)
				.collect(Collectors.groupingBy(p -> p.getRegistration().getTournament().getId(), Collectors.counting()));

		List<LabeledAmountItem> byTournament = byTournamentAmount.entrySet().stream()
				.sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
				.limit(TOP_N)
				.map(e -> LabeledAmountItem.builder()
						.id(e.getKey())
						.label(tournamentsById.containsKey(e.getKey()) ? tournamentsById.get(e.getKey()).getName() : "—")
						.amount(e.getValue())
						.count(byTournamentCount.getOrDefault(e.getKey(), 0L))
						.build())
				.toList();

		Map<String, BigDecimal> byBranchAmount = new LinkedHashMap<>();
		Map<String, Long> byBranchCount = new LinkedHashMap<>();
		for (Payment p : successInRange) {
			if (p.getRegistration() == null || p.getRegistration().getTournament() == null) continue;
			Tournament t = tournamentsById.get(p.getRegistration().getTournament().getId());
			String label = branchLabel(t);
			byBranchAmount.merge(label, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
			byBranchCount.merge(label, 1L, Long::sum);
		}
		List<LabeledAmountItem> byBranch = byBranchAmount.entrySet().stream()
				.sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
				.map(e -> LabeledAmountItem.builder()
						.label(e.getKey())
						.amount(e.getValue())
						.count(byBranchCount.getOrDefault(e.getKey(), 0L))
						.build())
				.toList();

		Map<String, Long> methodCounts = successInRange.stream()
				.map(Payment::getPaymentMethod)
				.filter(Objects::nonNull)
				.collect(Collectors.groupingBy(m -> m, Collectors.counting()));
		List<StatusCountItem> byPaymentMethod = methodCounts.entrySet().stream()
				.map(e -> StatusCountItem.builder().status(e.getKey()).label(e.getKey()).count(e.getValue()).build())
				.sorted(Comparator.comparingLong(StatusCountItem::getCount).reversed())
				.toList();

		return RevenueBreakdownResponse.builder()
				.trend(trend)
				.byBranch(byBranch)
				.byTournament(byTournament)
				.byPaymentMethod(byPaymentMethod)
				.build();
	}

	@Override
	public List<TournamentPerformanceItem> buildTournamentPerformance(Long ownerId, Instant fromParam, Instant toParam, List<Long> branchIds) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		// Danh sách giải đấu KHÔNG lọc theo khoảng thời gian — "hiệu suất giải đấu" phải liệt kê đủ
		// mọi giải của owner; chỉ riêng doanh thu mới scope theo [from,to] để khớp với phần còn lại
		// của trang (KPI, xu hướng doanh thu...).
		List<Tournament> allTournaments = ownerTournaments(ownerId, branchIds);
		List<Tournament> tournaments = allTournaments;
		List<Long> tournamentIds = ids(allTournaments);

		List<Payment> payments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn);
		Map<Long, BigDecimal> revenueByTournament = revenueByTournament(successPaymentsInRange(payments, from, to));
		// Lợi nhuận (doanh thu - tiền thưởng) dùng doanh thu TOÀN THỜI GIAN, không scope theo kỳ đang
		// lọc — giải thưởng là chi phí cố định 1 lần của cả giải, so với doanh thu 1 khoảng ngắn sẽ sai lệch.
		Map<Long, BigDecimal> allTimeRevenueByTournament = revenueByTournament(
				payments.stream().filter(p -> PaymentStatus.SUCCESS.getValue().equals(p.getStatus())).toList());

		List<Participant> participants = scoped(tournamentIds, participantRepository::findByTournamentIdIn);
		Map<Long, Long> activeParticipantsByTournament = participants.stream()
				.filter(p -> ParticipantStatus.ACTIVE.getValue().equals(p.getStatus()))
				.collect(Collectors.groupingBy(p -> p.getTournament().getId(), Collectors.counting()));

		List<Match> matches = scoped(tournamentIds, matchRepository::findByTournamentIdIn);
		Map<Long, long[]> matchCountsByTournament = new LinkedHashMap<>();
		for (Match m : matches) {
			Long tid = m.getTournament().getId();
			long[] counts = matchCountsByTournament.computeIfAbsent(tid, k -> new long[2]);
			counts[0]++;
			if (isResolved(m.getStatus())) counts[1]++;
		}

		return tournaments.stream()
				.map(t -> {
					long participantCount = activeParticipantsByTournament.getOrDefault(t.getId(), 0L);
					Double fillRate = t.getMaxParticipants() != null && t.getMaxParticipants() > 0
							? round1(participantCount * 100.0 / t.getMaxParticipants()) : null;
					long[] mc = matchCountsByTournament.getOrDefault(t.getId(), new long[2]);
					Double completionRate = mc[0] > 0 ? round1(mc[1] * 100.0 / mc[0]) : 0.0;
					TournamentStatus statusEnum = safeStatus(t.getStatus());
					BigDecimal prizePool = t.getPrizePool() != null ? t.getPrizePool() : BigDecimal.ZERO;
					BigDecimal netProfit = allTimeRevenueByTournament.getOrDefault(t.getId(), BigDecimal.ZERO).subtract(prizePool);
					return TournamentPerformanceItem.builder()
							.id(t.getId())
							.name(t.getName())
							.branchName(branchLabel(t))
							.participants(participantCount)
							.maxParticipants(t.getMaxParticipants())
							.fillRatePct(fillRate)
							.revenue(revenueByTournament.getOrDefault(t.getId(), BigDecimal.ZERO))
							.prizePool(prizePool)
							.netProfit(netProfit)
							.status(t.getStatus())
							.statusLabel(statusEnum != null ? statusEnum.getDisplayName() : t.getStatus())
							.completionRatePct(completionRate)
							.build();
				})
				.sorted(Comparator.comparing(TournamentPerformanceItem::getRevenue).reversed())
				.toList();
	}

	@Override
	public List<PlayerLeaderboardItem> buildPlayerLeaderboard(Long ownerId, Instant fromParam, Instant toParam, List<Long> branchIds) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds));
		List<TournamentResult> results = scoped(tournamentIds, tournamentResultRepository::findByTournamentIdIn);

		record Agg(String playerName, long tournamentsPlayed, long championCount, long top3Count,
				BigDecimal totalPrizeAmount, long totalPoints) {
		}

		Map<Long, List<TournamentResult>> byUser = results.stream()
				.filter(r -> inRange(r.getRecordedAt(), from, to))
				.filter(r -> r.getParticipant().getRegistration() != null
						&& r.getParticipant().getRegistration().getUser() != null)
				.collect(Collectors.groupingBy(r -> r.getParticipant().getRegistration().getUser().getId()));

		return byUser.entrySet().stream()
				.map(e -> {
					List<TournamentResult> rs = e.getValue();
					String name = rs.get(rs.size() - 1).getParticipant().getDisplayName();
					long champion = rs.stream().filter(r -> r.getFinalRank() != null && r.getFinalRank() == 1).count();
					long top3 = rs.stream().filter(r -> r.getFinalRank() != null && r.getFinalRank() <= 3).count();
					BigDecimal prize = rs.stream()
							.map(r -> r.getPrizeAmount() != null ? r.getPrizeAmount() : BigDecimal.ZERO)
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					long points = rs.stream().mapToLong(r -> r.getPointsEarned() != null ? r.getPointsEarned() : 0).sum();
					return PlayerLeaderboardItem.builder()
							.userId(e.getKey())
							.playerName(name)
							.tournamentsPlayed(rs.size())
							.championCount(champion)
							.top3Count(top3)
							.totalPrizeAmount(prize)
							.totalPoints(points)
							.build();
				})
				.sorted(Comparator.comparing(PlayerLeaderboardItem::getTotalPrizeAmount).reversed()
						.thenComparing(Comparator.comparingLong(PlayerLeaderboardItem::getTotalPoints).reversed()))
				.limit(TOP_N)
				.toList();
	}

	@Override
	public SocialEngagementResponse buildSocialEngagement(Long ownerId, Instant fromParam, Instant toParam, List<Long> branchIds) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds));
		List<FacebookPost> posts = scoped(tournamentIds, facebookPostRepository::findByTournamentIdIn).stream()
				.filter(p -> inRange(p.getPostedAt(), from, to))
				.toList();

		long likes = sumInt(posts, FacebookPost::getLikesCount);
		long comments = sumInt(posts, FacebookPost::getCommentsCount);
		long shares = sumInt(posts, FacebookPost::getSharesCount);
		long reach = sumInt(posts, FacebookPost::getReach);

		FacebookPost topPost = posts.stream()
				.max(Comparator.comparingInt(p -> p.getReach() != null ? p.getReach() : 0))
				.orElse(null);

		return SocialEngagementResponse.builder()
				.totalPosts(posts.size())
				.totalLikes(likes)
				.totalComments(comments)
				.totalShares(shares)
				.totalReach(reach)
				.topPostTournamentName(topPost != null && topPost.getTournament() != null
						? topPost.getTournament().getName() : null)
				.topPostReach(topPost != null && topPost.getReach() != null ? topPost.getReach() : 0)
				.build();
	}

	@Override
	public RegistrationStatsResponse buildRegistrationFunnel(Long ownerId, Instant fromParam, Instant toParam, String granularity, List<Long> branchIds) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds));
		List<Registration> registrations = scoped(tournamentIds, registrationRepository::findByTournamentIdIn).stream()
				.filter(r -> inRange(r.getCreatedAt(), from, to))
				.toList();

		Map<String, Long> counts = registrations.stream()
				.collect(Collectors.groupingBy(Registration::getStatus, Collectors.counting()));

		List<StatusCountItem> byStatus = Arrays.stream(RegistrationStatus.values())
				.map(s -> StatusCountItem.builder()
						.status(s.getValue())
						.label(s.getDisplayName())
						.count(counts.getOrDefault(s.getValue(), 0L))
						.build())
				.filter(i -> i.getCount() > 0)
				.toList();

		List<TrendPointResponse> trend = countTrend(
				registrations.stream().map(Registration::getCreatedAt).toList(), from, to, granularity);

		return RegistrationStatsResponse.builder()
				.total(registrations.size())
				.pending(counts.getOrDefault(RegistrationStatus.PENDING_PAYMENT.getValue(), 0L))
				.approved(counts.getOrDefault(RegistrationStatus.APPROVED.getValue(), 0L))
				.rejected(counts.getOrDefault(RegistrationStatus.REJECTED.getValue(), 0L))
				.cancelled(counts.getOrDefault(RegistrationStatus.CANCELLED.getValue(), 0L))
				.byStatus(byStatus)
				.monthlyTrend(trend)
				.build();
	}

	@Override
	public List<GameTypeBreakdownItem> buildGameTypeBreakdown(Long ownerId, Instant fromParam, Instant toParam, List<Long> branchIds) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Tournament> allTournaments = ownerTournaments(ownerId, branchIds);
		List<Tournament> tournaments = tournamentsInRange(allTournaments, from, to);
		List<Long> tournamentIds = ids(allTournaments);

		List<Payment> payments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn);
		Map<Long, BigDecimal> revenueByTournament = revenueByTournament(
				payments.stream().filter(p -> PaymentStatus.SUCCESS.getValue().equals(p.getStatus())).toList());

		List<Participant> participants = scoped(tournamentIds, participantRepository::findByTournamentIdIn);
		Map<Long, Long> activeParticipantsByTournament = participants.stream()
				.filter(p -> ParticipantStatus.ACTIVE.getValue().equals(p.getStatus()))
				.collect(Collectors.groupingBy(p -> p.getTournament().getId(), Collectors.counting()));

		Map<String, String> gameTypeNames = gameTypeDefinitionRepository.findAll().stream()
				.collect(Collectors.toMap(GameTypeDefinition::getCode, GameTypeDefinition::getName));

		Map<String, List<Tournament>> byGameType = tournaments.stream()
				.collect(Collectors.groupingBy(Tournament::getGameType));

		return byGameType.entrySet().stream()
				.map(e -> {
					String code = e.getKey();
					List<Tournament> ts = e.getValue();
					BigDecimal revenue = ts.stream()
							.map(t -> revenueByTournament.getOrDefault(t.getId(), BigDecimal.ZERO))
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					double avgFill = ts.stream()
							.filter(t -> t.getMaxParticipants() != null && t.getMaxParticipants() > 0)
							.mapToDouble(t -> activeParticipantsByTournament.getOrDefault(t.getId(), 0L) * 100.0 / t.getMaxParticipants())
							.average().orElse(0.0);
					return GameTypeBreakdownItem.builder()
							.code(code)
							.label(gameTypeNames.getOrDefault(code, code))
							.tournamentCount(ts.size())
							.totalRevenue(revenue)
							.avgFillRatePct(round1(avgFill))
							.build();
				})
				.sorted(Comparator.comparing(GameTypeBreakdownItem::getTotalRevenue).reversed())
				.toList();
	}

	@Override
	public PlayerGrowthResponse buildPlayerGrowth(Long ownerId, Instant fromParam, Instant toParam, String granularity, List<Long> branchIds) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds));
		List<Registration> allRegistrations = scoped(tournamentIds, registrationRepository::findByTournamentIdIn);

		Map<Long, Instant> firstRegistrationByUser = new LinkedHashMap<>();
		Map<Long, Long> registrationCountByUser = new LinkedHashMap<>();
		for (Registration r : allRegistrations) {
			if (r.getUser() == null || r.getCreatedAt() == null) continue;
			Long uid = r.getUser().getId();
			firstRegistrationByUser.merge(uid, r.getCreatedAt(), (a, b) -> a.isBefore(b) ? a : b);
			registrationCountByUser.merge(uid, 1L, Long::sum);
		}

		List<Instant> newPlayerTimestamps = firstRegistrationByUser.values().stream()
				.filter(t -> inRange(t, from, to))
				.toList();
		List<TrendPointResponse> trend = countTrend(newPlayerTimestamps, from, to, granularity);

		long activePlayers = allRegistrations.stream()
				.filter(r -> r.getUser() != null && inRange(r.getCreatedAt(), from, to))
				.map(r -> r.getUser().getId())
				.distinct()
				.count();

		long returningPlayers = allRegistrations.stream()
				.filter(r -> r.getUser() != null && inRange(r.getCreatedAt(), from, to))
				.map(r -> r.getUser().getId())
				.distinct()
				.filter(uid -> registrationCountByUser.getOrDefault(uid, 0L) > 1)
				.count();

		Double repeatRate = activePlayers > 0 ? round1(returningPlayers * 100.0 / activePlayers) : 0.0;

		return PlayerGrowthResponse.builder()
				.newPlayersTrend(trend)
				.activePlayerCount(activePlayers)
				.returningPlayerCount(returningPlayers)
				.repeatPlayerRatePct(repeatRate)
				.build();
	}

	@Override
	public TournamentAnalyticsDetailResponse buildTournamentDetail(Long ownerId, Long tournamentId, List<Long> branchIds) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (ownerId != null && (tournament.getCreatedBy() == null || !tournament.getCreatedBy().getId().equals(ownerId))) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		if (branchIds != null) {
			Long tournamentBranchId = tournament.getBranch() != null ? tournament.getBranch().getId() : null;
			if (tournamentBranchId == null || !branchIds.contains(tournamentBranchId)) {
				throw new BusinessException(ErrorCode.BRANCH_ACCESS_DENIED);
			}
		}

		List<Registration> registrations = registrationRepository.findByTournamentIdIn(List.of(tournamentId));
		List<Participant> participants = participantRepository.findByTournamentIdIn(List.of(tournamentId));
		List<Match> matches = matchRepository.findByTournamentIdIn(List.of(tournamentId));
		List<Payment> payments = paymentRepository.findByRegistration_Tournament_IdIn(List.of(tournamentId));

		Map<String, Long> regCounts = registrations.stream()
				.collect(Collectors.groupingBy(Registration::getStatus, Collectors.counting()));
		List<StatusCountItem> regByStatus = Arrays.stream(RegistrationStatus.values())
				.map(s -> StatusCountItem.builder().status(s.getValue()).label(s.getDisplayName())
						.count(regCounts.getOrDefault(s.getValue(), 0L)).build())
				.filter(i -> i.getCount() > 0).toList();
		Instant regFrom = registrations.stream().map(Registration::getCreatedAt).filter(Objects::nonNull)
				.min(Instant::compareTo).orElse(tournament.getCreatedAt());
		List<TrendPointResponse> regTrend = countTrend(
				registrations.stream().map(Registration::getCreatedAt).toList(),
				regFrom != null ? regFrom : tournament.getCreatedAt(), Instant.now(), "month");
		RegistrationStatsResponse registrationStats = RegistrationStatsResponse.builder()
				.total(registrations.size())
				.pending(regCounts.getOrDefault(RegistrationStatus.PENDING_PAYMENT.getValue(), 0L))
				.approved(regCounts.getOrDefault(RegistrationStatus.APPROVED.getValue(), 0L))
				.rejected(regCounts.getOrDefault(RegistrationStatus.REJECTED.getValue(), 0L))
				.cancelled(regCounts.getOrDefault(RegistrationStatus.CANCELLED.getValue(), 0L))
				.byStatus(regByStatus)
				.monthlyTrend(regTrend)
				.build();

		Map<String, Long> partCounts = participants.stream()
				.collect(Collectors.groupingBy(Participant::getStatus, Collectors.counting()));
		List<StatusCountItem> partByStatus = Arrays.stream(ParticipantStatus.values())
				.map(s -> StatusCountItem.builder().status(s.getValue()).label(s.getDisplayName())
						.count(partCounts.getOrDefault(s.getValue(), 0L)).build())
				.filter(i -> i.getCount() > 0).toList();
		ParticipantStatsResponse participantStats = ParticipantStatsResponse.builder()
				.total(participants.size())
				.active(partCounts.getOrDefault(ParticipantStatus.ACTIVE.getValue(), 0L))
				.withdrawn(partCounts.getOrDefault(ParticipantStatus.WITHDRAWN.getValue(), 0L))
				.byStatus(partByStatus)
				.build();

		Map<String, Long> matchCounts = matches.stream()
				.collect(Collectors.groupingBy(Match::getStatus, Collectors.counting()));
		long completedMatches = matches.stream().filter(m -> isResolved(m.getStatus())).count();
		List<StatusCountItem> matchByStatus = Arrays.stream(MatchStatus.values())
				.map(s -> StatusCountItem.builder().status(s.getValue()).label(s.getDisplayName())
						.count(matchCounts.getOrDefault(s.getValue(), 0L)).build())
				.filter(i -> i.getCount() > 0).toList();
		MatchStatsResponse matchStats = MatchStatsResponse.builder()
				.total(matches.size())
				.completed(completedMatches)
				.inProgress(matchCounts.getOrDefault(MatchStatus.IN_PROGRESS.getValue(), 0L))
				.pending(matchCounts.getOrDefault(MatchStatus.PENDING.getValue(), 0L))
				.completionRate(matches.isEmpty() ? 0.0 : completedMatches * 100.0 / matches.size())
				.byStatus(matchByStatus)
				.build();

		Instant paymentsFrom = payments.stream().map(this::effectiveTs).filter(Objects::nonNull)
				.min(Instant::compareTo).orElse(tournament.getCreatedAt());
		TransactionStatsResponse transactionStats = buildTransactionStats(payments,
				paymentsFrom != null ? paymentsFrom : tournament.getCreatedAt(), Instant.now(), "month");

		List<FacebookPost> posts = facebookPostRepository.findByTournamentIdOrderByPostedAtDesc(tournamentId);
		SocialEngagementResponse social = SocialEngagementResponse.builder()
				.totalPosts(posts.size())
				.totalLikes(sumInt(posts, FacebookPost::getLikesCount))
				.totalComments(sumInt(posts, FacebookPost::getCommentsCount))
				.totalShares(sumInt(posts, FacebookPost::getSharesCount))
				.totalReach(sumInt(posts, FacebookPost::getReach))
				.topPostTournamentName(tournament.getName())
				.topPostReach(posts.stream().mapToInt(p -> p.getReach() != null ? p.getReach() : 0).max().orElse(0))
				.build();

		long activeParticipants = partCounts.getOrDefault(ParticipantStatus.ACTIVE.getValue(), 0L);
		Double fillRate = tournament.getMaxParticipants() != null && tournament.getMaxParticipants() > 0
				? round1(activeParticipants * 100.0 / tournament.getMaxParticipants()) : null;
		TournamentStatus statusEnum = safeStatus(tournament.getStatus());

		GameTypeDefinition gameTypeDef = gameTypeDefinitionRepository.findById(tournament.getGameType()).orElse(null);
		TournamentFormatDefinition formatDef = tournamentFormatDefinitionRepository.findById(tournament.getFormat()).orElse(null);

		return TournamentAnalyticsDetailResponse.builder()
				.id(tournament.getId())
				.name(tournament.getName())
				.branchName(branchLabel(tournament))
				.gameTypeLabel(gameTypeDef != null ? gameTypeDef.getName() : tournament.getGameType())
				.formatLabel(formatDef != null ? formatDef.getName() : tournament.getFormat())
				.status(tournament.getStatus())
				.statusLabel(statusEnum != null ? statusEnum.getDisplayName() : tournament.getStatus())
				.entryFee(tournament.getEntryFee())
				.prizePool(tournament.getPrizePool())
				.prizeDescription(tournament.getPrizeDescription())
				.netProfit(transactionStats.getTotalAmount().subtract(
						tournament.getPrizePool() != null ? tournament.getPrizePool() : BigDecimal.ZERO))
				.maxParticipants(tournament.getMaxParticipants())
				.startAt(tournament.getStartAt())
				.endAt(tournament.getEndAt())
				.fillRatePct(fillRate)
				.transactionStats(transactionStats)
				.registrationStats(registrationStats)
				.participantStats(participantStats)
				.matchStats(matchStats)
				.social(social)
				.build();
	}

	@Override
	public TransactionStatsResponse buildTransactionStats(Long ownerId, Instant fromParam, Instant toParam, String granularity, List<Long> branchIds) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);
		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds));
		List<Payment> payments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn);
		return buildTransactionStats(payments, from, to, granularity);
	}

	@Override
	public PageResponse<PaymentHistoryResponse> listTransactions(
			Long ownerId, Long tournamentId, String status, Instant fromParam, Instant toParam, int page, int size, List<Long> branchIds) {
		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds));
		if (tournamentId != null && ownerId != null && !tournamentIds.contains(tournamentId)) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}

		Pageable pageable = PageRequest.of(Math.max(page, 0), size < 1 ? 10 : size,
				Sort.by(Sort.Direction.DESC, "createdAt"));
		Specification<Payment> spec = paymentSpec(tournamentIds, tournamentId, status, fromParam, toParam);
		Page<Payment> result = paymentRepository.findAll(spec, pageable);
		return PageResponse.of(result, this::toPaymentHistoryResponse);
	}

	private static final int MAX_MONTHLY_REPORT_SPAN = 60;

	@Override
	public MonthlyReportResponse buildMonthlyReport(Long ownerId, YearMonth fromParam, YearMonth toParam, List<Long> branchIds) {
		YearMonth to = toParam != null ? toParam : YearMonth.now(ZONE);
		YearMonth from = fromParam != null ? fromParam : to.minusMonths(11);
		if (from.isAfter(to)) {
			YearMonth tmp = from; from = to; to = tmp;
		}
		// Chặn range vô lý (vd nhập nhầm from=1990) để tránh sinh hàng chục nghìn dòng tháng rỗng.
		if (from.until(to, java.time.temporal.ChronoUnit.MONTHS) > MAX_MONTHLY_REPORT_SPAN) {
			from = to.minusMonths(MAX_MONTHLY_REPORT_SPAN);
		}

		List<Tournament> allTournaments = ownerTournaments(ownerId, branchIds);
		List<Long> tournamentIds = ids(allTournaments);

		List<Payment> success = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn).stream()
				.filter(p -> PaymentStatus.SUCCESS.getValue().equals(p.getStatus()))
				.toList();
		List<Registration> registrations = scoped(tournamentIds, registrationRepository::findByTournamentIdIn);

		Map<YearMonth, BigDecimal> revenueByMonth = new LinkedHashMap<>();
		Map<YearMonth, Long> txnCountByMonth = new LinkedHashMap<>();
		for (Payment p : success) {
			Instant ts = effectiveTs(p);
			if (ts == null) continue;
			YearMonth ym = YearMonth.from(ts.atZone(ZONE));
			revenueByMonth.merge(ym, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
			txnCountByMonth.merge(ym, 1L, Long::sum);
		}

		Map<YearMonth, Long> newTournamentsByMonth = new LinkedHashMap<>();
		for (Tournament t : allTournaments) {
			if (t.getCreatedAt() == null) continue;
			YearMonth ym = YearMonth.from(t.getCreatedAt().atZone(ZONE));
			newTournamentsByMonth.merge(ym, 1L, Long::sum);
		}

		Map<YearMonth, Long> newRegistrationsByMonth = new LinkedHashMap<>();
		for (Registration r : registrations) {
			if (r.getCreatedAt() == null) continue;
			YearMonth ym = YearMonth.from(r.getCreatedAt().atZone(ZONE));
			newRegistrationsByMonth.merge(ym, 1L, Long::sum);
		}

		List<MonthlyReportItem> months = new ArrayList<>();
		BigDecimal totalRevenue = BigDecimal.ZERO;
		long totalTxn = 0, totalNewT = 0, totalNewR = 0;
		for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
			BigDecimal rev = revenueByMonth.getOrDefault(ym, BigDecimal.ZERO);
			long txn = txnCountByMonth.getOrDefault(ym, 0L);
			long newT = newTournamentsByMonth.getOrDefault(ym, 0L);
			long newR = newRegistrationsByMonth.getOrDefault(ym, 0L);
			months.add(MonthlyReportItem.builder()
					.year(ym.getYear())
					.month(ym.getMonthValue())
					.monthLabel("Tháng " + ym.getMonthValue() + "/" + ym.getYear())
					.revenue(rev)
					.transactionCount(txn)
					.newTournaments(newT)
					.newRegistrations(newR)
					.build());
			totalRevenue = totalRevenue.add(rev);
			totalTxn += txn;
			totalNewT += newT;
			totalNewR += newR;
		}

		return MonthlyReportResponse.builder()
				.totalRevenue(totalRevenue)
				.totalTransactions(totalTxn)
				.totalNewTournaments(totalNewT)
				.totalNewRegistrations(totalNewR)
				.months(months)
				.build();
	}

	private Specification<Payment> paymentSpec(
			List<Long> tournamentIds, Long tournamentId, String status, Instant from, Instant to) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			var tournamentJoin = root.join("registration", JoinType.INNER).join("tournament", JoinType.INNER);

			if (tournamentId != null) {
				predicates.add(cb.equal(tournamentJoin.get("id"), tournamentId));
			} else if (!tournamentIds.isEmpty()) {
				predicates.add(tournamentJoin.get("id").in(tournamentIds));
			} else {
				predicates.add(cb.disjunction());
			}
			if (status != null && !status.isBlank()) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
			}
			if (to != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	private PaymentHistoryResponse toPaymentHistoryResponse(Payment p) {
		String tournamentName = null;
		Long tid = null;
		if (p.getRegistration() != null && p.getRegistration().getTournament() != null) {
			tournamentName = p.getRegistration().getTournament().getName();
			tid = p.getRegistration().getTournament().getId();
		}
		PaymentStatus statusEnum = null;
		try {
			statusEnum = PaymentStatus.valueOf(p.getStatus());
		} catch (IllegalArgumentException | NullPointerException ignored) {
			// giữ statusLabel = null, FE fallback hiển thị status thô
		}
		return PaymentHistoryResponse.builder()
				.id(p.getId())
				.registrationId(p.getRegistration() != null ? p.getRegistration().getId() : null)
				.tournamentId(tid)
				.tournamentName(tournamentName)
				.playerName(p.getRegistration() != null ? p.getRegistration().getPlayerFullName() : null)
				.amount(p.getAmount())
				.paymentMethod(p.getPaymentMethod())
				.status(p.getStatus())
				.statusLabel(statusEnum != null ? statusEnum.getDisplayName() : p.getStatus())
				.transactionCode(p.getTransactionCode())
				.checkoutUrl(p.getCheckoutUrl())
				.paidAt(p.getPaidAt())
				.createdAt(p.getCreatedAt())
				.build();
	}

	// ──────────────────────────── shared helpers ────────────────────────────

	/**
	 * branchIds null = không lọc theo chi nhánh (Owner mặc định xem toàn chuỗi); khác null = chỉ giữ
	 * lại các giải đấu thuộc 1 trong các chi nhánh đó (dùng để Owner lọc theo 1 chi nhánh cụ thể, hoặc
	 * để giới hạn Manager về đúng (các) chi nhánh họ được cấp quyền).
	 */
	private List<Tournament> ownerTournaments(Long ownerId, List<Long> branchIds) {
		// KHÔNG được coi ownerId == null là "không lọc" rồi trả toàn bộ dữ liệu của mọi chủ sân —
		// mọi endpoint gọi tới đây đều bắt buộc phải có ownerId xác định (Owner/Manager tự xem của mình).
		if (ownerId == null) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		List<Tournament> tournaments = tournamentRepository.findByCreatedById(ownerId);
		if (branchIds == null) {
			return tournaments;
		}
		return tournaments.stream()
				.filter(t -> t.getBranch() != null && branchIds.contains(t.getBranch().getId()))
				.toList();
	}

	private List<Long> ids(List<Tournament> tournaments) {
		return tournaments.stream().map(Tournament::getId).toList();
	}

	private <T> List<T> scoped(List<Long> tournamentIds, Function<List<Long>, List<T>> fetcher) {
		return tournamentIds.isEmpty() ? List.of() : fetcher.apply(tournamentIds);
	}

	private Instant defaultFrom(Instant from) {
		return from != null ? from : Instant.now().atZone(ZONE).minusMonths(DEFAULT_RANGE_MONTHS).toInstant();
	}

	private Instant defaultTo(Instant to) {
		return to != null ? to : Instant.now();
	}

	/** Chặn client truyền from/to cách nhau nhiều năm — tránh dựng trend map hàng chục nghìn điểm/query không giới hạn mỗi request. */
	private static final long MAX_RANGE_DAYS = 366L * 3;

	private Instant clampRangeStart(Instant from, Instant to) {
		if (java.time.Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
			return to.minus(MAX_RANGE_DAYS, java.time.temporal.ChronoUnit.DAYS);
		}
		return from;
	}

	private boolean inRange(Instant t, Instant from, Instant to) {
		return t != null && !t.isBefore(from) && !t.isAfter(to);
	}

	/** Tournament được coi là "diễn ra" trong khoảng nếu startAt rơi vào range; fallback createdAt nếu chưa có lịch. */
	private List<Tournament> tournamentsInRange(List<Tournament> tournaments, Instant from, Instant to) {
		return tournaments.stream()
				.filter(t -> inRange(t.getStartAt() != null ? t.getStartAt() : t.getCreatedAt(), from, to))
				.toList();
	}

	private List<Payment> successPaymentsInRange(List<Payment> payments, Instant from, Instant to) {
		return payments.stream()
				.filter(p -> PaymentStatus.SUCCESS.getValue().equals(p.getStatus()))
				.filter(p -> inRange(effectiveTs(p), from, to))
				.toList();
	}

	private Instant effectiveTs(Payment p) {
		return p.getPaidAt() != null ? p.getPaidAt() : p.getCreatedAt();
	}

	private BigDecimal sumAmount(List<Payment> payments) {
		return payments.stream()
				.map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private Map<Long, BigDecimal> revenueByTournament(List<Payment> successPayments) {
		Map<Long, BigDecimal> map = new LinkedHashMap<>();
		for (Payment p : successPayments) {
			if (p.getRegistration() == null || p.getRegistration().getTournament() == null) continue;
			Long tid = p.getRegistration().getTournament().getId();
			map.merge(tid, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
		}
		return map;
	}

	/** Thống kê giao dịch đầy đủ (mọi trạng thái) — dùng chung cho endpoint tổng quan lẫn drill-down 1 giải. */
	private TransactionStatsResponse buildTransactionStats(List<Payment> payments, Instant from, Instant to, String granularity) {
		List<Payment> inRangePayments = payments.stream()
				.filter(p -> inRange(effectiveTs(p), from, to))
				.toList();

		Map<String, Long> counts = inRangePayments.stream()
				.collect(Collectors.groupingBy(Payment::getStatus, Collectors.counting()));

		List<StatusCountItem> byStatus = Arrays.stream(PaymentStatus.values())
				.map(s -> StatusCountItem.builder().status(s.getValue()).label(s.getDisplayName())
						.count(counts.getOrDefault(s.getValue(), 0L)).build())
				.filter(i -> i.getCount() > 0)
				.toList();

		List<Payment> success = inRangePayments.stream()
				.filter(p -> PaymentStatus.SUCCESS.getValue().equals(p.getStatus())).toList();
		BigDecimal totalAmount = sumAmount(success);
		BigDecimal avgValue = !success.isEmpty()
				? totalAmount.divide(BigDecimal.valueOf(success.size()), 0, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		double avgConversionMinutes = success.stream()
				.filter(p -> p.getPaidAt() != null && p.getCreatedAt() != null)
				.mapToLong(p -> Duration.between(p.getCreatedAt(), p.getPaidAt()).toMinutes())
				.average().orElse(0.0);

		Map<String, Long> methodCounts = inRangePayments.stream()
				.map(Payment::getPaymentMethod).filter(Objects::nonNull)
				.collect(Collectors.groupingBy(m -> m, Collectors.counting()));
		List<StatusCountItem> byMethod = methodCounts.entrySet().stream()
				.map(e -> StatusCountItem.builder().status(e.getKey()).label(e.getKey()).count(e.getValue()).build())
				.sorted(Comparator.comparingLong(StatusCountItem::getCount).reversed())
				.toList();

		List<TrendPointResponse> trend = buildTrend(success, from, to, granularity);

		long total = inRangePayments.size();
		long successCount = counts.getOrDefault(PaymentStatus.SUCCESS.getValue(), 0L);

		return TransactionStatsResponse.builder()
				.totalTransactions(total)
				.successCount(successCount)
				.pendingCount(counts.getOrDefault(PaymentStatus.PENDING.getValue(), 0L))
				.failedCount(counts.getOrDefault(PaymentStatus.FAILED.getValue(), 0L))
				.cancelledCount(counts.getOrDefault(PaymentStatus.CANCELLED.getValue(), 0L))
				.successRatePct(total > 0 ? round1(successCount * 100.0 / total) : 0.0)
				.totalAmount(totalAmount)
				.avgTransactionValue(avgValue)
				.avgConversionMinutes(round1(avgConversionMinutes))
				.byStatus(byStatus)
				.byMethod(byMethod)
				.trend(trend)
				.build();
	}

	private String branchLabel(Tournament t) {
		if (t == null) return UNKNOWN_BRANCH_LABEL;
		if (t.getBranch() != null) return t.getBranch().getName();
		if (t.getVenueName() != null && !t.getVenueName().isBlank()) return t.getVenueName();
		return UNKNOWN_BRANCH_LABEL;
	}

	private Double growthPct(BigDecimal current, BigDecimal previous) {
		if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
			return current != null && current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
		}
		return round1(current.subtract(previous)
				.divide(previous, 4, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.doubleValue());
	}

	private Double round1(double v) {
		return Math.round(v * 10.0) / 10.0;
	}

	private boolean isResolved(String status) {
		try {
			return MatchStatus.valueOf(status).isResolved();
		} catch (IllegalArgumentException | NullPointerException e) {
			return false;
		}
	}

	private TournamentStatus safeStatus(String status) {
		try {
			return TournamentStatus.valueOf(status);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}

	private long sumInt(List<FacebookPost> posts, Function<FacebookPost, Integer> extractor) {
		return posts.stream().mapToLong(p -> {
			Integer v = extractor.apply(p);
			return v != null ? v : 0;
		}).sum();
	}

	// ──────────────────────────── trend bucketing ────────────────────────────

	private List<TrendPointResponse> buildTrend(List<Payment> successPayments, Instant from, Instant to, String granularity) {
		String g = normalizeGranularity(granularity);
		return switch (g) {
			case "day" -> dailyTrend(successPayments, from, to);
			case "week" -> weeklyTrend(successPayments, from, to);
			default -> monthlyTrend(successPayments, from, to);
		};
	}

	private String normalizeGranularity(String granularity) {
		if (granularity == null) return "month";
		String g = granularity.trim().toLowerCase(Locale.ROOT);
		return (g.equals("day") || g.equals("week") || g.equals("month")) ? g : "month";
	}

	private List<TrendPointResponse> dailyTrend(List<Payment> payments, Instant from, Instant to) {
		LocalDate start = LocalDate.ofInstant(from, ZONE);
		LocalDate end = LocalDate.ofInstant(to, ZONE);

		Map<LocalDate, BigDecimal> sums = new LinkedHashMap<>();
		Map<LocalDate, Long> counts = new LinkedHashMap<>();
		for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
			sums.put(d, BigDecimal.ZERO);
			counts.put(d, 0L);
		}
		for (Payment p : payments) {
			LocalDate d = LocalDate.ofInstant(effectiveTs(p), ZONE);
			if (!sums.containsKey(d)) continue;
			sums.merge(d, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
			counts.merge(d, 1L, Long::sum);
		}
		return sums.keySet().stream()
				.map(d -> TrendPointResponse.builder().period(d.format(DAY_FMT)).count(counts.get(d)).amount(sums.get(d)).build())
				.toList();
	}

	private List<TrendPointResponse> weeklyTrend(List<Payment> payments, Instant from, Instant to) {
		WeekFields wf = WeekFields.of(Locale.forLanguageTag("vi-VN"));
		LocalDate start = mondayOf(LocalDate.ofInstant(from, ZONE), wf);
		LocalDate end = LocalDate.ofInstant(to, ZONE);

		Map<LocalDate, BigDecimal> sums = new LinkedHashMap<>();
		Map<LocalDate, Long> counts = new LinkedHashMap<>();
		for (LocalDate d = start; !d.isAfter(end); d = d.plusWeeks(1)) {
			sums.put(d, BigDecimal.ZERO);
			counts.put(d, 0L);
		}
		for (Payment p : payments) {
			LocalDate weekStart = mondayOf(LocalDate.ofInstant(effectiveTs(p), ZONE), wf);
			if (!sums.containsKey(weekStart)) continue;
			sums.merge(weekStart, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
			counts.merge(weekStart, 1L, Long::sum);
		}
		return sums.keySet().stream()
				.map(d -> TrendPointResponse.builder().period(d.format(DAY_FMT)).count(counts.get(d)).amount(sums.get(d)).build())
				.toList();
	}

	private LocalDate mondayOf(LocalDate date, WeekFields wf) {
		return date.with(wf.dayOfWeek(), 1);
	}

	private List<TrendPointResponse> monthlyTrend(List<Payment> payments, Instant from, Instant to) {
		YearMonth start = YearMonth.from(from.atZone(ZONE));
		YearMonth end = YearMonth.from(to.atZone(ZONE));

		Map<YearMonth, BigDecimal> sums = new LinkedHashMap<>();
		Map<YearMonth, Long> counts = new LinkedHashMap<>();
		for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
			sums.put(ym, BigDecimal.ZERO);
			counts.put(ym, 0L);
		}
		for (Payment p : payments) {
			YearMonth ym = YearMonth.from(effectiveTs(p).atZone(ZONE));
			if (!sums.containsKey(ym)) continue;
			sums.merge(ym, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
			counts.merge(ym, 1L, Long::sum);
		}
		return sums.keySet().stream()
				.map(ym -> TrendPointResponse.builder().period(ym.format(MONTH_FMT)).count(counts.get(ym)).amount(sums.get(ym)).build())
				.toList();
	}

	/** Trend đếm số lượng thuần (không có amount) — dùng cho phễu đăng ký và tăng trưởng người chơi mới. */
	private List<TrendPointResponse> countTrend(List<Instant> timestamps, Instant from, Instant to, String granularity) {
		String g = normalizeGranularity(granularity);
		return switch (g) {
			case "day" -> dailyCountTrend(timestamps, from, to);
			case "week" -> weeklyCountTrend(timestamps, from, to);
			default -> monthlyCountTrend(timestamps, from, to);
		};
	}

	private List<TrendPointResponse> dailyCountTrend(List<Instant> timestamps, Instant from, Instant to) {
		LocalDate start = LocalDate.ofInstant(from, ZONE);
		LocalDate end = LocalDate.ofInstant(to, ZONE);
		Map<LocalDate, Long> counts = new LinkedHashMap<>();
		for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) counts.put(d, 0L);
		for (Instant ts : timestamps) {
			if (ts == null) continue;
			LocalDate d = LocalDate.ofInstant(ts, ZONE);
			if (counts.containsKey(d)) counts.merge(d, 1L, Long::sum);
		}
		return counts.entrySet().stream()
				.map(e -> TrendPointResponse.builder().period(e.getKey().format(DAY_FMT)).count(e.getValue()).build())
				.toList();
	}

	private List<TrendPointResponse> weeklyCountTrend(List<Instant> timestamps, Instant from, Instant to) {
		WeekFields wf = WeekFields.of(Locale.forLanguageTag("vi-VN"));
		LocalDate start = mondayOf(LocalDate.ofInstant(from, ZONE), wf);
		LocalDate end = LocalDate.ofInstant(to, ZONE);
		Map<LocalDate, Long> counts = new LinkedHashMap<>();
		for (LocalDate d = start; !d.isAfter(end); d = d.plusWeeks(1)) counts.put(d, 0L);
		for (Instant ts : timestamps) {
			if (ts == null) continue;
			LocalDate weekStart = mondayOf(LocalDate.ofInstant(ts, ZONE), wf);
			if (counts.containsKey(weekStart)) counts.merge(weekStart, 1L, Long::sum);
		}
		return counts.entrySet().stream()
				.map(e -> TrendPointResponse.builder().period(e.getKey().format(DAY_FMT)).count(e.getValue()).build())
				.toList();
	}

	private List<TrendPointResponse> monthlyCountTrend(List<Instant> timestamps, Instant from, Instant to) {
		YearMonth start = YearMonth.from(from.atZone(ZONE));
		YearMonth end = YearMonth.from(to.atZone(ZONE));
		Map<YearMonth, Long> counts = new LinkedHashMap<>();
		for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) counts.put(ym, 0L);
		for (Instant ts : timestamps) {
			if (ts == null) continue;
			YearMonth ym = YearMonth.from(ts.atZone(ZONE));
			if (counts.containsKey(ym)) counts.merge(ym, 1L, Long::sum);
		}
		return counts.entrySet().stream()
				.map(e -> TrendPointResponse.builder().period(e.getKey().format(MONTH_FMT)).count(e.getValue()).build())
				.toList();
	}
}
