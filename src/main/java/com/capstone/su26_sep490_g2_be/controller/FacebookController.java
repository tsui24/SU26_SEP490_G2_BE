package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.config.MailProperties;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.entity.FacebookPost;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.repository.FacebookPostRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.FacebookInsightsService;
import com.capstone.su26_sep490_g2_be.service.FacebookPublishService;
import com.capstone.su26_sep490_g2_be.service.impl.FacebookTokenManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shared/facebook")
@RequiredArgsConstructor
@Tag(name = "Facebook (Owner + Manager)", description = "Đăng bài & thống kê Facebook Page")
public class FacebookController {

	private final FacebookPublishService facebookPublishService;
	private final FacebookInsightsService facebookInsightsService;
	private final FacebookTokenManager tokenManager;
	private final FacebookPostRepository facebookPostRepository;
	private final TournamentRepository tournamentRepository;
	private final UserRepository userRepository;
	private final MailProperties mailProperties;

	// ─── Publish endpoints ──────────────────────────────────────────────

	@PostMapping("/post/text")
	@Operation(summary = "Đăng bài text / link lên Facebook Page")
	public ResponseEntity<ApiResponse<Map<String, String>>> publishTextPost(
			@AuthenticationPrincipal UserDetails principal,
			@Valid @RequestBody TextPostRequest request) {
		String tournamentUrl = tournamentPublicUrl(request.getTournamentId());
		String message = appendTournamentLink(request.getMessage(), tournamentUrl);
		String link = (request.getLink() != null && !request.getLink().isBlank())
				? request.getLink()
				: tournamentUrl;
		String postId = facebookPublishService.publishTextPost(message, link);
		savePost(postId, message, "TEXT", request.getTournamentId(), principal);
		return ResponseEntity.ok(ApiResponse.success("Đăng bài Facebook thành công",
				Map.of("facebookPostId", postId)));
	}

	@PostMapping("/post/photo")
	@Operation(summary = "Đăng bài kèm 1 ảnh (URL) lên Facebook Page")
	public ResponseEntity<ApiResponse<Map<String, String>>> publishPhotoPost(
			@AuthenticationPrincipal UserDetails principal,
			@Valid @RequestBody PhotoPostRequest request) {
		String message = appendTournamentLink(request.getMessage(), tournamentPublicUrl(request.getTournamentId()));
		String postId = facebookPublishService.publishPhotoPost(message, request.getImageUrl());
		savePost(postId, message, "PHOTO", request.getTournamentId(), principal);
		return ResponseEntity.ok(ApiResponse.success("Đăng bài Facebook (có ảnh) thành công",
				Map.of("facebookPostId", postId)));
	}

	@PostMapping("/post/photos")
	@Operation(summary = "Đăng bài kèm nhiều ảnh (URL) lên Facebook Page")
	public ResponseEntity<ApiResponse<Map<String, String>>> publishMultiPhotoPost(
			@AuthenticationPrincipal UserDetails principal,
			@Valid @RequestBody MultiPhotoPostRequest request) {
		String message = appendTournamentLink(request.getMessage(), tournamentPublicUrl(request.getTournamentId()));
		String postId = facebookPublishService.publishMultiPhotoPost(message, request.getImageUrls());
		savePost(postId, message, "MULTI_PHOTO", request.getTournamentId(), principal);
		return ResponseEntity.ok(ApiResponse.success("Đăng bài Facebook (nhiều ảnh) thành công",
				Map.of("facebookPostId", postId)));
	}

	@PostMapping("/post/minio-photo")
	@Operation(summary = "Đăng bài kèm 1 ảnh từ MinIO (BE upload binary)")
	public ResponseEntity<ApiResponse<Map<String, String>>> publishMinioPhoto(
			@AuthenticationPrincipal UserDetails principal,
			@Valid @RequestBody MinioPhotoPostRequest request) {
		String message = appendTournamentLink(request.getMessage(), tournamentPublicUrl(request.getTournamentId()));
		String postId = facebookPublishService.publishPhotoFromMinio(
				message, request.getMinioObjectKey());
		savePost(postId, message, "PHOTO", request.getTournamentId(), principal);
		return ResponseEntity.ok(ApiResponse.success("Đăng bài Facebook (ảnh MinIO) thành công",
				Map.of("facebookPostId", postId)));
	}

