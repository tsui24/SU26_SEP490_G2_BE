package com.capstone.su26_sep490_g2_be.dto.response;

import com.capstone.su26_sep490_g2_be.dto.request.AnalyticsQueryRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SavedViewResponse {
	private Long id;
	private String name;
	private AnalyticsQueryRequest config;
	private Instant createdAt;
}
