package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.AnalyticsQueryRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SavedViewRequest;
import com.capstone.su26_sep490_g2_be.dto.response.AnalyticsOverviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.AnalyticsQueryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.AtRiskPlayerItem;
import com.capstone.su26_sep490_g2_be.dto.response.GameTypeBreakdownItem;
import com.capstone.su26_sep490_g2_be.dto.response.InsightItem;
import com.capstone.su26_sep490_g2_be.dto.response.LabeledAmountItem;
import com.capstone.su26_sep490_g2_be.dto.response.MatchStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportItem;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PaymentHistoryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerAnalyticsDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerGrowthResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerLeaderboardItem;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerRetentionResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RevenueBreakdownResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SavedViewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SocialEngagementResponse;
import com.capstone.su26_sep490_g2_be.dto.response.StatusCountItem;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentAnalyticsDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentPerformanceItem;
import com.capstone.su26_sep490_g2_be.dto.response.TransactionStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TrendPointResponse;
import com.capstone.su26_sep490_g2_be.entity.AnalyticsSavedView;
import com.capstone.su26_sep490_g2_be.entity.FacebookPost;
import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.AnalyticsDimension;
import com.capstone.su26_sep490_g2_be.enums.AnalyticsMetric;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.AnalyticsSavedViewRepository;
import com.capstone.su26_sep490_g2_be.repository.FacebookPostRepository;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentFormatDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentResultRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.AnalyticsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
	/**
	 * Ngưỡng "rủi ro rời bỏ": không có hoạt động (đăng ký) nào trong hơn N ngày tính đến hiện tại,
	 * nhưng đã từng hoạt động ít nhất 1 lần với owner này. Con số 90 ngày (~1 quý) là lựa chọn sản
	 * phẩm hợp lý cho tần suất chơi billiard theo giải — không có yêu cầu cụ thể từ đề bài nên chọn
	 * ngưỡng này và document rõ thay vì hard-code rải rác.
	 */
	private static final int AT_RISK_THRESHOLD_DAYS = 90;

	private final TournamentRepository tournamentRepository;
	private final RegistrationRepository registrationRepository;
	private final ParticipantRepository participantRepository;
	private final PaymentRepository paymentRepository;
	private final MatchRepository matchRepository;
	private final TournamentResultRepository tournamentResultRepository;
	private final FacebookPostRepository facebookPostRepository;
	private final GameTypeDefinitionRepository gameTypeDefinitionRepository;
	private final TournamentFormatDefinitionRepository tournamentFormatDefinitionRepository;
	private final UserRepository userRepository;
	private final AnalyticsSavedViewRepository analyticsSavedViewRepository;
	// Khởi tạo trực tiếp thay vì Spring bean — cùng convention với PayOSServiceImpl/PaymentController,
	// tránh phụ thuộc vào ObjectMapper bean auto-config (không có sẵn trong project này).
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public AnalyticsOverviewResponse buildOverview(Long ownerId, Instant fromParam, Instant toParam, List<Long> branchIds,
			List<String> gameTypes, List<String> statuses) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Tournament> allTournaments = ownerTournaments(ownerId, branchIds, gameTypes, statuses);
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

		BigDecimal arpu = uniquePlayers > 0
				? totalRevenue.divide(BigDecimal.valueOf(uniquePlayers), 0, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		List<Payment> paymentsInRange = payments.stream().filter(p -> inRange(effectiveTs(p), from, to)).toList();
		Double paymentSuccessRatePct = paymentsInRange.isEmpty() ? 0.0
				: round1(successInRange.size() * 100.0 / paymentsInRange.size());

		RetentionSummary retention = computeRetentionSummary(tournamentIds, from, to);

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
				.arpu(arpu)
				.paymentSuccessRatePct(paymentSuccessRatePct)
				.atRiskPlayerCount(retention.atRiskPlayerCount())
				.periodReturnRatePct(retention.periodReturnRatePct())
				.build();
	}

	@Override
	public RevenueBreakdownResponse buildRevenueBreakdown(Long ownerId, Instant fromParam, Instant toParam, String granularity,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Tournament> allTournaments = ownerTournaments(ownerId, branchIds, gameTypes, statuses);
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
	public List<TournamentPerformanceItem> buildTournamentPerformance(Long ownerId, Instant fromParam, Instant toParam,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		// Danh sách giải đấu KHÔNG lọc theo khoảng thời gian — "hiệu suất giải đấu" phải liệt kê đủ
		// mọi giải của owner (trong phạm vi branch/loại bi/trạng thái đang lọc); chỉ riêng doanh thu
		// mới scope theo [from,to] để khớp với phần còn lại của trang (KPI, xu hướng doanh thu...).
		List<Tournament> allTournaments = ownerTournaments(ownerId, branchIds, gameTypes, statuses);
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
	public List<PlayerLeaderboardItem> buildPlayerLeaderboard(Long ownerId, Instant fromParam, Instant toParam, List<Long> branchIds,
			List<String> gameTypes, List<String> statuses, String sortBy, Integer limit, String segment, String search) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);
		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds, gameTypes, statuses));

		Map<Long, PlayerAgg> aggMap = aggregatePlayers(tournamentIds, from, to);

		int effLimit = (limit == null || limit < 1) ? TOP_N : Math.min(limit, 100);
		String seg = segment == null || segment.isBlank() ? "ALL" : segment.trim().toUpperCase(Locale.ROOT);
		String searchLower = (search == null || search.isBlank()) ? null : search.trim().toLowerCase(Locale.ROOT);
		Instant now = Instant.now();

		Comparator<PlayerAgg> comparator = playerComparator(sortBy);

		return aggMap.values().stream()
				.filter(a -> matchesSegment(a, seg, from, now))
				.filter(a -> searchLower == null || (a.playerName() != null && a.playerName().toLowerCase(Locale.ROOT).contains(searchLower)))
				.sorted(comparator)
				.limit(effLimit)
				.map(a -> toLeaderboardItem(a, from, to, now))
				.toList();
	}

	private boolean matchesSegment(PlayerAgg a, String segment, Instant from, Instant now) {
		return switch (segment) {
			case "NEW" -> a.isNewInRange(from, now);
			case "RETURNING" -> a.isReturningInRange(from);
			case "CHAMPION" -> a.championCount() > 0;
			case "AT_RISK" -> a.lastActivityAt() != null && Duration.between(a.lastActivityAt(), now).toDays() > AT_RISK_THRESHOLD_DAYS;
			default -> true;
		};
	}

	private Comparator<PlayerAgg> playerComparator(String sortBy) {
		String s = sortBy == null || sortBy.isBlank() ? "PRIZE" : sortBy.trim().toUpperCase(Locale.ROOT);
		return switch (s) {
			case "POINTS" -> Comparator.comparingLong(PlayerAgg::totalPoints).reversed();
			case "TOURNAMENTS" -> Comparator.comparingLong(PlayerAgg::tournamentsPlayedInRange).reversed();
			case "SPEND" -> Comparator.comparing(PlayerAgg::totalSpendInRange).reversed();
			case "WINS" -> Comparator.comparingLong(PlayerAgg::matchesWon).reversed();
			case "MATCHES" -> Comparator.comparingLong(PlayerAgg::matchesPlayed).reversed();
			case "RECENCY" -> Comparator.comparing(
					(PlayerAgg a) -> a.lastActivityAt() != null ? a.lastActivityAt() : Instant.EPOCH).reversed();
			default -> Comparator.comparing(PlayerAgg::totalPrizeAmount).reversed()
					.thenComparing(Comparator.comparingLong(PlayerAgg::totalPoints).reversed());
		};
	}

	private PlayerLeaderboardItem toLeaderboardItem(PlayerAgg a, Instant from, Instant to, Instant now) {
		Long days = a.lastActivityAt() != null ? Duration.between(a.lastActivityAt(), now).toDays() : null;
		return PlayerLeaderboardItem.builder()
				.userId(a.userId())
				.playerName(a.playerName())
				.tournamentsPlayed(a.tournamentsPlayedInRange())
				.championCount(a.championCount())
				.top3Count(a.top3Count())
				.totalPrizeAmount(a.totalPrizeAmount())
				.totalPoints(a.totalPoints())
				.totalSpend(a.totalSpendInRange())
				.matchesPlayed(a.matchesPlayed())
				.matchesWon(a.matchesWon())
				.winRatePct(a.matchesPlayed() > 0 ? round1(a.matchesWon() * 100.0 / a.matchesPlayed()) : null)
				.lifetimeTournaments(a.lifetimeTournaments())
				.firstSeenAt(a.firstSeenAt())
				.lastActivityAt(a.lastActivityAt())
				.daysSinceLastActivity(days)
				.isNewPlayer(a.isNewInRange(from, to))
				.isReturning(a.isReturningInRange(from))
				.build();
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
	public RegistrationStatsResponse buildRegistrationFunnel(Long ownerId, Instant fromParam, Instant toParam, String granularity,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds, gameTypes, statuses));
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
	public PlayerGrowthResponse buildPlayerGrowth(Long ownerId, Instant fromParam, Instant toParam, String granularity,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);

		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds, gameTypes, statuses));
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

		RetentionSummary retention = computeRetentionSummary(tournamentIds, from, to);

		return PlayerGrowthResponse.builder()
				.newPlayersTrend(trend)
				.activePlayerCount(activePlayers)
				.returningPlayerCount(returningPlayers)
				.repeatPlayerRatePct(repeatRate)
				.periodReturnRatePct(retention.periodReturnRatePct())
				.build();
	}

	@Override
	public PlayerRetentionResponse buildPlayerRetention(Long ownerId, Instant fromParam, Instant toParam, List<Long> branchIds,
			List<String> gameTypes, List<String> statuses) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);
		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds, gameTypes, statuses));

		RetentionSummary summary = computeRetentionSummary(tournamentIds, from, to);

		Instant now = Instant.now();
		Map<Long, PlayerAgg> lifetimeAgg = aggregatePlayers(tournamentIds, Instant.EPOCH, now);

		long b1 = 0, b2 = 0, b3 = 0, b4 = 0;
		for (PlayerAgg a : lifetimeAgg.values()) {
			long n = a.lifetimeTournaments();
			if (n <= 0) continue;
			if (n == 1) b1++;
			else if (n <= 3) b2++;
			else if (n <= 6) b3++;
			else b4++;
		}
		List<StatusCountItem> loyaltyDistribution = List.of(
				StatusCountItem.builder().status("1").label("1 giải").count(b1).build(),
				StatusCountItem.builder().status("2-3").label("2-3 giải").count(b2).build(),
				StatusCountItem.builder().status("4-6").label("4-6 giải").count(b3).build(),
				StatusCountItem.builder().status("7+").label("7+ giải").count(b4).build());

		List<AtRiskPlayerItem> atRiskPlayers = lifetimeAgg.values().stream()
				.filter(a -> a.lastActivityAt() != null)
				.filter(a -> Duration.between(a.lastActivityAt(), now).toDays() > AT_RISK_THRESHOLD_DAYS)
				.sorted(Comparator.comparing(PlayerAgg::lastActivityAt))
				.limit(50)
				.map(a -> AtRiskPlayerItem.builder()
						.userId(a.userId())
						.playerName(a.playerName())
						.lastActivityAt(a.lastActivityAt())
						.daysSinceLastActivity(Duration.between(a.lastActivityAt(), now).toDays())
						.lifetimeTournaments(a.lifetimeTournaments())
						.totalSpend(a.totalSpendInRange())
						.build())
				.toList();

		return PlayerRetentionResponse.builder()
				.periodReturnRatePct(summary.periodReturnRatePct())
				.previousPeriodActivePlayers(summary.previousPeriodActive())
				.currentPeriodReturningPlayers(summary.currentReturning())
				.loyaltyDistribution(loyaltyDistribution)
				.atRiskThresholdDays(AT_RISK_THRESHOLD_DAYS)
				.atRiskPlayers(atRiskPlayers)
				.build();
	}

	@Override
	public PlayerAnalyticsDetailResponse buildPlayerDetail(Long ownerId, Long userId, List<Long> branchIds) {
		List<Tournament> allTournaments = ownerTournaments(ownerId, branchIds);
		List<Long> tournamentIds = ids(allTournaments);
		Map<Long, Tournament> tournamentsById = allTournaments.stream()
				.collect(Collectors.toMap(Tournament::getId, Function.identity()));

		List<Registration> registrations = scoped(tournamentIds, registrationRepository::findByTournamentIdIn).stream()
				.filter(r -> r.getUser() != null && userId.equals(r.getUser().getId()))
				.sorted(Comparator.comparing(Registration::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
				.toList();
		if (registrations.isEmpty()) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}

		List<Payment> payments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn).stream()
				.filter(p -> PaymentStatus.SUCCESS.getValue().equals(p.getStatus())
						&& p.getUser() != null && userId.equals(p.getUser().getId()))
				.toList();
		Map<Long, BigDecimal> paidByTournament = new LinkedHashMap<>();
		for (Payment p : payments) {
			if (p.getRegistration() == null || p.getRegistration().getTournament() == null) continue;
			paidByTournament.merge(p.getRegistration().getTournament().getId(),
					p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
		}

		List<TournamentResult> results = scoped(tournamentIds, tournamentResultRepository::findByTournamentIdIn).stream()
				.filter(r -> r.getParticipant() != null && r.getParticipant().getRegistration() != null
						&& r.getParticipant().getRegistration().getUser() != null
						&& userId.equals(r.getParticipant().getRegistration().getUser().getId()))
				.toList();
		Map<Long, TournamentResult> resultByTournament = results.stream()
				.collect(Collectors.toMap(r -> r.getTournament().getId(), Function.identity(), (a, b) -> a));

		List<PlayerAnalyticsDetailResponse.TournamentHistoryItem> history = registrations.stream()
				.map(r -> {
					Tournament t = tournamentsById.getOrDefault(r.getTournament().getId(), r.getTournament());
					TournamentResult res = resultByTournament.get(t.getId());
					RegistrationStatus st = safeRegistrationStatus(r.getStatus());
					return PlayerAnalyticsDetailResponse.TournamentHistoryItem.builder()
							.tournamentId(t.getId())
							.tournamentName(t.getName())
							.branchName(branchLabel(t))
							.registeredAt(r.getCreatedAt())
							.registrationStatus(r.getStatus())
							.registrationStatusLabel(st != null ? st.getDisplayName() : r.getStatus())
							.amountPaid(paidByTournament.getOrDefault(t.getId(), BigDecimal.ZERO))
							.finalRank(res != null ? res.getFinalRank() : null)
							.prizeAmount(res != null ? res.getPrizeAmount() : null)
							.pointsEarned(res != null ? res.getPointsEarned() : null)
							.build();
				})
				.toList();

		User user = registrations.get(0).getUser();
		Map<Long, PlayerAgg> agg = aggregatePlayers(tournamentIds, Instant.EPOCH, Instant.now());
		PlayerAgg a = agg.get(userId);
		PlayerLeaderboardItem summary = a != null ? toLeaderboardItem(a, Instant.EPOCH, Instant.now(), Instant.now()) : null;

		return PlayerAnalyticsDetailResponse.builder()
				.userId(userId)
				.playerName(resolvePlayerName(user, registrations.get(0).getPlayerFullName()))
				.email(user.getEmail())
				.summary(summary)
				.history(history)
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
	public TransactionStatsResponse buildTransactionStats(Long ownerId, Instant fromParam, Instant toParam, String granularity,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);
		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds, gameTypes, statuses));
		List<Payment> payments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn);
		return buildTransactionStats(payments, from, to, granularity);
	}

	@Override
	public PageResponse<PaymentHistoryResponse> listTransactions(
			Long ownerId, Long tournamentId, String status, Instant fromParam, Instant toParam, int page, int size,
			List<Long> branchIds, List<String> gameTypes, List<String> statuses) {
		List<Long> tournamentIds = ids(ownerTournaments(ownerId, branchIds, gameTypes, statuses));
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

	// ──────────────────────────── Phase 2: flexible query engine ────────────────────────────

	@Override
	public AnalyticsQueryResponse runQuery(Long ownerId, List<Long> actorAccessibleBranchIds, AnalyticsQueryRequest request) {
		validateQueryRequest(request);
		List<Long> branchIds = resolveQueryBranchIds(actorAccessibleBranchIds, request.getBranchIds());

		Instant from = clampRangeStart(defaultFrom(parseQueryDate(request.getFrom(), false)), defaultTo(parseQueryDate(request.getTo(), true)));
		Instant to = defaultTo(parseQueryDate(request.getTo(), true));
		String granularity = normalizeGranularity(request.getGranularity());

		List<AnalyticsDimension> dims = parseDimensions(request.getDimensions());
		List<AnalyticsMetric> mets = parseMetrics(request.getMetrics());
		AnalyticsMetric.FactKind factKind = mets.get(0).getFactKind();
		for (AnalyticsMetric m : mets) {
			if (m.getFactKind() != factKind) {
				throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST,
						"Không thể kết hợp metric \"" + m.name() + "\" với metric thuộc nhóm dữ liệu khác (" + factKind
								+ ") trong cùng 1 truy vấn — hãy tách thành 2 truy vấn riêng.");
			}
		}
		for (AnalyticsDimension d : dims) {
			if (!dimensionSupportsFactKind(d, factKind)) {
				throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST,
						"Chiều \"" + d.name() + "\" không áp dụng được cho nhóm dữ liệu " + factKind + ".");
			}
		}

		AnalyticsQueryRequest.Filters filters = request.getFilters() != null ? request.getFilters() : new AnalyticsQueryRequest.Filters();
		List<Long> queryTournamentIds = filters.getTournamentIds();
		List<Tournament> tournaments = ownerTournaments(ownerId, branchIds, filters.getGameTypes(), filters.getTournamentStatuses());
		if (queryTournamentIds != null && !queryTournamentIds.isEmpty()) {
			tournaments = tournaments.stream().filter(t -> queryTournamentIds.contains(t.getId())).toList();
		}
		List<Long> tournamentIds = ids(tournaments);
		Map<Long, Tournament> tournamentsById = tournaments.stream()
				.collect(Collectors.toMap(Tournament::getId, Function.identity()));
		Map<String, String> gameTypeNames = gameTypeDefinitionRepository.findAll().stream()
				.collect(Collectors.toMap(GameTypeDefinition::getCode, GameTypeDefinition::getName));

		int limit = request.getLimit() == null || request.getLimit() < 1 ? 50 : Math.min(request.getLimit(), 500);

		QueryGroupResult current = runGroupedQuery(factKind, dims, mets, tournamentIds, tournamentsById, gameTypeNames, filters, from, to, granularity);

		Map<String, Object> previousTotals = null;
		String comparedFromStr = null;
		String comparedToStr = null;
		if (request.isComparePreviousPeriod()) {
			Duration span = Duration.between(from, to);
			Instant prevFrom = from.minus(span);
			QueryGroupResult prevResult = runGroupedQuery(factKind, List.of(), mets, tournamentIds, tournamentsById, gameTypeNames, filters, prevFrom, from, granularity);
			previousTotals = prevResult.totals();
			comparedFromStr = prevFrom.atZone(ZONE).toLocalDate().toString();
			comparedToStr = from.atZone(ZONE).toLocalDate().toString();
		}

		boolean truncated = current.rows().size() > limit;
		List<AnalyticsQueryResponse.Row> rows = current.rows().stream()
				.sorted(rowComparator(request.getSortBy(), request.getSortDir(), mets))
				.limit(limit)
				.toList();

		return AnalyticsQueryResponse.builder()
				.rows(rows)
				.totals(current.totals())
				.previousPeriodTotals(previousTotals)
				.meta(AnalyticsQueryResponse.Meta.builder()
						.from(from.atZone(ZONE).toLocalDate().toString())
						.to(to.atZone(ZONE).toLocalDate().toString())
						.granularity(granularity)
						.comparedFrom(comparedFromStr)
						.comparedTo(comparedToStr)
						.dimensions(dims.stream().map(Enum::name).toList())
						.metrics(mets.stream().map(Enum::name).toList())
						.truncated(truncated)
						.build())
				.build();
	}

	private record QueryGroupResult(List<AnalyticsQueryResponse.Row> rows, Map<String, Object> totals) {}

	private QueryGroupResult runGroupedQuery(AnalyticsMetric.FactKind factKind, List<AnalyticsDimension> dims, List<AnalyticsMetric> mets,
			List<Long> tournamentIds, Map<Long, Tournament> tournamentsById, Map<String, String> gameTypeNames,
			AnalyticsQueryRequest.Filters filters, Instant from, Instant to, String granularity) {
		return switch (factKind) {
			case PAYMENT -> runPaymentQuery(dims, mets, tournamentIds, tournamentsById, gameTypeNames, filters, from, to, granularity);
			case REGISTRATION -> runRegistrationQuery(dims, mets, tournamentIds, tournamentsById, gameTypeNames, filters, from, to, granularity);
			case TOURNAMENT -> runTournamentQuery(dims, mets, tournamentIds, tournamentsById, gameTypeNames, from, to, granularity);
		};
	}

	private static final class PaymentBucket {
		BigDecimal revenue = BigDecimal.ZERO;
		BigDecimal refund = BigDecimal.ZERO;
		long txnCount;
		long successCount;
		BigDecimal successSum = BigDecimal.ZERO;
	}

	private QueryGroupResult runPaymentQuery(List<AnalyticsDimension> dims, List<AnalyticsMetric> mets,
			List<Long> tournamentIds, Map<Long, Tournament> tournamentsById, Map<String, String> gameTypeNames,
			AnalyticsQueryRequest.Filters filters, Instant from, Instant to, String granularity) {

		List<Payment> payments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn).stream()
				.filter(p -> inRange(effectiveTs(p), from, to))
				.filter(p -> p.getRegistration() != null && p.getRegistration().getTournament() != null
						&& tournamentsById.containsKey(p.getRegistration().getTournament().getId()))
				.filter(p -> filters.getPaymentStatuses() == null || filters.getPaymentStatuses().isEmpty()
						|| filters.getPaymentStatuses().contains(p.getStatus()))
				.filter(p -> filters.getPaymentMethods() == null || filters.getPaymentMethods().isEmpty()
						|| filters.getPaymentMethods().contains(p.getPaymentMethod()))
				.toList();

		Map<List<String>, PaymentBucket> grouped = new LinkedHashMap<>();
		PaymentBucket total = new PaymentBucket();
		for (Payment p : payments) {
			Tournament t = tournamentsById.get(p.getRegistration().getTournament().getId());
			List<String> key = dims.stream().map(d -> switch (d) {
				case TIME -> bucketLabel(effectiveTs(p), granularity);
				case PAYMENT_METHOD -> p.getPaymentMethod() != null ? p.getPaymentMethod() : "—";
				case PAYMENT_STATUS -> {
					PaymentStatus s = safePaymentStatus(p.getStatus());
					yield s != null ? s.getDisplayName() : p.getStatus();
				}
				default -> tournamentDimensionValue(d, t, gameTypeNames);
			}).toList();
			accumulatePayment(grouped.computeIfAbsent(key, k -> new PaymentBucket()), p);
			accumulatePayment(total, p);
		}

		List<AnalyticsQueryResponse.Row> rows = grouped.entrySet().stream()
				.map(e -> AnalyticsQueryResponse.Row.builder()
						.dimensions(zipDimensions(dims, e.getKey()))
						.metrics(finalizePaymentMetrics(mets, e.getValue()))
						.build())
				.toList();
		return new QueryGroupResult(rows, finalizePaymentMetrics(mets, total));
	}

	private void accumulatePayment(PaymentBucket b, Payment p) {
		boolean success = PaymentStatus.SUCCESS.getValue().equals(p.getStatus());
		b.txnCount++;
		BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
		if (success) {
			b.successCount++;
			b.revenue = b.revenue.add(amt);
			b.successSum = b.successSum.add(amt);
		} else if (PaymentStatus.FAILED.getValue().equals(p.getStatus()) || PaymentStatus.CANCELLED.getValue().equals(p.getStatus())) {
			b.refund = b.refund.add(amt);
		}
	}

	private Map<String, Object> finalizePaymentMetrics(List<AnalyticsMetric> mets, PaymentBucket b) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (AnalyticsMetric metric : mets) {
			m.put(metric.name(), switch (metric) {
				case REVENUE -> b.revenue;
				case REFUND_AMOUNT -> b.refund;
				case TRANSACTION_COUNT -> b.txnCount;
				case AVG_TRANSACTION_VALUE -> b.successCount > 0
						? b.successSum.divide(BigDecimal.valueOf(b.successCount), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
				case PAYMENT_SUCCESS_RATE -> b.txnCount > 0 ? round1(b.successCount * 100.0 / b.txnCount) : 0.0;
				default -> null;
			});
		}
		return m;
	}

	private static final class RegistrationBucket {
		long total;
		long approved;
		final Set<Long> uniqueUsers = new HashSet<>();
		final Set<Long> newUsers = new HashSet<>();
		final Set<Long> returningUsers = new HashSet<>();
	}

	private QueryGroupResult runRegistrationQuery(List<AnalyticsDimension> dims, List<AnalyticsMetric> mets,
			List<Long> tournamentIds, Map<Long, Tournament> tournamentsById, Map<String, String> gameTypeNames,
			AnalyticsQueryRequest.Filters filters, Instant from, Instant to, String granularity) {

		List<Registration> all = scoped(tournamentIds, registrationRepository::findByTournamentIdIn);
		// firstSeen cả đời (không giới hạn theo from/to) để phân loại NEW_VS_RETURNING chuẩn.
		Map<Long, Instant> firstSeenByUser = new LinkedHashMap<>();
		for (Registration r : all) {
			if (r.getUser() == null || r.getCreatedAt() == null) continue;
			firstSeenByUser.merge(r.getUser().getId(), r.getCreatedAt(), (a, b) -> a.isBefore(b) ? a : b);
		}

		List<Registration> registrations = all.stream()
				.filter(r -> inRange(r.getCreatedAt(), from, to))
				.filter(r -> r.getTournament() != null && tournamentsById.containsKey(r.getTournament().getId()))
				.filter(r -> filters.getRegistrationStatuses() == null || filters.getRegistrationStatuses().isEmpty()
						|| filters.getRegistrationStatuses().contains(r.getStatus()))
				.filter(r -> matchesPlayerSegmentFilter(r, filters.getPlayerSegment(), firstSeenByUser, from))
				.toList();

		Map<List<String>, RegistrationBucket> grouped = new LinkedHashMap<>();
		RegistrationBucket total = new RegistrationBucket();
		for (Registration r : registrations) {
			Tournament t = tournamentsById.get(r.getTournament().getId());
			String newVsReturning = newVsReturningLabel(r.getUser(), firstSeenByUser, from);
			List<String> key = dims.stream().map(d -> switch (d) {
				case TIME -> bucketLabel(r.getCreatedAt(), granularity);
				case REGISTRATION_STATUS -> {
					RegistrationStatus s = safeRegistrationStatus(r.getStatus());
					yield s != null ? s.getDisplayName() : r.getStatus();
				}
				case NEW_VS_RETURNING -> newVsReturning;
				default -> tournamentDimensionValue(d, t, gameTypeNames);
			}).toList();
			accumulateRegistration(grouped.computeIfAbsent(key, k -> new RegistrationBucket()), r, newVsReturning);
			accumulateRegistration(total, r, newVsReturning);
		}

		List<AnalyticsQueryResponse.Row> rows = grouped.entrySet().stream()
				.map(e -> AnalyticsQueryResponse.Row.builder()
						.dimensions(zipDimensions(dims, e.getKey()))
						.metrics(finalizeRegistrationMetrics(mets, e.getValue()))
						.build())
				.toList();
		return new QueryGroupResult(rows, finalizeRegistrationMetrics(mets, total));
	}

	private boolean matchesPlayerSegmentFilter(Registration r, String playerSegment, Map<Long, Instant> firstSeenByUser, Instant from) {
		if (playerSegment == null || playerSegment.isBlank() || "ALL".equalsIgnoreCase(playerSegment)) return true;
		String label = newVsReturningLabel(r.getUser(), firstSeenByUser, from);
		if ("NEW".equalsIgnoreCase(playerSegment)) return "Mới".equals(label);
		if ("RETURNING".equalsIgnoreCase(playerSegment)) return "Quay lại".equals(label);
		return true;
	}

	private String newVsReturningLabel(User user, Map<Long, Instant> firstSeenByUser, Instant from) {
		if (user == null) return "Không rõ";
		Instant firstSeen = firstSeenByUser.get(user.getId());
		if (firstSeen == null) return "Không rõ";
		return firstSeen.isBefore(from) ? "Quay lại" : "Mới";
	}

	private void accumulateRegistration(RegistrationBucket b, Registration r, String newVsReturning) {
		b.total++;
		if (RegistrationStatus.APPROVED.getValue().equals(r.getStatus())) b.approved++;
		if (r.getUser() != null) {
			b.uniqueUsers.add(r.getUser().getId());
			if ("Mới".equals(newVsReturning)) b.newUsers.add(r.getUser().getId());
			else if ("Quay lại".equals(newVsReturning)) b.returningUsers.add(r.getUser().getId());
		}
	}

	private Map<String, Object> finalizeRegistrationMetrics(List<AnalyticsMetric> mets, RegistrationBucket b) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (AnalyticsMetric metric : mets) {
			m.put(metric.name(), switch (metric) {
				case REGISTRATION_COUNT -> b.total;
				case APPROVAL_RATE -> b.total > 0 ? round1(b.approved * 100.0 / b.total) : 0.0;
				case UNIQUE_PLAYERS -> (long) b.uniqueUsers.size();
				case NEW_PLAYERS -> (long) b.newUsers.size();
				case RETURNING_PLAYERS -> (long) b.returningUsers.size();
				default -> null;
			});
		}
		return m;
	}

	private static final class TournamentBucket {
		long count;
		double fillRateSum;
		long fillRateN;
		long matchTotal;
		long matchCompleted;
		BigDecimal prizePool = BigDecimal.ZERO;
		BigDecimal netProfit = BigDecimal.ZERO;
	}

	private QueryGroupResult runTournamentQuery(List<AnalyticsDimension> dims, List<AnalyticsMetric> mets,
			List<Long> tournamentIds, Map<Long, Tournament> tournamentsById, Map<String, String> gameTypeNames,
			Instant from, Instant to, String granularity) {

		List<Tournament> tournaments = tournamentsInRange(new ArrayList<>(tournamentsById.values()), from, to);

		List<Payment> allPayments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn);
		Map<Long, BigDecimal> revenueByTournament = revenueByTournament(
				allPayments.stream().filter(p -> PaymentStatus.SUCCESS.getValue().equals(p.getStatus())).toList());
		List<Participant> participants = scoped(tournamentIds, participantRepository::findByTournamentIdIn);
		Map<Long, Long> activeParticipantsByTournament = participants.stream()
				.filter(p -> ParticipantStatus.ACTIVE.getValue().equals(p.getStatus()))
				.collect(Collectors.groupingBy(p -> p.getTournament().getId(), Collectors.counting()));
		List<Match> matches = scoped(tournamentIds, matchRepository::findByTournamentIdIn);
		Map<Long, long[]> matchCounts = new LinkedHashMap<>();
		for (Match m : matches) {
			long[] c = matchCounts.computeIfAbsent(m.getTournament().getId(), k -> new long[2]);
			c[0]++;
			if (isResolved(m.getStatus())) c[1]++;
		}

		Map<List<String>, TournamentBucket> grouped = new LinkedHashMap<>();
		TournamentBucket total = new TournamentBucket();
		for (Tournament t : tournaments) {
			List<String> key = dims.stream()
					.map(d -> d == AnalyticsDimension.TIME
							? bucketLabel(t.getStartAt() != null ? t.getStartAt() : t.getCreatedAt(), granularity)
							: tournamentDimensionValue(d, t, gameTypeNames))
					.toList();
			accumulateTournament(grouped.computeIfAbsent(key, k -> new TournamentBucket()), t, revenueByTournament, activeParticipantsByTournament, matchCounts);
			accumulateTournament(total, t, revenueByTournament, activeParticipantsByTournament, matchCounts);
		}

		List<AnalyticsQueryResponse.Row> rows = grouped.entrySet().stream()
				.map(e -> AnalyticsQueryResponse.Row.builder()
						.dimensions(zipDimensions(dims, e.getKey()))
						.metrics(finalizeTournamentMetrics(mets, e.getValue()))
						.build())
				.toList();
		return new QueryGroupResult(rows, finalizeTournamentMetrics(mets, total));
	}

	private void accumulateTournament(TournamentBucket b, Tournament t, Map<Long, BigDecimal> revenueByTournament,
			Map<Long, Long> activeParticipantsByTournament, Map<Long, long[]> matchCounts) {
		b.count++;
		if (t.getMaxParticipants() != null && t.getMaxParticipants() > 0) {
			double fill = activeParticipantsByTournament.getOrDefault(t.getId(), 0L) * 100.0 / t.getMaxParticipants();
			b.fillRateSum += fill;
			b.fillRateN++;
		}
		long[] mc = matchCounts.getOrDefault(t.getId(), new long[2]);
		b.matchTotal += mc[0];
		b.matchCompleted += mc[1];
		BigDecimal prize = t.getPrizePool() != null ? t.getPrizePool() : BigDecimal.ZERO;
		b.prizePool = b.prizePool.add(prize);
		BigDecimal revenue = revenueByTournament.getOrDefault(t.getId(), BigDecimal.ZERO);
		b.netProfit = b.netProfit.add(revenue.subtract(prize));
	}

	private Map<String, Object> finalizeTournamentMetrics(List<AnalyticsMetric> mets, TournamentBucket b) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (AnalyticsMetric metric : mets) {
			m.put(metric.name(), switch (metric) {
				case TOURNAMENT_COUNT -> b.count;
				case AVG_FILL_RATE -> b.fillRateN > 0 ? round1(b.fillRateSum / b.fillRateN) : 0.0;
				case COMPLETION_RATE -> b.matchTotal > 0 ? round1(b.matchCompleted * 100.0 / b.matchTotal) : 0.0;
				case PRIZE_POOL -> b.prizePool;
				case NET_PROFIT -> b.netProfit;
				default -> null;
			});
		}
		return m;
	}

	private String tournamentDimensionValue(AnalyticsDimension d, Tournament t, Map<String, String> gameTypeNames) {
		if (t == null) return "—";
		return switch (d) {
			case BRANCH -> branchLabel(t);
			case TOURNAMENT -> t.getName();
			case TOURNAMENT_STATUS -> {
				TournamentStatus s = safeStatus(t.getStatus());
				yield s != null ? s.getDisplayName() : t.getStatus();
			}
			case GAME_TYPE -> gameTypeNames.getOrDefault(t.getGameType(), t.getGameType());
			default -> "—";
		};
	}

	private Map<String, String> zipDimensions(List<AnalyticsDimension> dims, List<String> values) {
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i < dims.size(); i++) {
			m.put(dims.get(i).name(), values.get(i));
		}
		return m;
	}

	private Comparator<AnalyticsQueryResponse.Row> rowComparator(String sortBy, String sortDir, List<AnalyticsMetric> mets) {
		String key = (sortBy != null && !sortBy.isBlank()) ? sortBy.trim().toUpperCase(Locale.ROOT) : mets.get(0).name();
		boolean asc = "ASC".equalsIgnoreCase(sortDir);
		Comparator<AnalyticsQueryResponse.Row> cmp = Comparator.comparingDouble(r -> toDouble(r.getMetrics().get(key)));
		return asc ? cmp : cmp.reversed();
	}

	private double toDouble(Object v) {
		if (v == null) return 0.0;
		if (v instanceof BigDecimal bd) return bd.doubleValue();
		if (v instanceof Number n) return n.doubleValue();
		return 0.0;
	}

	private void validateQueryRequest(AnalyticsQueryRequest request) {
		if (request == null) throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Thiếu nội dung truy vấn.");
		if (request.getDimensions() == null || request.getDimensions().isEmpty()) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Phải chọn ít nhất 1 dimension.");
		}
		if (request.getMetrics() == null || request.getMetrics().isEmpty()) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Phải chọn ít nhất 1 metric.");
		}
	}

	private List<AnalyticsDimension> parseDimensions(List<String> raw) {
		return raw.stream().map(s -> {
			try {
				return AnalyticsDimension.valueOf(s.trim().toUpperCase(Locale.ROOT));
			} catch (Exception e) {
				throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Dimension không hợp lệ: " + s);
			}
		}).distinct().toList();
	}

	private List<AnalyticsMetric> parseMetrics(List<String> raw) {
		return raw.stream().map(s -> {
			try {
				return AnalyticsMetric.valueOf(s.trim().toUpperCase(Locale.ROOT));
			} catch (Exception e) {
				throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Metric không hợp lệ: " + s);
			}
		}).distinct().toList();
	}

	private boolean dimensionSupportsFactKind(AnalyticsDimension d, AnalyticsMetric.FactKind kind) {
		return switch (d) {
			case TIME, BRANCH, TOURNAMENT, TOURNAMENT_STATUS, GAME_TYPE -> true;
			case PAYMENT_METHOD, PAYMENT_STATUS -> kind == AnalyticsMetric.FactKind.PAYMENT;
			case REGISTRATION_STATUS, NEW_VS_RETURNING -> kind == AnalyticsMetric.FactKind.REGISTRATION;
		};
	}

	private List<Long> resolveQueryBranchIds(List<Long> actorAccessibleBranchIds, List<Long> requestedBranchIds) {
		if (requestedBranchIds == null || requestedBranchIds.isEmpty()) {
			return actorAccessibleBranchIds;
		}
		if (actorAccessibleBranchIds != null) {
			for (Long id : requestedBranchIds) {
				if (!actorAccessibleBranchIds.contains(id)) {
					throw new BusinessException(ErrorCode.BRANCH_ACCESS_DENIED);
				}
			}
		}
		return requestedBranchIds;
	}

	private Instant parseQueryDate(String value, boolean endOfDay) {
		if (value == null || value.isBlank()) return null;
		LocalDate d = LocalDate.parse(value.trim());
		return endOfDay ? d.atTime(LocalTime.MAX).atZone(ZONE).toInstant() : d.atStartOfDay(ZONE).toInstant();
	}

	private String bucketLabel(Instant ts, String granularity) {
		if (ts == null) return "—";
		String g = normalizeGranularity(granularity);
		return switch (g) {
			case "day" -> LocalDate.ofInstant(ts, ZONE).format(DAY_FMT);
			case "week" -> mondayOf(LocalDate.ofInstant(ts, ZONE), WeekFields.of(Locale.forLanguageTag("vi-VN"))).format(DAY_FMT);
			default -> YearMonth.from(ts.atZone(ZONE)).format(MONTH_FMT);
		};
	}

	// ──────────────────────────── Phase 3: insights ────────────────────────────

	@Override
	public List<InsightItem> buildInsights(Long ownerId, Instant fromParam, Instant toParam, List<Long> branchIds) {
		Instant from = clampRangeStart(defaultFrom(fromParam), defaultTo(toParam));
		Instant to = defaultTo(toParam);
		List<Tournament> allTournaments = ownerTournaments(ownerId, branchIds);
		List<Long> tournamentIds = ids(allTournaments);
		Duration span = Duration.between(from, to);

		List<InsightItem> insights = new ArrayList<>();

		RetentionSummary current = computeRetentionSummary(tournamentIds, from, to);
		RetentionSummary previous = computeRetentionSummary(tournamentIds, from.minus(span), from);
		if (current.periodReturnRatePct() != null && previous.periodReturnRatePct() != null && previous.previousPeriodActive() > 0) {
			double delta = current.periodReturnRatePct() - previous.periodReturnRatePct();
			if (delta <= -10) {
				insights.add(InsightItem.builder().severity("WARNING")
						.message("Tỷ lệ khách quay lại giảm " + round1(Math.abs(delta)) + " điểm phần trăm so với kỳ trước.").build());
			} else if (delta >= 10) {
				insights.add(InsightItem.builder().severity("POSITIVE")
						.message("Tỷ lệ khách quay lại tăng " + round1(delta) + " điểm phần trăm so với kỳ trước.").build());
			}
		}
		if (current.atRiskPlayerCount() >= 5) {
			insights.add(InsightItem.builder().severity("WARNING")
					.message(current.atRiskPlayerCount() + " người chơi chưa quay lại sau hơn " + AT_RISK_THRESHOLD_DAYS
							+ " ngày — cân nhắc chương trình kéo khách quay lại.").build());
		}

		Map<String, List<Tournament>> byBranch = tournamentsInRange(allTournaments, from, to).stream()
				.collect(Collectors.groupingBy(this::branchLabel));
		if (byBranch.size() > 1) {
			List<Participant> participants = scoped(tournamentIds, participantRepository::findByTournamentIdIn);
			Map<Long, Long> activeByTournament = participants.stream()
					.filter(p -> ParticipantStatus.ACTIVE.getValue().equals(p.getStatus()))
					.collect(Collectors.groupingBy(p -> p.getTournament().getId(), Collectors.counting()));
			for (Map.Entry<String, List<Tournament>> e : byBranch.entrySet()) {
				double avgFill = e.getValue().stream()
						.filter(t -> t.getMaxParticipants() != null && t.getMaxParticipants() > 0)
						.mapToDouble(t -> activeByTournament.getOrDefault(t.getId(), 0L) * 100.0 / t.getMaxParticipants())
						.average().orElse(-1);
				if (avgFill >= 0 && avgFill < 50) {
					insights.add(InsightItem.builder().severity("WARNING")
							.message("Chi nhánh \"" + e.getKey() + "\" có tỷ lệ lấp đầy trung bình chỉ " + Math.round(avgFill) + "% trong kỳ.").build());
				}
			}
		}

		Map<Long, PlayerAgg> agg = aggregatePlayers(tournamentIds, from, to);
		BigDecimal totalSpend = agg.values().stream().map(PlayerAgg::totalSpendInRange).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (totalSpend.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal top10Spend = agg.values().stream()
					.sorted(Comparator.comparing(PlayerAgg::totalSpendInRange).reversed())
					.limit(10)
					.map(PlayerAgg::totalSpendInRange)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			double pct = top10Spend.divide(totalSpend, 4, RoundingMode.HALF_UP).doubleValue() * 100;
			if (pct >= 50) {
				insights.add(InsightItem.builder().severity("INFO")
						.message("Top 10 khách chi tiêu nhiều nhất đóng góp " + Math.round(pct) + "% tổng doanh thu trong kỳ.").build());
			}
		}

		List<Payment> allPayments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn);
		List<Payment> paymentsInRange = allPayments.stream().filter(p -> inRange(effectiveTs(p), from, to)).toList();
		if (!paymentsInRange.isEmpty()) {
			long success = paymentsInRange.stream().filter(p -> PaymentStatus.SUCCESS.getValue().equals(p.getStatus())).count();
			double rate = success * 100.0 / paymentsInRange.size();
			if (rate < 80) {
				insights.add(InsightItem.builder().severity("WARNING")
						.message("Tỷ lệ thanh toán thành công chỉ đạt " + Math.round(rate) + "% trong kỳ.").build());
			}
		}

		BigDecimal revenue = sumAmount(successPaymentsInRange(allPayments, from, to));
		BigDecimal prevRevenue = sumAmount(successPaymentsInRange(allPayments, from.minus(span), from));
		Double growth = growthPct(revenue, prevRevenue);
		if (growth != null && growth >= 20 && prevRevenue.compareTo(BigDecimal.ZERO) > 0) {
			insights.add(InsightItem.builder().severity("POSITIVE")
					.message("Doanh thu tăng " + Math.round(growth) + "% so với kỳ trước.").build());
		}

		return insights.stream().limit(6).toList();
	}

	// ──────────────────────────── Phase 3: saved views ────────────────────────────

	@Override
	public List<SavedViewResponse> listSavedViews(Long userId) {
		return analyticsSavedViewRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
				.map(this::toSavedViewResponse)
				.toList();
	}

	@Override
	@Transactional
	public SavedViewResponse createSavedView(Long userId, SavedViewRequest request) {
		String json;
		try {
			json = objectMapper.writeValueAsString(request.getConfig());
		} catch (JsonProcessingException e) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Không đọc được cấu hình truy vấn.");
		}
		User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		AnalyticsSavedView entity = AnalyticsSavedView.builder()
				.user(user)
				.name(request.getName())
				.configJson(json)
				.build();
		return toSavedViewResponse(analyticsSavedViewRepository.save(entity));
	}

	@Override
	@Transactional
	public void deleteSavedView(Long userId, Long viewId) {
		AnalyticsSavedView view = analyticsSavedViewRepository.findByIdAndUser_Id(viewId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		analyticsSavedViewRepository.delete(view);
	}

	private SavedViewResponse toSavedViewResponse(AnalyticsSavedView v) {
		AnalyticsQueryRequest config;
		try {
			config = objectMapper.readValue(v.getConfigJson(), AnalyticsQueryRequest.class);
		} catch (JsonProcessingException e) {
			config = null;
		}
		return SavedViewResponse.builder().id(v.getId()).name(v.getName()).config(config).createdAt(v.getCreatedAt()).build();
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

	// ──────────────────────────── shared player aggregation ────────────────────────────

	/**
	 * Gộp toàn bộ chỉ số 1 người chơi (đăng ký/thanh toán/kết quả/trận đấu) trong phạm vi tournamentIds
	 * đã lọc theo chi nhánh/loại bi/trạng thái. "lifetimeXxx" luôn tính CẢ ĐỜI (không phụ thuộc from/to);
	 * "xxxInRange"/"activeInRange" tính riêng trong [from,to] truyền vào — gọi lại với from=EPOCH,
	 * to=now để lấy góc nhìn "cả đời" (dùng cho retention/loyalty/drill-down).
	 * Người chơi MANUAL (đăng ký hộ, không có tài khoản — Registration.user == null) không có định danh
	 * ổn định nên KHÔNG được tính vào phân tích theo người chơi này.
	 */
	private Map<Long, PlayerAgg> aggregatePlayers(List<Long> tournamentIds, Instant from, Instant to) {
		Map<Long, PlayerAccumulator> acc = new LinkedHashMap<>();

		List<Registration> registrations = scoped(tournamentIds, registrationRepository::findByTournamentIdIn);
		for (Registration r : registrations) {
			if (r.getUser() == null) continue;
			Long uid = r.getUser().getId();
			PlayerAccumulator a = acc.computeIfAbsent(uid, k -> new PlayerAccumulator());
			a.userId = uid;
			if (a.playerName == null) a.playerName = resolvePlayerName(r.getUser(), r.getPlayerFullName());
			if (r.getCreatedAt() != null) {
				if (a.firstSeenAt == null || r.getCreatedAt().isBefore(a.firstSeenAt)) a.firstSeenAt = r.getCreatedAt();
				if (a.lastActivityAt == null || r.getCreatedAt().isAfter(a.lastActivityAt)) a.lastActivityAt = r.getCreatedAt();
				if (inRange(r.getCreatedAt(), from, to)) a.activeInRange = true;
			}
			if (RegistrationStatus.APPROVED.getValue().equals(r.getStatus()) && r.getTournament() != null) {
				a.lifetimeApprovedTournamentIds.add(r.getTournament().getId());
				if (inRange(r.getCreatedAt(), from, to)) a.inRangeApprovedTournamentIds.add(r.getTournament().getId());
			}
		}

		List<Payment> payments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn);
		for (Payment p : payments) {
			if (p.getUser() == null || !PaymentStatus.SUCCESS.getValue().equals(p.getStatus())) continue;
			if (!inRange(effectiveTs(p), from, to)) continue;
			Long uid = p.getUser().getId();
			PlayerAccumulator a = acc.computeIfAbsent(uid, k -> new PlayerAccumulator());
			a.userId = uid;
			if (a.playerName == null) a.playerName = resolvePlayerName(p.getUser(), null);
			a.totalSpendInRange = a.totalSpendInRange.add(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
		}

		List<TournamentResult> results = scoped(tournamentIds, tournamentResultRepository::findByTournamentIdIn);
		for (TournamentResult r : results) {
			if (!inRange(r.getRecordedAt(), from, to)) continue;
			if (r.getParticipant() == null || r.getParticipant().getRegistration() == null
					|| r.getParticipant().getRegistration().getUser() == null) continue;
			Long uid = r.getParticipant().getRegistration().getUser().getId();
			PlayerAccumulator a = acc.computeIfAbsent(uid, k -> new PlayerAccumulator());
			a.userId = uid;
			if (a.playerName == null) a.playerName = r.getParticipant().getDisplayName();
			if (r.getFinalRank() != null && r.getFinalRank() == 1) a.championCount++;
			if (r.getFinalRank() != null && r.getFinalRank() <= 3) a.top3Count++;
			a.totalPrizeAmount = a.totalPrizeAmount.add(r.getPrizeAmount() != null ? r.getPrizeAmount() : BigDecimal.ZERO);
			a.totalPoints += r.getPointsEarned() != null ? r.getPointsEarned() : 0;
		}

		// Trận đấu tính trên TOÀN BỘ giải đấu đang lọc (branch/loại bi/trạng thái), KHÔNG lọc thêm
		// theo from/to — trận đấu không có mốc "diễn ra" đáng tin cậy tách biệt khỏi lịch giải đấu.
		List<Match> matches = scoped(tournamentIds, matchRepository::findByTournamentIdIn);
		for (Match m : matches) {
			if (!isResolved(m.getStatus())) continue;
			Long p1Uid = participantUserId(m.getPlayer1());
			Long p2Uid = participantUserId(m.getPlayer2());
			Long winnerUid = participantUserId(m.getWinner());
			if (p1Uid != null) {
				PlayerAccumulator a = acc.computeIfAbsent(p1Uid, k -> new PlayerAccumulator());
				a.userId = p1Uid;
				a.matchesPlayed++;
				if (a.playerName == null) a.playerName = m.getPlayer1().getDisplayName();
			}
			if (p2Uid != null) {
				PlayerAccumulator a = acc.computeIfAbsent(p2Uid, k -> new PlayerAccumulator());
				a.userId = p2Uid;
				a.matchesPlayed++;
				if (a.playerName == null) a.playerName = m.getPlayer2().getDisplayName();
			}
			if (winnerUid != null && acc.containsKey(winnerUid)) acc.get(winnerUid).matchesWon++;
		}

		Map<Long, PlayerAgg> result = new LinkedHashMap<>();
		for (PlayerAccumulator a : acc.values()) {
			result.put(a.userId, new PlayerAgg(
					a.userId, a.playerName,
					a.lifetimeApprovedTournamentIds.size(),
					a.inRangeApprovedTournamentIds.size(),
					a.championCount, a.top3Count, a.totalPrizeAmount, a.totalPoints,
					a.totalSpendInRange, a.matchesPlayed, a.matchesWon,
					a.firstSeenAt, a.lastActivityAt, a.activeInRange));
		}
		return result;
	}

	private static final class PlayerAccumulator {
		Long userId;
		String playerName;
		final Set<Long> lifetimeApprovedTournamentIds = new HashSet<>();
		final Set<Long> inRangeApprovedTournamentIds = new HashSet<>();
		long championCount;
		long top3Count;
		BigDecimal totalPrizeAmount = BigDecimal.ZERO;
		long totalPoints;
		BigDecimal totalSpendInRange = BigDecimal.ZERO;
		long matchesPlayed;
		long matchesWon;
		Instant firstSeenAt;
		Instant lastActivityAt;
		boolean activeInRange;
	}

	private record PlayerAgg(
			Long userId,
			String playerName,
			long lifetimeTournaments,
			long tournamentsPlayedInRange,
			long championCount,
			long top3Count,
			BigDecimal totalPrizeAmount,
			long totalPoints,
			BigDecimal totalSpendInRange,
			long matchesPlayed,
			long matchesWon,
			Instant firstSeenAt,
			Instant lastActivityAt,
			boolean activeInRange) {

		boolean isNewInRange(Instant from, Instant to) {
			return firstSeenAt != null && !firstSeenAt.isBefore(from) && !firstSeenAt.isAfter(to);
		}

		boolean isReturningInRange(Instant from) {
			return firstSeenAt != null && firstSeenAt.isBefore(from) && activeInRange;
		}
	}

	private record RetentionSummary(Double periodReturnRatePct, long previousPeriodActive, long currentReturning, long atRiskPlayerCount) {}

	/**
	 * periodReturnRatePct = % người chơi có hoạt động (đăng ký, bất kể trạng thái) trong kỳ TRƯỚC
	 * (cùng độ dài [from,to], liền kề ngay trước "from") mà CŨNG có hoạt động trong kỳ hiện tại
	 * [from,to]. Chuẩn xác hơn "repeatPlayerRatePct" cũ (vốn chỉ đếm tổng số đăng ký cả đời > 1).
	 * atRiskPlayerCount = số người chơi đã từng hoạt động nhưng lần hoạt động gần nhất cách "hiện tại"
	 * (Instant.now(), không phải "to" của bộ lọc) hơn AT_RISK_THRESHOLD_DAYS ngày.
	 */
	private RetentionSummary computeRetentionSummary(List<Long> tournamentIds, Instant from, Instant to) {
		Map<Long, PlayerAgg> currentAgg = aggregatePlayers(tournamentIds, from, to);
		Duration span = Duration.between(from, to);
		Instant prevFrom = from.minus(span);
		Map<Long, PlayerAgg> prevAgg = aggregatePlayers(tournamentIds, prevFrom, from);

		Set<Long> prevActive = prevAgg.values().stream().filter(PlayerAgg::activeInRange).map(PlayerAgg::userId).collect(Collectors.toSet());
		Set<Long> currActive = currentAgg.values().stream().filter(PlayerAgg::activeInRange).map(PlayerAgg::userId).collect(Collectors.toSet());
		long returning = prevActive.stream().filter(currActive::contains).count();
		Double rate = prevActive.isEmpty() ? 0.0 : round1(returning * 100.0 / prevActive.size());

		Instant now = Instant.now();
		Map<Long, PlayerAgg> lifetimeAgg = aggregatePlayers(tournamentIds, Instant.EPOCH, now);
		long atRisk = lifetimeAgg.values().stream()
				.filter(a -> a.lastActivityAt() != null && Duration.between(a.lastActivityAt(), now).toDays() > AT_RISK_THRESHOLD_DAYS)
				.count();

		return new RetentionSummary(rate, prevActive.size(), returning, atRisk);
	}

	private Long participantUserId(Participant p) {
		if (p == null || p.getRegistration() == null || p.getRegistration().getUser() == null) return null;
		return p.getRegistration().getUser().getId();
	}

	private String resolvePlayerName(User user, String fallback) {
		if (user.getProfile() != null && user.getProfile().getFullName() != null && !user.getProfile().getFullName().isBlank()) {
			return user.getProfile().getFullName();
		}
		if (fallback != null && !fallback.isBlank()) return fallback;
		return user.getEmail();
	}

	private RegistrationStatus safeRegistrationStatus(String status) {
		try {
			return RegistrationStatus.valueOf(status);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}

	private PaymentStatus safePaymentStatus(String status) {
		try {
			return PaymentStatus.valueOf(status);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}

	// ──────────────────────────── shared helpers ────────────────────────────

	/**
	 * branchIds null = không lọc theo chi nhánh (Owner mặc định xem toàn chuỗi); khác null = chỉ giữ
	 * lại các giải đấu thuộc 1 trong các chi nhánh đó (dùng để Owner lọc theo 1 chi nhánh cụ thể, hoặc
	 * để giới hạn Manager về đúng (các) chi nhánh họ được cấp quyền). gameTypes/statuses null/rỗng =
	 * không lọc thêm theo loại bi / trạng thái giải đấu.
	 */
	private List<Tournament> ownerTournaments(Long ownerId, List<Long> branchIds) {
		return ownerTournaments(ownerId, branchIds, null, null);
	}

	private List<Tournament> ownerTournaments(Long ownerId, List<Long> branchIds, List<String> gameTypes, List<String> statuses) {
		// KHÔNG được coi ownerId == null là "không lọc" rồi trả toàn bộ dữ liệu của mọi chủ sân —
		// mọi endpoint gọi tới đây đều bắt buộc phải có ownerId xác định (Owner/Manager tự xem của mình).
		if (ownerId == null) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		List<Tournament> tournaments = tournamentRepository.findByCreatedById(ownerId);
		if (branchIds != null) {
			tournaments = tournaments.stream()
					.filter(t -> t.getBranch() != null && branchIds.contains(t.getBranch().getId()))
					.toList();
		}
		if (gameTypes != null && !gameTypes.isEmpty()) {
			tournaments = tournaments.stream().filter(t -> gameTypes.contains(t.getGameType())).toList();
		}
		if (statuses != null && !statuses.isEmpty()) {
			tournaments = tournaments.stream().filter(t -> statuses.contains(t.getStatus())).toList();
		}
		return tournaments;
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
