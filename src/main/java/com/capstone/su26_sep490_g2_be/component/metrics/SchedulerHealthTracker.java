package com.capstone.su26_sep490_g2_be.component.metrics;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SchedulerHealthTracker {

	private final Map<String, JobStatus> jobs = new ConcurrentHashMap<>();

	public void runTracked(String jobName, Runnable job) {
		long start = System.currentTimeMillis();
		try {
			job.run();
			jobs.put(jobName, new JobStatus(Instant.now(), System.currentTimeMillis() - start, true, null));
		} catch (RuntimeException ex) {
			jobs.put(jobName, new JobStatus(Instant.now(), System.currentTimeMillis() - start, false, ex.getMessage()));
			throw ex;
		}
	}

	public Map<String, JobStatus> snapshot() {
		return Map.copyOf(jobs);
	}

	@Getter
	public static class JobStatus {
		private final Instant lastRunAt;
		private final long lastDurationMs;
		private final boolean success;
		private final String lastError;

		public JobStatus(Instant lastRunAt, long lastDurationMs, boolean success, String lastError) {
			this.lastRunAt = lastRunAt;
			this.lastDurationMs = lastDurationMs;
			this.success = success;
			this.lastError = lastError;
		}
	}
}
