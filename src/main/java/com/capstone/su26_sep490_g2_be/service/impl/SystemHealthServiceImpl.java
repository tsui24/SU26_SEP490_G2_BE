package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.component.metrics.SchedulerHealthTracker;
import com.capstone.su26_sep490_g2_be.dto.response.SchedulerJobStatusItem;
import com.capstone.su26_sep490_g2_be.dto.response.StatusCountItem;
import com.capstone.su26_sep490_g2_be.dto.response.SystemHealthResponse;
import com.capstone.su26_sep490_g2_be.repository.EmailSendLogRepository;
import com.capstone.su26_sep490_g2_be.service.SystemHealthService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SystemHealthServiceImpl implements SystemHealthService {

	private static final Map<String, String> AUTH_FAILURE_LABELS = Map.of(
			"MISSING_TOKEN", "Thiếu token",
			"INVALID_TOKEN", "Token không hợp lệ",
			"ACCOUNT_LOCKED", "Tài khoản bị khóa/xóa");

	private final MeterRegistry meterRegistry;
	private final JdbcTemplate jdbcTemplate;
	private final Environment environment;
	private final SchedulerHealthTracker schedulerHealthTracker;
	private final EmailSendLogRepository emailSendLogRepository;

	@Override
	public SystemHealthResponse build() {
		boolean dbConnected = pingDb();

		double heapUsed = sumGaugeValues("jvm.memory.used", "area", "heap");
		double heapMax = sumGaugeValues("jvm.memory.max", "area", "heap");
		double heapPercent = heapMax > 0 ? heapUsed * 100.0 / heapMax : 0;

		double cpuUsage = findGaugeValue("system.cpu.usage");
		if (Double.isNaN(cpuUsage)) {
			cpuUsage = -1;
		}

		DbSizeInfo dbSizeInfo = fetchDbSize();
		HttpTrafficSummary traffic = summarizeHttpTraffic();

		return SystemHealthResponse.builder()
				.appStatus(dbConnected ? "UP" : "DEGRADED")
				.dbConnected(dbConnected)
				.uptimeSeconds((long) findGaugeValue("process.uptime"))
				.javaVersion(System.getProperty("java.version"))
				.activeProfile(activeProfile())
				.heapUsedBytes(heapUsed)
				.heapMaxBytes(heapMax)
				.heapUsedPercent(heapPercent)
				.threadCount((long) findGaugeValue("jvm.threads.live"))
				.cpuUsagePercent(cpuUsage >= 0 ? cpuUsage * 100.0 : -1)
				.dbPoolActive(findGaugeValue("hikaricp.connections.active"))
				.dbPoolIdle(findGaugeValue("hikaricp.connections.idle"))
				.dbPoolMax(findGaugeValue("hikaricp.connections.max"))
				.dbSizeBytes(dbSizeInfo.sizeBytes())
				.dbTableCount(dbSizeInfo.tableCount())
				.httpTotalRequests(traffic.totalRequests())
				.httpErrorRequests(traffic.errorRequests())
				.httpErrorRatePercent(traffic.errorRatePercent())
				.httpAvgLatencyMs(traffic.avgLatencyMs())
				.httpMaxLatencyMs(traffic.maxLatencyMs())
				.topErrorEndpoints(traffic.topErrorEndpoints())
				.httpRequestsByStatusClass(traffic.requestsByStatusClass())
				.authFailuresByReason(authFailuresByReason())
				.schedulerJobs(schedulerJobs())
				.mailQueueAvgLatencyMs(mailQueueAvgLatencyMs())
				.build();
	}

	private boolean pingDb() {
		try {
			Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			return result != null;
		} catch (DataAccessException ex) {
			return false;
		}
	}

	private String activeProfile() {
		String[] active = environment.getActiveProfiles();
		return active.length > 0 ? String.join(",", active) : String.join(",", environment.getDefaultProfiles());
	}

	private double findGaugeValue(String name) {
		Gauge gauge = meterRegistry.find(name).gauge();
		return gauge != null ? gauge.value() : -1;
	}

	private double sumGaugeValues(String name, String tagKey, String tagValue) {
		return meterRegistry.find(name).tag(tagKey, tagValue).gauges().stream()
				.mapToDouble(Gauge::value)
				.sum();
	}

	private record DbSizeInfo(long sizeBytes, long tableCount) {
	}

	private DbSizeInfo fetchDbSize() {
		try {
			Map<String, Object> row = jdbcTemplate.queryForMap(
					"SELECT COALESCE(SUM(data_length + index_length), 0) AS sizeBytes, COUNT(*) AS tableCount " +
							"FROM information_schema.tables WHERE table_schema = DATABASE()");
			return new DbSizeInfo(((Number) row.get("sizeBytes")).longValue(), ((Number) row.get("tableCount")).longValue());
		} catch (DataAccessException ex) {
			return new DbSizeInfo(0, 0);
		}
	}

	private record HttpTrafficSummary(long totalRequests, long errorRequests, double errorRatePercent,
			double avgLatencyMs, double maxLatencyMs, List<StatusCountItem> topErrorEndpoints,
			List<StatusCountItem> requestsByStatusClass) {
	}

	private HttpTrafficSummary summarizeHttpTraffic() {
		var timers = meterRegistry.find("http.server.requests").timers();
		long total = 0;
		long errors = 0;
		double totalTimeMs = 0;
		double maxMs = 0;
		Map<String, Long> errorsByUri = new HashMap<>();
		Map<String, Long> byStatusClass = new HashMap<>();

		for (Timer timer : timers) {
			long count = timer.count();
			total += count;
			totalTimeMs += timer.totalTime(TimeUnit.MILLISECONDS);
			maxMs = Math.max(maxMs, timer.max(TimeUnit.MILLISECONDS));
			String status = timer.getId().getTag("status");
			String uri = timer.getId().getTag("uri");
			if (status != null && !status.isEmpty()) {
				String statusClass = status.charAt(0) + "xx";
				byStatusClass.merge(statusClass, count, Long::sum);
			}
			if (status != null && (status.startsWith("4") || status.startsWith("5"))) {
				errors += count;
				if (uri != null) {
					errorsByUri.merge(uri, count, Long::sum);
				}
			}
		}

		double avgMs = total > 0 ? totalTimeMs / total : 0;
		double errorRate = total > 0 ? errors * 100.0 / total : 0;
		List<StatusCountItem> topErrors = errorsByUri.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.limit(5)
				.map(e -> StatusCountItem.builder().status(e.getKey()).label(e.getKey()).count(e.getValue()).build())
				.toList();
		List<StatusCountItem> statusClasses = byStatusClass.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(e -> StatusCountItem.builder().status(e.getKey()).label(e.getKey()).count(e.getValue()).build())
				.toList();

		return new HttpTrafficSummary(total, errors, errorRate, avgMs, maxMs, topErrors, statusClasses);
	}

	private List<StatusCountItem> authFailuresByReason() {
		return meterRegistry.find("auth.failed").counters().stream()
				.map(c -> {
					String reason = c.getId().getTag("reason");
					return StatusCountItem.builder()
							.status(reason)
							.label(AUTH_FAILURE_LABELS.getOrDefault(reason, reason))
							.count((long) c.count())
							.build();
				})
				.sorted(Comparator.comparingLong(StatusCountItem::getCount).reversed())
				.toList();
	}

	private List<SchedulerJobStatusItem> schedulerJobs() {
		return schedulerHealthTracker.snapshot().entrySet().stream()
				.map(e -> SchedulerJobStatusItem.builder()
						.name(e.getKey())
						.lastRunAt(e.getValue().getLastRunAt())
						.success(e.getValue().isSuccess())
						.lastDurationMs(e.getValue().getLastDurationMs())
						.lastError(e.getValue().getLastError())
						.build())
				.sorted(Comparator.comparing(SchedulerJobStatusItem::getName))
				.toList();
	}

	private double mailQueueAvgLatencyMs() {
		Instant since30d = Instant.now().minus(30, ChronoUnit.DAYS);
		List<Object[]> timings = emailSendLogRepository.findSentTimingsSince(since30d);
		double totalMs = 0;
		int count = 0;
		for (Object[] row : timings) {
			Instant createdAt = (Instant) row[0];
			Instant sentAt = (Instant) row[1];
			if (createdAt != null && sentAt != null) {
				totalMs += Duration.between(createdAt, sentAt).toMillis();
				count++;
			}
		}
		return count > 0 ? totalMs / count : 0;
	}
}
