package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.FacebookProperties;
import com.capstone.su26_sep490_g2_be.service.FacebookInsightsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FacebookInsightsServiceImpl implements FacebookInsightsService {

	private final WebClient webClient;
	private final FacebookProperties fbProps;
	private final FacebookTokenManager tokenManager;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public FacebookInsightsServiceImpl(WebClient.Builder webClientBuilder,
			FacebookProperties fbProps,
			FacebookTokenManager tokenManager) {
		this.webClient = webClientBuilder
				.baseUrl(fbProps.getGraphBaseUrl())
				.build();
		this.fbProps = fbProps;
		this.tokenManager = tokenManager;
	}

	@Override
	public Map<String, Object> getPostEngagement(String facebookPostId) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("facebookPostId", facebookPostId);

		// 1) Thông tin cơ bản — không cần pages_read_engagement
		try {
			JsonNode basic = queryGraph(
					"/" + facebookPostId,
					Map.of("fields", "id,message,created_time,full_picture,permalink_url"));
			result.put("message", truncate(basic.path("message").asText(""), 200));
			result.put("createdTime", basic.path("created_time").asText());
			result.put("fullPicture", basic.path("full_picture").asText(null));
			result.put("permalinkUrl", basic.path("permalink_url").asText(null));
		} catch (Exception ex) {
			log.warn("Could not fetch basic post info for {}: {}", facebookPostId, ex.getMessage());
		}

		// 2) likes/comments/shares — cần pages_read_engagement
		boolean engagementLoaded = false;
		try {
			JsonNode engagement = queryGraph(
					"/" + facebookPostId,
					Map.of("fields", "likes.summary(true).limit(0),comments.summary(true).limit(0),shares"));
			result.put("likes", engagement.path("likes").path("summary").path("total_count").asInt(0));
			result.put("comments", engagement.path("comments").path("summary").path("total_count").asInt(0));
			result.put("shares", engagement.path("shares").path("count").asInt(0));
			engagementLoaded = true;
		} catch (Exception ex) {
			log.warn("likes/comments/shares unavailable for {}: {}", facebookPostId, ex.getMessage());
		}

		// 3) Fallback từ Insights (chỉ cần read_insights) — không phụ thuộc pages_read_engagement
		if (!engagementLoaded) {
			int likes = 0;
			int comments = 0;
			int shares = 0;
			boolean reactionsOk = false;
			boolean activityOk = false;
			Map<String, Integer> byType = new LinkedHashMap<>();
			Map<String, Integer> activityRaw = new LinkedHashMap<>();
			List<String> sources = new ArrayList<>();

			// reactions (like/love/haha/...)
			try {
				JsonNode reactionsData = queryGraph(
						"/" + facebookPostId + "/insights",
						Map.of(
								"metric", "post_reactions_by_type_total",
								"period", "lifetime"));
				JsonNode value = firstInsightValue(reactionsData);
				if (value != null && value.isObject()) {
					int totalReactions = 0;
					var it = value.fields();
					while (it.hasNext()) {
						var e = it.next();
						int count = e.getValue().asInt(0);
						byType.put(e.getKey(), count);
						totalReactions += count;
					}
					likes = totalReactions;
					reactionsOk = true;
					sources.add("post_reactions_by_type_total");
				}
			} catch (Exception ex) {
				log.warn("post_reactions_by_type_total unavailable: {}", ex.getMessage());
			}

			// activity theo action type: like / comment / share
			try {
				JsonNode activityData = queryGraph(
						"/" + facebookPostId + "/insights",
						Map.of(
								"metric", "post_activity_by_action_type",
								"period", "lifetime"));
				JsonNode value = firstInsightValue(activityData);
				if (value != null && value.isObject()) {
					var it = value.fields();
					while (it.hasNext()) {
						var e = it.next();
						activityRaw.put(e.getKey(), e.getValue().asInt(0));
					}
					// FB có thể trả key số ít hoặc số nhiều
					comments = firstInt(value, "comment", "comments");
					shares = firstInt(value, "share", "shares");
					int likeFromActivity = firstInt(value, "like", "likes");
					if (!reactionsOk) {
						likes = likeFromActivity;
					}
					activityOk = true;
					sources.add("post_activity_by_action_type");
				}
			} catch (Exception ex) {
				log.warn("post_activity_by_action_type unavailable: {}", ex.getMessage());
			}

			result.put("likes", likes);
			result.put("comments", comments);
			result.put("shares", shares);
			if (!byType.isEmpty()) {
				result.put("reactionsByType", byType);
			}
			if (!activityRaw.isEmpty()) {
				result.put("activityByActionType", activityRaw);
			}
			if (!sources.isEmpty()) {
				result.put("engagementSource", "insights:" + String.join("+", sources));
				result.put("engagementNote", activityOk
						? "likes/comments/shares lấy từ Page Insights (period=lifetime)."
						: "Chỉ lấy được reactions; comments/shares mặc định 0 vì thiếu metric activity.");
			} else {
				result.put("engagementNote", "Không lấy được lượt tương tác từ Insights");
			}
			result.put("insightsAvailable", reactionsOk || activityOk);
		}

		return result;
	}

	@Override
	public Map<String, Object> getPostInsights(String facebookPostId) {
		Map<String, Object> result = new LinkedHashMap<>(getPostEngagement(facebookPostId));

		String[][] metricGroups = {
				{"post_impressions", "post_impressions_unique", "post_engaged_users"},
				{"post_reactions_by_type_total"},
				{"post_activity_by_action_type"},
				{"post_clicks_by_type"},
		};

		Map<String, Object> insights = new HashMap<>();
		for (String[] group : metricGroups) {
			try {
				JsonNode insightsData = queryGraph(
						"/" + facebookPostId + "/insights",
						Map.of(
								"metric", String.join(",", group),
								"period", "lifetime"));
				JsonNode dataArray = insightsData.path("data");

				if (dataArray.isArray()) {
					for (JsonNode metric : dataArray) {
						String name = metric.path("name").asText();
						JsonNode values = metric.path("values");
						if (values.isArray() && !values.isEmpty()) {
							JsonNode value = values.get(0).path("value");
							if (value.isObject()) {
								Map<String, Object> map = new LinkedHashMap<>();
								value.fields().forEachRemaining(f -> map.put(f.getKey(), f.getValue().asInt(0)));
								insights.put(name, map);
							} else {
								insights.put(name, value.asInt(0));
							}
						}
					}
				}
			} catch (Exception ex) {
				log.debug("Metric group [{}] not available: {}", String.join(",", group), ex.getMessage());
			}
		}

		if (insights.isEmpty()) {
			result.put("insights", null);
			result.put("insightsNote", "Insights chưa có — bài đăng có thể quá mới hoặc thiếu quyền read_insights");
		} else {
			result.put("insights", insights);
		}

		return result;
	}

	@Override
	public Map<String, Object> debugCurrentToken() {
		Map<String, Object> result = new LinkedHashMap<>();
		String token = tokenManager.getPageAccessToken();
		result.put("tokenPreview", token.length() > 20
				? token.substring(0, 10) + "..." + token.substring(token.length() - 5) : "***");
		result.put("tokenLength", token.length());

		try {
			String debugUrl = UriComponentsBuilder
					.fromPath("/debug_token")
					.queryParam("input_token", token)
					.queryParam("access_token", fbProps.getAppId() + "|" + fbProps.getAppSecret())
					.build()
					.encode()
					.toUriString();

			String body = webClient.get().uri(debugUrl).retrieve().bodyToMono(String.class).block();
			JsonNode data = parseJson(body).path("data");

			result.put("appId", data.path("app_id").asText(null));
			result.put("type", data.path("type").asText(null));
			result.put("isValid", data.path("is_valid").asBoolean(false));
			result.put("expiresAt", data.path("expires_at").asInt(0) == 0
					? "Không hết hạn" : data.path("expires_at").asText());

			List<String> scopes = new ArrayList<>();
			JsonNode scopesNode = data.path("scopes");
			if (scopesNode.isArray()) {
				for (JsonNode s : scopesNode) {
					scopes.add(s.asText());
				}
			}
			result.put("scopes", scopes);

			List<String> missing = new ArrayList<>();
			List<String> required = List.of("pages_manage_posts", "pages_show_list",
					"pages_read_engagement", "read_insights");
			for (String r : required) {
				if (!scopes.contains(r)) missing.add(r);
			}
			result.put("missingScopes", missing);

		} catch (Exception ex) {
			result.put("debugError", "Không debug được token: " + ex.getMessage());
		}

		return result;
	}

	private int firstInt(JsonNode value, String... keys) {
		for (String key : keys) {
			if (value.has(key) && !value.get(key).isNull()) {
				return value.get(key).asInt(0);
			}
		}
		return 0;
	}

	private JsonNode firstInsightValue(JsonNode insightsData) {
		JsonNode dataArray = insightsData.path("data");
		if (!dataArray.isArray() || dataArray.isEmpty()) {
			return null;
		}
		JsonNode values = dataArray.get(0).path("values");
		if (!values.isArray() || values.isEmpty()) {
			return null;
		}
		return values.get(0).path("value");
	}

	/**
	 * Gọi Graph API với query params được encode đúng (tránh lỗi với likes.summary(true)).
	 */
	private JsonNode queryGraph(String path, Map<String, String> queryParams) {
		try {
			UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
			for (Map.Entry<String, String> e : queryParams.entrySet()) {
				builder.queryParam(e.getKey(), e.getValue());
			}
			builder.queryParam("access_token", tokenManager.getPageAccessToken());

			String uri = builder.build().encode().toUriString();
			String body = webClient.get()
					.uri(uri)
					.retrieve()
					.bodyToMono(String.class)
					.block();
			return parseJson(body);
		} catch (WebClientResponseException ex) {
			log.error("Facebook Graph API error: {} — {}", ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new RuntimeException("Facebook API error: " + ex.getResponseBodyAsString(), ex);
		}
	}

	private JsonNode parseJson(String body) {
		try {
			return objectMapper.readTree(body);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to parse Facebook response", ex);
		}
	}

	private String truncate(String s, int maxLen) {
		if (s == null) return "";
		return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
	}
}
