package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.AdminDashboardStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.DashboardAlertItem;
import com.capstone.su26_sep490_g2_be.dto.response.StatusCountItem;
import com.capstone.su26_sep490_g2_be.dto.response.TrendPointResponse;
import com.capstone.su26_sep490_g2_be.entity.TournamentStatusHistory;
import com.capstone.su26_sep490_g2_be.enums.BranchStatus;
import com.capstone.su26_sep490_g2_be.enums.EmailSendStatus;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.enums.TournamentAuditChangeType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.EmailAutomationRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.EmailSendLogRepository;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFormTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentFormatDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentStatusHistoryRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
	private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");
	private static final int TREND_DAYS = 7;
	private static final int TREND_MONTHS = 6;
	private static final int RECENT_ALERTS_LIMIT = 8;

	private final UserRepository userRepository;
	private final BranchRepository branchRepository;
	private final TournamentRepository tournamentRepository;
	private final TournamentFormatDefinitionRepository formatDefinitionRepository;
	private final GameTypeDefinitionRepository gameTypeDefinitionRepository;
	private final RegistrationFormTemplateRepository registrationFormTemplateRepository;
	private final EmailSendLogRepository emailSendLogRepository;
	private final EmailAutomationRuleRepository emailAutomationRuleRepository;
	private final TournamentStatusHistoryRepository tournamentStatusHistoryRepository;

	@Override
	public AdminDashboardStatsResponse buildStats() {
		Instant since30d = Instant.now().minus(30, ChronoUnit.DAYS);
		Instant since7d = Instant.now().minus(TREND_DAYS - 1L, ChronoUnit.DAYS);

		List<Object[]> tournamentRows = tournamentRepository.findDashboardProjection();
		List<String> emailStatuses30d = emailSendLogRepository.findStatusesSince(since30d);

		return AdminDashboardStatsResponse.builder()
				.totalAccounts(userRepository.countByStatusNot(UserStatus.DELETED))
				.newAccounts30d(userRepository.findCreatedAtSince(since30d).size())
				.lockedAccounts(userRepository.countByStatus(UserStatus.LOCKED))
				.accountsByRole(accountsByRole())
				.newAccountsTrend(dailyAccountTrend(since7d))
				.totalBranches(branchRepository.count())
				.activeBranches(branchRepository.countByStatus(BranchStatus.ACTIVE))
				.ongoingTournaments(tournamentRows.stream()
						.filter(r -> TournamentStatus.IN_PROGRESS.getValue().equals(r[0]))
						.count())
				.tournamentsByStatus(tournamentsByStatus(tournamentRows))
				.newTournamentsTrend(monthlyTournamentTrend(tournamentRows))
				.formatUsage(formatUsage(tournamentRows))
				.gameTypeUsage(gameTypeUsage(tournamentRows))
				.registrationTemplateUsage(registrationTemplateUsage(tournamentRows))
				.totalEmails30d(emailStatuses30d.size())
				.emailsByStatus30d(emailsByStatus(emailStatuses30d))
				.emailSuccessRate30d(emailSuccessRate(emailStatuses30d))
				.enabledAutomationRules(emailAutomationRuleRepository.countByIsEnabled(true))
				.disabledAutomationRules(emailAutomationRuleRepository.countByIsEnabled(false))
				.recentAlerts(recentAlerts())
				.build();
	}

	private List<StatusCountItem> emailsByStatus(List<String> statuses) {
		Map<String, Long> counts = statuses.stream()
				.collect(Collectors.groupingBy(s -> s, Collectors.counting()));

		return Arrays.stream(EmailSendStatus.values())
				.map(s -> StatusCountItem.builder()
						.status(s.getValue())
						.label(s.getDisplayName())
						.count(counts.getOrDefault(s.getValue(), 0L))
						.build())
				.filter(i -> i.getCount() > 0)
				.toList();
	}

	private double emailSuccessRate(List<String> statuses) {
		if (statuses.isEmpty()) return 0.0;
		long sent = statuses.stream().filter(EmailSendStatus.SENT.getValue()::equals).count();
		return sent * 100.0 / statuses.size();
	}

	private List<StatusCountItem> accountsByRole() {
		Map<String, String> roleNames = Arrays.stream(RoleCode.values())
				.collect(Collectors.toMap(RoleCode::getCode, RoleCode::getDisplayName));

		return userRepository.countGroupByRole().stream()
				.map(row -> {
					String code = (String) row[0];
					long count = (Long) row[1];
					return StatusCountItem.builder()
							.status(code)
							.label(roleNames.getOrDefault(code, code))
							.count(count)
							.build();
				})
				.sorted(Comparator.comparingLong(StatusCountItem::getCount).reversed())
				.toList();
	}

	private List<TrendPointResponse> dailyAccountTrend(Instant since) {
		List<Instant> createdAts = userRepository.findCreatedAtSince(since);
		Map<LocalDate, Long> byDay = createdAts.stream()
				.collect(Collectors.groupingBy(t -> t.atZone(ZONE).toLocalDate(), Collectors.counting()));

		LocalDate today = LocalDate.now(ZONE);
		List<TrendPointResponse> trend = new ArrayList<>();
		for (int i = TREND_DAYS - 1; i >= 0; i--) {
			LocalDate day = today.minusDays(i);
			trend.add(TrendPointResponse.builder()
					.period(day.format(DAY_FORMATTER))
					.count(byDay.getOrDefault(day, 0L))
					.build());
		}
		return trend;
	}

	private List<StatusCountItem> tournamentsByStatus(List<Object[]> rows) {
		Map<String, Long> counts = rows.stream()
				.collect(Collectors.groupingBy(r -> (String) r[0], Collectors.counting()));

		return Arrays.stream(TournamentStatus.values())
				.map(s -> StatusCountItem.builder()
						.status(s.getValue())
						.label(s.getDisplayName())
						.count(counts.getOrDefault(s.getValue(), 0L))
						.build())
				.filter(i -> i.getCount() > 0)
				.toList();
	}

	private List<TrendPointResponse> monthlyTournamentTrend(List<Object[]> rows) {
		Map<YearMonth, Long> byMonth = rows.stream()
				.map(r -> (Instant) r[4])
				.filter(Objects::nonNull)
				.collect(Collectors.groupingBy(t -> YearMonth.from(t.atZone(ZONE)), Collectors.counting()));

		YearMonth current = YearMonth.now(ZONE);
		List<TrendPointResponse> trend = new ArrayList<>();
		for (int i = TREND_MONTHS - 1; i >= 0; i--) {
			YearMonth ym = current.minusMonths(i);
			trend.add(TrendPointResponse.builder()
					.period(ym.format(MONTH_FORMATTER))
					.count(byMonth.getOrDefault(ym, 0L))
					.build());
		}
		return trend;
	}

	private List<StatusCountItem> formatUsage(List<Object[]> rows) {
		Map<String, Long> counts = rows.stream()
				.collect(Collectors.groupingBy(r -> (String) r[1], Collectors.counting()));

		return formatDefinitionRepository.findAllByOrderByCreatedAtAsc().stream()
				.map(f -> StatusCountItem.builder()
						.status(f.getCode())
						.label(f.getName())
						.count(counts.getOrDefault(f.getCode(), 0L))
						.build())
				.sorted(Comparator.comparingLong(StatusCountItem::getCount).reversed())
				.toList();
	}

	private List<StatusCountItem> gameTypeUsage(List<Object[]> rows) {
		Map<String, Long> counts = rows.stream()
				.collect(Collectors.groupingBy(r -> (String) r[2], Collectors.counting()));

		return gameTypeDefinitionRepository.findAllByOrderBySortOrderAscCreatedAtAsc().stream()
				.map(g -> StatusCountItem.builder()
						.status(g.getCode())
						.label(g.getName())
						.count(counts.getOrDefault(g.getCode(), 0L))
						.build())
				.sorted(Comparator.comparingLong(StatusCountItem::getCount).reversed())
				.toList();
	}

	private List<StatusCountItem> registrationTemplateUsage(List<Object[]> rows) {
		Map<Long, Long> counts = rows.stream()
				.map(r -> (Long) r[3])
				.filter(Objects::nonNull)
				.collect(Collectors.groupingBy(id -> id, Collectors.counting()));

		return registrationFormTemplateRepository.findAll().stream()
				.map(t -> StatusCountItem.builder()
						.status(t.getCode())
						.label(t.getName())
						.count(counts.getOrDefault(t.getId(), 0L))
						.build())
				.sorted(Comparator.comparingLong(StatusCountItem::getCount).reversed())
				.toList();
	}

	private List<DashboardAlertItem> recentAlerts() {
		List<DashboardAlertItem> warnings = tournamentStatusHistoryRepository
				.findRecentByChangeType(TournamentAuditChangeType.WARNING.getValue(), PageRequest.of(0, 5)).stream()
				.map(h -> DashboardAlertItem.builder()
						.type("TOURNAMENT_WARNING")
						.title("Cảnh báo giải đấu: " + safeName(h))
						.detail(h.getNote())
						.occurredAt(h.getCreatedAt())
						.build())
				.toList();

		List<DashboardAlertItem> failedEmails = emailSendLogRepository
				.findFirst5ByStatusOrderByCreatedAtDesc(EmailSendStatus.FAILED.getValue()).stream()
				.map(l -> DashboardAlertItem.builder()
						.type("EMAIL_FAILED")
						.title("Gửi email thất bại: " + l.getRecipientEmail())
						.detail(l.getErrorMessage())
						.occurredAt(l.getCreatedAt())
						.build())
				.toList();

		return Stream.concat(warnings.stream(), failedEmails.stream())
				.sorted(Comparator.comparing(DashboardAlertItem::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(RECENT_ALERTS_LIMIT)
				.toList();
	}

	private String safeName(TournamentStatusHistory h) {
		return h.getTournament() != null ? h.getTournament().getName() : "N/A";
	}
}
