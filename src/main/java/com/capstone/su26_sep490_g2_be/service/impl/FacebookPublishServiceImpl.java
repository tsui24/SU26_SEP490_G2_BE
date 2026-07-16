package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.FacebookProperties;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.FacebookPublishService;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FacebookPublishServiceImpl implements FacebookPublishService {

	private final WebClient webClient;
	private final FacebookProperties fbProps;
	private final MinioStorageService minioStorageService;
	private final FacebookTokenManager tokenManager;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public FacebookPublishServiceImpl(WebClient.Builder webClientBuilder,
			FacebookProperties fbProps,
			MinioStorageService minioStorageService,
			FacebookTokenManager tokenManager) {
		this.webClient = webClientBuilder
				.baseUrl(fbProps.getGraphBaseUrl())
				.build();
		this.fbProps = fbProps;
		this.minioStorageService = minioStorageService;
		this.tokenManager = tokenManager;
	}

	private String getToken() {
		return tokenManager.getPageAccessToken();
	}

	// ─── Text / URL-based methods ────────────────────────────────────────

	@Override
	public String publishTextPost(String message, String link) {
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("message", message);
		body.add("access_token", getToken());
		if (link != null && !link.isBlank()) {
			body.add("link", link);
		}

		JsonNode response = postFormToFacebook("/" + fbProps.getPageId() + "/feed", body);
		String postId = response.path("id").asText();
		log.info("Facebook text post created: {}", postId);
		return postId;
	}

	@Override
	public String publishPhotoPost(String message, String imageUrl) {
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("caption", message);
		body.add("url", imageUrl);
		body.add("access_token", getToken());

		JsonNode response = postFormToFacebook("/" + fbProps.getPageId() + "/photos", body);
		return extractPhotoPostId(response);
	}

	@Override
	public String publishMultiPhotoPost(String message, List<String> imageUrls) {
		if (imageUrls == null || imageUrls.isEmpty()) {
			return publishTextPost(message, null);
		}
		if (imageUrls.size() == 1) {
			return publishPhotoPost(message, imageUrls.get(0));
		}

		List<String> photoIds = new ArrayList<>();
		for (String url : imageUrls) {
			MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
			body.add("url", url);
			body.add("published", "false");
			body.add("access_token", getToken());
			JsonNode resp = postFormToFacebook("/" + fbProps.getPageId() + "/photos", body);
			photoIds.add(resp.path("id").asText());
		}

		return createMultiPhotoFeedPost(message, photoIds);
	}

	// ─── MinIO-based methods (binary upload) ─────────────────────────────

	@Override
	public String publishPhotoFromMinio(String message, String minioObjectKey) {
		byte[] imageBytes = minioStorageService.getObjectBytes(minioObjectKey);
		String filename = extractFilename(minioObjectKey);

		JsonNode response = uploadBinaryPhoto(imageBytes, filename, message, true);
		return extractPhotoPostId(response);
	}

	@Override
	public String publishMultiPhotoFromMinio(String message, List<String> minioObjectKeys) {
		if (minioObjectKeys == null || minioObjectKeys.isEmpty()) {
			return publishTextPost(message, null);
		}
		if (minioObjectKeys.size() == 1) {
			return publishPhotoFromMinio(message, minioObjectKeys.get(0));
		}

		List<String> photoIds = new ArrayList<>();
		for (String objectKey : minioObjectKeys) {
			byte[] imageBytes = minioStorageService.getObjectBytes(objectKey);
			String filename = extractFilename(objectKey);
			JsonNode resp = uploadBinaryPhoto(imageBytes, filename, null, false);
			photoIds.add(resp.path("id").asText());
		}

		return createMultiPhotoFeedPost(message, photoIds);
	}

	// ─── Internal helpers ────────────────────────────────────────────────

	private JsonNode uploadBinaryPhoto(byte[] imageBytes, String filename,
			String caption, boolean published) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("source", new ByteArrayResource(imageBytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		}).contentType(guessMediaType(filename));

		builder.part("access_token", getToken());
		builder.part("published", String.valueOf(published));
		if (caption != null && !caption.isBlank()) {
			builder.part("caption", caption);
		}

		try {
			String body = webClient.post()
					.uri("/" + fbProps.getPageId() + "/photos")
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(BodyInserters.fromMultipartData(builder.build()))
					.retrieve()
					.bodyToMono(String.class)
					.block();
			log.info("Facebook binary photo uploaded: {}", filename);
			return parseJson(body);
		} catch (WebClientResponseException ex) {
			log.error("Facebook photo upload error: {} — {}", ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
					"Facebook photo upload error: " + ex.getResponseBodyAsString());
		}
	}

	private String createMultiPhotoFeedPost(String message, List<String> photoIds) {
		MultiValueMap<String, String> feedBody = new LinkedMultiValueMap<>();
		feedBody.add("message", message);
		feedBody.add("access_token", getToken());
		for (int i = 0; i < photoIds.size(); i++) {
			feedBody.add("attached_media[" + i + "]",
					"{\"media_fbid\":\"" + photoIds.get(i) + "\"}");
		}

		JsonNode response = postFormToFacebook("/" + fbProps.getPageId() + "/feed", feedBody);
		String postId = response.path("id").asText();
		log.info("Facebook multi-photo post created: {} ({} photos)", postId, photoIds.size());
		return postId;
	}

	private JsonNode postFormToFacebook(String uri, MultiValueMap<String, String> formData) {
		try {
			String body = webClient.post()
					.uri(uri)
					.body(BodyInserters.fromFormData(formData))
					.retrieve()
					.bodyToMono(String.class)
					.block();
			return parseJson(body);
		} catch (WebClientResponseException ex) {
			if (isTokenExpiredError(ex)) {
				log.warn("Facebook token expired, invalidating cache and retrying...");
				tokenManager.invalidateCache();
				formData.set("access_token", getToken());
				return retryPost(uri, formData);
			}
			log.error("Facebook API error: {} — {}", ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
					"Facebook API error: " + ex.getResponseBodyAsString());
		}
	}

	private JsonNode retryPost(String uri, MultiValueMap<String, String> formData) {
		try {
			String body = webClient.post()
					.uri(uri)
					.body(BodyInserters.fromFormData(formData))
					.retrieve()
					.bodyToMono(String.class)
					.block();
			return parseJson(body);
		} catch (WebClientResponseException ex) {
			log.error("Facebook API retry failed: {} — {}", ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
					"Facebook API error (sau retry): " + ex.getResponseBodyAsString());
		}
	}

	private boolean isTokenExpiredError(WebClientResponseException ex) {
		String body = ex.getResponseBodyAsString();
		return body.contains("\"code\":190");
	}

	private String extractPhotoPostId(JsonNode response) {
		String photoId = response.path("id").asText();
		String postId = response.path("post_id").asText();
		log.info("Facebook photo: photoId={}, postId={}", photoId, postId);
		return postId.isBlank() ? photoId : postId;
	}

	private String extractFilename(String objectKey) {
		int lastSlash = objectKey.lastIndexOf('/');
		return lastSlash >= 0 ? objectKey.substring(lastSlash + 1) : objectKey;
	}

	private MediaType guessMediaType(String filename) {
		String lower = filename.toLowerCase();
		if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
		if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
		if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
		return MediaType.IMAGE_JPEG;
	}

	private JsonNode parseJson(String body) {
		try {
			return objectMapper.readTree(body);
		} catch (Exception ex) {
			log.error("Failed to parse Facebook response: {}", body);
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
					"Không parse được response từ Facebook");
		}
	}
}
