package com.capstone.su26_sep490_g2_be.api;

import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.util.WebClientUriBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Mẫu client gọi API ngoài. Tạo class tương tự cho từng third-party service.
 */
@Component
public class ExternalApiClient {

	private final WebClient webClient;
	private final String baseUrl;

	public ExternalApiClient(
			WebClient.Builder webClientBuilder,
			@Value("${app.external-api.base-url}") String baseUrl) {
		this.webClient = webClientBuilder.build();
		this.baseUrl = baseUrl;
	}

//	public String getSampleResource(String resourceId) {
//		String url = WebClientUriBuilder.buildWithPathVariable(baseUrl, "/resources/{id}", resourceId);
//		try {
//			return webClient.get()
//					.uri(url)
//					.retrieve()
//					.bodyToMono(String.class)
//					.block();
//		} catch (WebClientResponseException ex) {
//			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, ex.getMessage());
//		}
//	}
}