	@PostMapping("/post/minio-photos")
	@Operation(summary = "Đăng bài kèm nhiều ảnh từ MinIO (BE upload binary)")
	public ResponseEntity<ApiResponse<Map<String, String>>> publishMinioPhotos(
			@AuthenticationPrincipal UserDetails principal,
			@Valid @RequestBody MinioMultiPhotoPostRequest request) {
		String message = appendTournamentLink(request.getMessage(), tournamentPublicUrl(request.getTournamentId()));
		String postId = facebookPublishService.publishMultiPhotoFromMinio(
				message, request.getMinioObjectKeys());
		savePost(postId, message, "MULTI_PHOTO", request.getTournamentId(), principal);
		return ResponseEntity.ok(ApiResponse.success("Đăng bài Facebook (nhiều ảnh MinIO) thành công",
				Map.of("facebookPostId", postId)));
	}

	// ─── Insights endpoints ─────────────────────────────────────────────

	@GetMapping("/posts")
	@Operation(summary = "Danh sách bài đã đăng (phân trang)")
	@Transactional(readOnly = true)
	public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> listPosts(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		Page<Map<String, Object>> result = facebookPostRepository
				.findAllByOrderByPostedAtDesc(PageRequest.of(page, size))
				.map(this::toPostSummary);
		return ResponseEntity.ok(ApiResponse.success(result));
	}

	@GetMapping("/posts/tournament/{tournamentId}")
	@Operation(summary = "Danh sách bài đã đăng cho 1 giải đấu")
	@Transactional(readOnly = true)
	public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listPostsByTournament(
			@PathVariable Long tournamentId) {
		List<Map<String, Object>> result = facebookPostRepository
				.findByTournamentIdOrderByPostedAtDesc(tournamentId)
				.stream().map(this::toPostSummary).toList();
		return ResponseEntity.ok(ApiResponse.success(result));
	}

	@GetMapping("/posts/{postRecordId}")
	@Operation(summary = "Chi tiết 1 bài đăng trong hệ thống (full content)")
	@Transactional(readOnly = true)
	public ResponseEntity<ApiResponse<Map<String, Object>>> getPost(
			@PathVariable Long postRecordId) {
		FacebookPost record = facebookPostRepository.findById(postRecordId)
				.orElseThrow(() -> new RuntimeException("Bài đăng không tồn tại trong hệ thống"));
		return ResponseEntity.ok(ApiResponse.success(toPostDetail(record)));
	}

	@GetMapping("/posts/{postRecordId}/engagement")
	@Operation(summary = "Lấy lượt tương tác — mặc định từ cache DB; refresh=true để gọi Facebook")
	@Transactional
	public ResponseEntity<ApiResponse<Map<String, Object>>> getEngagement(
			@PathVariable Long postRecordId,
			@RequestParam(defaultValue = "false") boolean refresh) {
		FacebookPost record = facebookPostRepository.findById(postRecordId)
				.orElseThrow(() -> new RuntimeException("Bài đăng không tồn tại trong hệ thống"));

		if (!refresh && record.getStatsSyncedAt() != null) {
			return ResponseEntity.ok(ApiResponse.success(cachedEngagement(record)));
		}

		Map<String, Object> engagement = facebookInsightsService.getPostEngagement(record.getFacebookPostId());
		applyEngagementCache(record, engagement);
		facebookPostRepository.save(record);
		engagement.put("fromCache", false);
		engagement.put("statsSyncedAt", record.getStatsSyncedAt());
		return ResponseEntity.ok(ApiResponse.success(engagement));
	}

