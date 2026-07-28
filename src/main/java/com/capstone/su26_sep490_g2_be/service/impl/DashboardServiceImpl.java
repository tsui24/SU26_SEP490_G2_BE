package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.DashboardStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.MatchStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RevenueStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.StatusCountItem;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TrendPointResponse;
import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Payment;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

	private static final int TREND_MONTHS = 6;
	private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");
	private static final ZoneId ZONE = ZoneId.systemDefault();

	private static final Set<String> ACTIVE_TOURNAMENT_STATUSES = Set.of(
			TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
			TournamentStatus.REGISTRATION_CLOSED.getValue(),
			TournamentStatus.DRAW_PREVIEW.getValue(),
			TournamentStatus.DRAW_DONE.getValue(),
			TournamentStatus.FINAL_BRACKET_READY.getValue(),
			TournamentStatus.IN_PROGRESS.getValue());

	private final TournamentRepository tournamentRepository;
	private final RegistrationRepository registrationRepository;
	private final ParticipantRepository participantRepository;
	private final PaymentRepository paymentRepository;
	private final MatchRepository matchRepository;
	private final GameTypeDefinitionRepository gameTypeDefinitionRepository;

	@Override
	public DashboardStatsResponse buildStats(Long ownerUserId, List<Long> branchIds) {
		if (ownerUserId == null) {
			// ownerUserId == null KHÔNG được coi là "xem toàn hệ thống" — mọi caller (Owner/Manager)
			// đều phải xác định rõ mình thuộc owner nào, tránh lộ số liệu của owner khác.
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		List<Tournament> tournaments = tournamentRepository.findByCreatedById(ownerUserId);
		if (branchIds != null) {
			tournaments = tournaments.stream()
					.filter(t -> t.getBranch() != null && branchIds.contains(t.getBranch().getId()))
					.toList();
		}

		List<Long> tournamentIds = tournaments.stream().map(Tournament::getId).toList();

		List<Registration> registrations = scoped(tournamentIds, registrationRepository::findByTournamentIdIn);
		List<Participant> participants = scoped(tournamentIds, participantRepository::findByTournamentIdIn);
		List<Match> matches = scoped(tournamentIds, matchRepository::findByTournamentIdIn);
		List<Payment> payments = scoped(tournamentIds, paymentRepository::findByRegistration_Tournament_IdIn);

		return DashboardStatsResponse.builder()
				.tournaments(buildTournamentStats(tournaments))
				.registrations(buildRegistrationStats(registrations))
				.participants(buildParticipantStats(participants))
				.revenue(buildRevenueStats(payments))
				.matches(buildMatchStats(matches))
				.build();
	}

	private <T> List<T> scoped(List<Long> tournamentIds, Function<List<Long>, List<T>> fetcher) {
		return tournamentIds.isEmpty() ? List.of() : fetcher.apply(tournamentIds);
	}

	private TournamentStatsResponse buildTournamentStats(List<Tournament> tournaments) {
		Map<String, Long> counts = tournaments.stream()
				.collect(Collectors.groupingBy(Tournament::getStatus, Collectors.counting()));

		List<StatusCountItem> byStatus = Arrays.stream(TournamentStatus.values())
				.map(s -> StatusCountItem.builder()
						.status(s.getValue())
						.label(s.getDisplayName())
						.count(counts.getOrDefault(s.getValue(), 0L))
						.build())
				.filter(i -> i.getCount() > 0)
				.toList();

		long active = tournaments.stream()
				.filter(t -> ACTIVE_TOURNAMENT_STATUSES.contains(t.getStatus()))
				.count();

		BigDecimal totalPrizePool = tournaments.stream()
				.map(Tournament::getPrizePool)
				.filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		Map<String, String> gameTypeNames = gameTypeDefinitionRepository.findAll().stream()
				.collect(Collectors.toMap(GameTypeDefinition::getCode, GameTypeDefinition::getName));

		List<StatusCountItem> byGameType = tournaments.stream()
				.collect(Collectors.groupingBy(Tournament::getGameType, Collectors.counting()))
				.entrySet().stream()
				.map(e -> StatusCountItem.builder()
						.status(e.getKey())
						.label(gameTypeNames.getOrDefault(e.getKey(), e.getKey()))
						.count(e.getValue())
						.build())
				.sorted(Comparator.comparingLong(StatusCountItem::getCount).reversed())
				.toList();

		return TournamentStatsResponse.builder()
				.total(tournaments.size())
				.active(active)
				.openForRegistration(counts.getOrDefault(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), 0L))
				.inProgress(counts.getOrDefault(TournamentStatus.IN_PROGRESS.getValue(), 0L))
				.completed(counts.getOrDefault(TournamentStatus.COMPLETED.getValue(), 0L))
				.cancelled(counts.getOrDefault(TournamentStatus.CANCELLED.getValue(), 0L))
				.totalPrizePool(totalPrizePool)
				.byStatus(byStatus)
				.byGameType(byGameType)
				.monthlyTrend(monthlyCountTrend(tournaments.stream().map(Tournament::getCreatedAt).toList()))
				.build();
	}

	private RegistrationStatsResponse buildRegistrationStats(List<Registration> registrations) {
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

		return RegistrationStatsResponse.builder()
				.total(registrations.size())
				.pending(counts.getOrDefault(RegistrationStatus.PENDING_PAYMENT.getValue(), 0L))
				.approved(counts.getOrDefault(RegistrationStatus.APPROVED.getValue(), 0L))
				.rejected(counts.getOrDefault(RegistrationStatus.REJECTED.getValue(), 0L))
				.cancelled(counts.getOrDefault(RegistrationStatus.CANCELLED.getValue(), 0L))
				.byStatus(byStatus)
				.monthlyTrend(monthlyCountTrend(registrations.stream().map(Registration::getCreatedAt).toList()))
				.build();
	}

	private ParticipantStatsResponse buildParticipantStats(List<Participant> participants) {
		Map<String, Long> counts = participants.stream()
				.collect(Collectors.groupingBy(Participant::getStatus, Collectors.counting()));

		List<StatusCountItem> byStatus = Arrays.stream(ParticipantStatus.values())
				.map(s -> StatusCountItem.builder()
						.status(s.getValue())
						.label(s.getDisplayName())
						.count(counts.getOrDefault(s.getValue(), 0L))
						.build())
				.filter(i -> i.getCount() > 0)
				.toList();

		return ParticipantStatsResponse.builder()
				.total(participants.size())
				.active(counts.getOrDefault(ParticipantStatus.ACTIVE.getValue(), 0L))
				.withdrawn(counts.getOrDefault(ParticipantStatus.WITHDRAWN.getValue(), 0L))
				.byStatus(byStatus)
				.build();
	}

	private RevenueStatsResponse buildRevenueStats(List<Payment> payments) {
		Map<String, Long> counts = payments.stream()
				.collect(Collectors.groupingBy(Payment::getStatus, Collectors.counting()));

		BigDecimal total = payments.stream()
				.filter(p -> PaymentStatus.SUCCESS.getValue().equals(p.getStatus()))
				.map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		long successCount = counts.getOrDefault(PaymentStatus.SUCCESS.getValue(), 0L);
		BigDecimal avgTicketValue = successCount > 0
				? total.divide(BigDecimal.valueOf(successCount), 0, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		List<StatusCountItem> byStatus = Arrays.stream(PaymentStatus.values())
				.map(s -> StatusCountItem.builder()
						.status(s.getValue())
						.label(s.getDisplayName())
						.count(counts.getOrDefault(s.getValue(), 0L))
						.build())
				.filter(i -> i.getCount() > 0)
				.toList();

		return RevenueStatsResponse.builder()
				.totalRevenue(total)
				.successCount(successCount)
				.pendingCount(counts.getOrDefault(PaymentStatus.PENDING.getValue(), 0L))
				.failedCount(counts.getOrDefault(PaymentStatus.FAILED.getValue(), 0L))
				.avgTicketValue(avgTicketValue)
				.byStatus(byStatus)
				.monthlyTrend(monthlyRevenueTrend(payments))
				.build();
	}

	private MatchStatsResponse buildMatchStats(List<Match> matches) {
		Map<String, Long> counts = matches.stream()
				.collect(Collectors.groupingBy(Match::getStatus, Collectors.counting()));

		long completed = matches.stream()
				.filter(m -> isResolved(m.getStatus()))
				.count();
		long total = matches.size();

		List<StatusCountItem> byStatus = Arrays.stream(MatchStatus.values())
				.map(s -> StatusCountItem.builder()
						.status(s.getValue())
						.label(s.getDisplayName())
						.count(counts.getOrDefault(s.getValue(), 0L))
						.build())
				.filter(i -> i.getCount() > 0)
				.toList();

		return MatchStatsResponse.builder()
				.total(total)
				.completed(completed)
				.inProgress(counts.getOrDefault(MatchStatus.IN_PROGRESS.getValue(), 0L))
				.pending(counts.getOrDefault(MatchStatus.PENDING.getValue(), 0L))
				.completionRate(total > 0 ? (completed * 100.0 / total) : 0.0)
				.byStatus(byStatus)
				.build();
	}

	private boolean isResolved(String status) {
		try {
			return MatchStatus.valueOf(status).isResolved();
		} catch (IllegalArgumentException | NullPointerException e) {
			return false;
		}
	}

	private List<YearMonth> lastMonths(int count) {
		YearMonth current = YearMonth.now(ZONE);
		List<YearMonth> months = new ArrayList<>();
		for (int i = count - 1; i >= 0; i--) {
			months.add(current.minusMonths(i));
		}
		return months;
	}

	private List<TrendPointResponse> monthlyCountTrend(List<Instant> timestamps) {
		Map<YearMonth, Long> byMonth = timestamps.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.groupingBy(t -> YearMonth.from(t.atZone(ZONE)), Collectors.counting()));

		return lastMonths(TREND_MONTHS).stream()
				.map(ym -> TrendPointResponse.builder()
						.period(ym.format(PERIOD_FORMATTER))
						.count(byMonth.getOrDefault(ym, 0L))
						.build())
				.toList();
	}

	private List<TrendPointResponse> monthlyRevenueTrend(List<Payment> payments) {
		Map<YearMonth, BigDecimal> sumByMonth = new HashMap<>();
		Map<YearMonth, Long> countByMonth = new HashMap<>();

		for (Payment p : payments) {
			if (!PaymentStatus.SUCCESS.getValue().equals(p.getStatus())) continue;
			Instant ts = p.getPaidAt() != null ? p.getPaidAt() : p.getCreatedAt();
			if (ts == null) continue;
			YearMonth ym = YearMonth.from(ts.atZone(ZONE));
			sumByMonth.merge(ym, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
			countByMonth.merge(ym, 1L, Long::sum);
		}

		return lastMonths(TREND_MONTHS).stream()
				.map(ym -> TrendPointResponse.builder()
						.period(ym.format(PERIOD_FORMATTER))
						.count(countByMonth.getOrDefault(ym, 0L))
						.amount(sumByMonth.getOrDefault(ym, BigDecimal.ZERO))
						.build())
				.toList();
	}
}
