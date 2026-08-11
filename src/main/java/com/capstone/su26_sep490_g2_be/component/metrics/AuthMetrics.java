package com.capstone.su26_sep490_g2_be.component.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthMetrics {

	private final MeterRegistry meterRegistry;

	public void recordFailure(String reason) {
		meterRegistry.counter("auth.failed", "reason", reason).increment();
	}
}
