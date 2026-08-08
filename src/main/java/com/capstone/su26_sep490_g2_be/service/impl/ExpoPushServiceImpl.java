package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.DeviceToken;
import com.capstone.su26_sep490_g2_be.repository.DeviceTokenRepository;
import com.capstone.su26_sep490_g2_be.service.ExpoPushService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ExpoPushServiceImpl implements ExpoPushService {

	/** Expo từ chối gói quá 100 thông báo trong một lượt. */
	private static final int BATCH_SIZE = 100;

	private final WebClient webClient;
	private final DeviceTokenRepository deviceTokenRepository;

	public ExpoPushServiceImpl(
			WebClient.Builder webClientBuilder,
			DeviceTokenRepository deviceTokenRepository,
			@Value("${app.push.expo-url:https://exp.host/--/api/v2/push/send}") String expoUrl) {
		this.webClient = webClientBuilder.baseUrl(expoUrl).build();
		this.deviceTokenRepository = deviceTokenRepository;
	}

	@Override
	@Transactional
	public void sendToUsers(List<Long> userIds, String title, String body, Map<String, Object> data) {
		if (userIds == null || userIds.isEmpty()) {
			return;
		}

		List<Long> distinctIds = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
		if (distinctIds.isEmpty()) {
			return;
		}

		List<DeviceToken> devices = deviceTokenRepository.findByUserIdIn(distinctIds);
		if (devices.isEmpty()) {
			log.debug("Không có thiết bị nào đăng ký cho {} người nhận — bỏ qua push", distinctIds.size());
			return;
		}

		List<String> tokens = devices.stream().map(DeviceToken::getExpoToken).distinct().toList();

		for (int from = 0; from < tokens.size(); from += BATCH_SIZE) {
			List<String> batch = tokens.subList(from, Math.min(from + BATCH_SIZE, tokens.size()));
			sendBatch(batch, title, body, data);
		}
	}

	private void sendBatch(List<String> tokens, String title, String body, Map<String, Object> data) {
		List<Map<String, Object>> messages = new ArrayList<>(tokens.size());
		for (String token : tokens) {
			Map<String, Object> message = new HashMap<>();
			message.put("to", token);
			message.put("title", title);
			message.put("body", body);
			message.put("sound", "default");
			if (data != null && !data.isEmpty()) {
				message.put("data", data);
			}
			messages.add(message);
		}

		try {
			JsonNode response = webClient.post()
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(messages)
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block(Duration.ofSeconds(15));

			handleResponse(tokens, response);
		} catch (Exception e) {
			// Push hỏng không được phép làm hỏng nghiệp vụ đã commit trước đó — người dùng vẫn
			// nhận được email, và vẫn thấy thông báo khi mở app.
			log.warn("Gửi push tới Expo thất bại cho {} thiết bị: {}", tokens.size(), e.getMessage());
		}
	}

	/**
	 * Expo trả về mảng kết quả theo đúng thứ tự đã gửi lên. Token của app đã gỡ cài đặt sẽ báo
	 * {@code DeviceNotRegistered} — xoá ngay, vì Expo sẽ chặn hẳn nếu cứ gửi mãi vào token chết.
	 */
	private void handleResponse(List<String> tokens, JsonNode response) {
		if (response == null) {
			return;
		}

		JsonNode results = response.path("data");
		if (!results.isArray()) {
			log.warn("Phản hồi Expo không có mảng data: {}", response);
			return;
		}

		List<String> deadTokens = new ArrayList<>();
		for (int i = 0; i < results.size() && i < tokens.size(); i++) {
			JsonNode result = results.get(i);
			if (!"error".equals(result.path("status").asText())) {
				continue;
			}

			String errorType = result.path("details").path("error").asText();
			if ("DeviceNotRegistered".equals(errorType)) {
				deadTokens.add(tokens.get(i));
			} else {
				log.warn("Expo từ chối một thông báo ({}): {}", errorType, result.path("message").asText());
			}
		}

		if (!deadTokens.isEmpty()) {
			deviceTokenRepository.deleteByExpoTokenIn(deadTokens);
			log.info("Đã xoá {} push token không còn hiệu lực", deadTokens.size());
		}
	}
}