	@GetMapping("/posts/{postRecordId}/insights")
	@Operation(summary = "Lấy insights — mặc định từ cache nếu có; refresh=true để gọi Facebook")
	@Transactional
	public ResponseEntity<ApiResponse<Map<String, Object>>> getInsights(
			@PathVariable Long postRecordId,
			@RequestParam(defaultValue = "false") boolean refresh) {
		FacebookPost record = facebookPostRepository.findById(postRecordId)
				.orElseThrow(() -> new RuntimeException("Bài đăng không tồn tại trong hệ thống"));

		Map<String, Object> response = new LinkedHashMap<>(toPostDetail(record));

		if (!refresh && record.getStatsSyncedAt() != null) {
			response.putAll(cachedEngagement(record));
			response.put("insights", Map.of(
					"post_impressions", nullSafe(record.getImpressions()),
					"post_impressions_unique", nullSafe(record.getReach()),
					"post_engaged_users", nullSafe(record.getEngagedUsers())));
			response.put("fromCache", true);
			response.put("content", record.getContent());
			return ResponseEntity.ok(ApiResponse.success(response));
		}

		Map<String, Object> insights = facebookInsightsService.getPostInsights(record.getFacebookPostId());
		applyEngagementCache(record, insights);
		if (insights.get("permalinkUrl") != null) {
			record.setPermalinkUrl(String.valueOf(insights.get("permalinkUrl")));
		}
		facebookPostRepository.save(record);

		response.putAll(insights);
		response.put("content", record.getContent());
		response.put("fromCache", false);
		response.put("statsSyncedAt", record.getStatsSyncedAt());
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	// ─── Token management ───────────────────────────────────────────────

	@PostMapping("/token/exchange")
	@Operation(summary = "Exchange short-lived User Token → Page Token không hết hạn")
	public ResponseEntity<ApiResponse<Map<String, String>>> exchangeToken(
			@Valid @RequestBody TokenExchangeRequest request) {
		String neverExpiringPageToken = tokenManager.exchangeToNeverExpiringPageToken(
				request.getShortLivedToken());
		return ResponseEntity.ok(ApiResponse.success(
				"Exchange thành công — Page Token đã được cập nhật",
				Map.of("pageAccessToken", neverExpiringPageToken)));
	}

	@GetMapping("/token/debug")
	@Operation(summary = "Kiểm tra Page Token đang dùng + quyền thực tế")
	public ResponseEntity<ApiResponse<Map<String, Object>>> debugToken() {
		Map<String, Object> info = facebookInsightsService.debugCurrentToken();
		return ResponseEntity.ok(ApiResponse.success("Token hiện tại", info));
	}

	// ─── Helpers ────────────────────────────────────────────────────────

	private String tournamentPublicUrl(Long tournamentId) {
		return mailProperties.tournamentPublicUrl(tournamentId);
	}

	/** Gắn URL trang giải vào nội dung nếu bài gắn tournament và chưa có sẵn link đó. */
	private String appendTournamentLink(String message, String tournamentUrl) {
		if (tournamentUrl == null || tournamentUrl.isBlank()) {
			return message;
		}
		String body = message == null ? "" : message;
		if (body.contains(tournamentUrl)) {
			return body;
		}
		return body + "\n\n🔗 Xem thông tin giải đấu:\n" + tournamentUrl;
	}

	private void savePost(String facebookPostId, String content, String postType,
			Long tournamentId, UserDetails principal) {
		FacebookPost post = FacebookPost.builder()
				.facebookPostId(facebookPostId)
				.content(content)
				.postType(postType)
				.postedAt(Instant.now())
				.build();

		if (tournamentId != null) {
			tournamentRepository.findById(tournamentId).ifPresent(post::setTournament);
		}
		if (principal != null) {
			userRepository.findByEmail(principal.getUsername()).ifPresent(post::setPostedBy);
		}

		facebookPostRepository.save(post);
	}

	private Map<String, Object> toPostSummary(FacebookPost p) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", p.getId());
		m.put("facebookPostId", p.getFacebookPostId());
		m.put("content", p.getContent() != null && p.getContent().length() > 200
				? p.getContent().substring(0, 200) + "..." : p.getContent());
		m.put("postType", p.getPostType());
		m.put("postedAt", p.getPostedAt());
		m.put("permalinkUrl", p.getPermalinkUrl());
		m.put("fullPicture", p.getFullPictureUrl());
		m.put("tournamentId", p.getTournament() != null ? p.getTournament().getId() : null);
		m.put("tournamentName", p.getTournament() != null ? p.getTournament().getName() : null);
		m.put("postedByEmail", p.getPostedBy() != null ? p.getPostedBy().getEmail() : "Hệ thống (tự động)");
		m.put("likes", p.getLikesCount());
		m.put("comments", p.getCommentsCount());
		m.put("shares", p.getSharesCount());
		m.put("impressions", p.getImpressions());
		m.put("reach", p.getReach());
		m.put("engagedUsers", p.getEngagedUsers());
		m.put("statsSyncedAt", p.getStatsSyncedAt());
		return m;
	}

	private Map<String, Object> toPostDetail(FacebookPost p) {
		Map<String, Object> m = toPostSummary(p);
		m.put("content", p.getContent());
		return m;
	}

	private Map<String, Object> cachedEngagement(FacebookPost p) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("facebookPostId", p.getFacebookPostId());
		m.put("likes", p.getLikesCount());
		m.put("comments", p.getCommentsCount());
		m.put("shares", p.getSharesCount());
		m.put("permalinkUrl", p.getPermalinkUrl());
		m.put("fullPicture", p.getFullPictureUrl());
		m.put("fromCache", true);
		m.put("statsSyncedAt", p.getStatsSyncedAt());
		return m;
	}

	private void applyEngagementCache(FacebookPost record, Map<String, Object> data) {
		record.setLikesCount(toInteger(data.get("likes")));
		record.setCommentsCount(toInteger(data.get("comments")));
		record.setSharesCount(toInteger(data.get("shares")));

		Object insightsObj = data.get("insights");
		if (insightsObj instanceof Map<?, ?> insights) {
			record.setImpressions(toInteger(insights.get("post_impressions")));
			record.setReach(toInteger(insights.get("post_impressions_unique")));
			record.setEngagedUsers(toInteger(insights.get("post_engaged_users")));
		}

		if (data.get("permalinkUrl") != null) {
			record.setPermalinkUrl(String.valueOf(data.get("permalinkUrl")));
		}
		if (data.get("fullPicture") != null && !String.valueOf(data.get("fullPicture")).isBlank()) {
			record.setFullPictureUrl(String.valueOf(data.get("fullPicture")));
		}
		record.setStatsSyncedAt(Instant.now());
	}

	private Integer toInteger(Object value) {
		if (value == null) return null;
		if (value instanceof Number n) return n.intValue();
		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private int nullSafe(Integer value) {
		return value != null ? value : 0;
	}

	// ─── Request DTOs ───────────────────────────────────────────────────

	@Getter @Setter
	public static class TextPostRequest {
		@NotBlank(message = "Nội dung bài viết không được để trống")
		private String message;
		private String link;
		private Long tournamentId;
	}

	@Getter @Setter
	public static class PhotoPostRequest {
		@NotBlank(message = "Nội dung bài viết không được để trống")
		private String message;
		@NotBlank(message = "URL ảnh không được để trống")
		private String imageUrl;
		private Long tournamentId;
	}

	@Getter @Setter
	public static class MultiPhotoPostRequest {
		@NotBlank(message = "Nội dung bài viết không được để trống")
		private String message;
		private List<String> imageUrls;
		private Long tournamentId;
	}

	@Getter @Setter
	public static class MinioPhotoPostRequest {
		@NotBlank(message = "Nội dung bài viết không được để trống")
		private String message;
		@NotBlank(message = "MinIO object key không được để trống")
		private String minioObjectKey;
		private Long tournamentId;
	}

	@Getter @Setter
	public static class MinioMultiPhotoPostRequest {
		@NotBlank(message = "Nội dung bài viết không được để trống")
		private String message;
		private List<String> minioObjectKeys;
		private Long tournamentId;
	}

	@Getter @Setter
	public static class TokenExchangeRequest {
		@NotBlank(message = "Short-lived token không được để trống")
		private String shortLivedToken;
	}
}
