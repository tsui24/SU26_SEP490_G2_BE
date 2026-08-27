package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.config.MailProperties;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.FacebookPost;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.FacebookPostRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
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
import org.springframework.security.core.Authentication;
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
	private final BranchAccessService branchAccessService;
	private final BranchRepository branchRepository;

	// ─── Publish endpoints ──────────────────────────────────────────────

	@PostMapping("/post/text")
	@Operation(summary = "Đăng bài text / link lên Facebook Page")
	public ResponseEntity<ApiResponse<Map<String, String>>> publishTextPost(
			Authentication authentication,
			@Valid @RequestBody TextPostRequest request) {
		assertTournamentAccess(resolveActor(authentication), request.getTournamentId());
		String tournamentUrl = tournamentPublicUrl(request.getTournamentId());
		String message = appendTournamentLink(request.getMessage(), tournamentUrl);
		String link = (request.getLink() != null && !request.getLink().isBlank())
				? request.getLink()
				: tournamentUrl;
		String postId = facebookPublishService.publishTextPost(message, link);
		savePost(postId, message, "TEXT", request.getTournamentId(), authentication);
		return ResponseEntity.ok(ApiResponse.success("Đăng bài Facebook thành công",
				Map.of("facebookPostId", postId)));
	}

	@PostMapping("/post/photo")
	@Operation(summary = "Đăng bài kèm 1 ảnh (URL) lên Facebook Page")
	public ResponseEntity<ApiResponse<Map<String, String>>> publishPhotoPost(
			Authentication authentication,
			@Valid @RequestBody PhotoPostRequest request) {
		assertTournamentAccess(resolveActor(authentication), request.getTournamentId());
		String message = appendTournamentLink(request.getMessage(), tournamentPublicUrl(request.getTournamentId()));
		String postId = facebookPublishService.publishPhotoPost(message, request.getImageUrl());
		savePost(postId, message, "PHOTO", request.getTournamentId(), authentication);
		return ResponseEntity.ok(ApiResponse.success("Đăng bài Facebook (có ảnh) thành công",
				Map.of("facebookPostId", postId)));
	}

	@PostMapping("/post/photos")
	@Operation(summary = "Đăng bài kèm nhiều ảnh (URL) lên Facebook Page")
	public ResponseEntity<ApiResponse<Map<String, String>>> publishMultiPhotoPost(
			Authentication authentication,
			@Valid @RequestBody MultiPhotoPostRequest request) {
		assertTournamentAccess(resolveActor(authentication), request.getTournamentId());
		String message = appendTournamentLink(request.getMessage(), tournamentPublicUrl(request.getTournamentId()));
		String postId = facebookPublishService.publishMultiPhotoPost(message, request.getImageUrls());
		savePost(postId, message, "MULTI_PHOTO", request.getTournamentId(), authentication);
		return ResponseEntity.ok(ApiResponse.success("Đăng bài Facebook (nhiều ảnh) thành công",
				Map.of("facebookPostId", postId)));
	}

	@PostMapping("/post/minio-photo")
	@Operation(summary = "Đăng bài kèm 1 ảnh từ MinIO (BE upload binary)")
	public ResponseEntity<ApiResponse<Map<String, String>>> publishMinioPhoto(
			Authentication authentication,
			@Valid @RequestBody MinioPhotoPostRequest request) {
		assertTournamentAccess(resolveActor(authentication), request.getTournamentId());
		String message = appendTournamentLink(request.getMessage(), tournamentPublicUrl(request.getTournamentId()));
		String postId = facebookPublishService.publishPhotoFromMinio(
				message, request.getMinioObjectKey());
		savePost(postId, message, "PHOTO", request.getTournamentId(), authentication);
		return ResponseEntity.ok(ApiResponse.success("Đăng bài Facebook (ảnh MinIO) thành công",
				Map.of("facebookPostId", postId)));
	}

	@PostMapping("/post/minio-photos")
	@Operation(summary = "Đăng bài kèm nhiều ảnh từ MinIO (BE upload binary)")
	public ResponseEntity<ApiResponse<Map<String, String>>> publishMinioPhotos(
			Authentication authentication,
			@Valid @RequestBody MinioMultiPhotoPostRequest request) {
		assertTournamentAccess(resolveActor(authentication), request.getTournamentId());
		String message = appendTournamentLink(request.getMessage(), tournamentPublicUrl(request.getTournamentId()));
		String postId = facebookPublishService.publishMultiPhotoFromMinio(
				message, request.getMinioObjectKeys());
		savePost(postId, message, "MULTI_PHOTO", request.getTournamentId(), authentication);
		return ResponseEntity.ok(ApiResponse.success("Đăng bài Facebook (nhiều ảnh MinIO) thành công",
				Map.of("facebookPostId", postId)));
	}

	// ─── Insights endpoints ─────────────────────────────────────────────

	@GetMapping("/posts")
	@Operation(summary = "Danh sách bài đã đăng (phân trang)")
	@Transactional(readOnly = true)
	public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> listPosts(
			Authentication authentication,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		List<Long> branchIds = resolveAccessibleBranchIds(resolveActor(authentication));
		Page<Map<String, Object>> result = facebookPostRepository
				.findByTournament_Branch_IdInOrTournamentIsNullOrderByPostedAtDesc(branchIds, PageRequest.of(page, size))
				.map(this::toPostSummary);
		return ResponseEntity.ok(ApiResponse.success(result));
	}

	@GetMapping("/posts/tournament/{tournamentId}")
	@Operation(summary = "Danh sách bài đã đăng cho 1 giải đấu")
	@Transactional(readOnly = true)
	public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listPostsByTournament(
			Authentication authentication,
			@PathVariable Long tournamentId) {
		assertTournamentAccess(resolveActor(authentication), tournamentId);
		List<Map<String, Object>> result = facebookPostRepository
				.findByTournamentIdOrderByPostedAtDesc(tournamentId)
				.stream().map(this::toPostSummary).toList();
		return ResponseEntity.ok(ApiResponse.success(result));
	}

	@GetMapping("/posts/{postRecordId}")
	@Operation(summary = "Chi tiết 1 bài đăng trong hệ thống (full content)")
	@Transactional(readOnly = true)
	public ResponseEntity<ApiResponse<Map<String, Object>>> getPost(
			Authentication authentication,
			@PathVariable Long postRecordId) {
		FacebookPost record = facebookPostRepository.findById(postRecordId)
				.orElseThrow(() -> new RuntimeException("Bài đăng không tồn tại trong hệ thống"));
		assertPostAccess(resolveActor(authentication), record);
		return ResponseEntity.ok(ApiResponse.success(toPostDetail(record)));
	}

	@GetMapping("/posts/{postRecordId}/engagement")
	@Operation(summary = "Lấy lượt tương tác — mặc định từ cache DB; refresh=true để gọi Facebook")
	@Transactional
	public ResponseEntity<ApiResponse<Map<String, Object>>> getEngagement(
			Authentication authentication,
			@PathVariable Long postRecordId,
			@RequestParam(defaultValue = "false") boolean refresh) {
		FacebookPost record = facebookPostRepository.findById(postRecordId)
				.orElseThrow(() -> new RuntimeException("Bài đăng không tồn tại trong hệ thống"));
		assertPostAccess(resolveActor(authentication), record);

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
			Authentication authentication,
			@PathVariable Long postRecordId,
			@RequestParam(defaultValue = "false") boolean refresh) {
		FacebookPost record = facebookPostRepository.findById(postRecordId)
				.orElseThrow(() -> new RuntimeException("Bài đăng không tồn tại trong hệ thống"));
		assertPostAccess(resolveActor(authentication), record);

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

	/**
	 * {@code JwtAuthenticationFilter} gán {@code principal = email} (String) và
	 * {@code credentials = userId} (Long) — KHÔNG phải {@code UserDetails}, nên
	 * {@code @AuthenticationPrincipal UserDetails} không bao giờ bind được ở đây (âm thầm null,
	 * Spring không ném lỗi vì kiểu không khớp). Lấy userId thẳng từ credentials, đúng cách mọi
	 * controller khác trong hệ thống đang làm (VD {@code extractUserId(Authentication)}).
	 */
	private User resolveActor(Authentication authentication) {
		if (authentication == null || !(authentication.getCredentials() instanceof Long userId)) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		return userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
	}

	/**
	 * Bài đăng gắn với 1 giải cụ thể chỉ được xem/tạo bởi actor có quyền trên chi nhánh của giải đó
	 * — cùng logic {@code canActorAccessBranch} dùng khắp hệ thống (Owner theo chuỗi sở hữu, Manager
	 * theo chi nhánh được cấp quyền). Không truyền tournamentId (bài đăng chung, không gắn giải) thì
	 * bỏ qua vì không có tín hiệu chi nhánh nào để kiểm tra.
	 */
	private void assertTournamentAccess(User actor, Long tournamentId) {
		if (tournamentId == null) {
			return;
		}
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		Branch branch = tournament.getBranch();
		Long branchId = branch != null ? branch.getId() : null;
		if (!branchAccessService.canActorAccessBranch(actor, branchId)) {
			throw new BusinessException(ErrorCode.BRANCH_ACCESS_DENIED);
		}
	}

	private void assertPostAccess(User actor, FacebookPost record) {
		Tournament tournament = record.getTournament();
		if (tournament == null) {
			return;
		}
		assertTournamentAccess(actor, tournament.getId());
	}

	/** Owner thấy (các) chi nhánh mình sở hữu; Manager thấy (các) chi nhánh được cấp quyền. */
	private List<Long> resolveAccessibleBranchIds(User actor) {
		String roleCode = actor.getRole().getCode();
		if ("OWNER".equals(roleCode)) {
			return branchRepository.findByOwnerId(actor.getId()).stream().map(Branch::getId).toList();
		}
		if ("MANAGER".equals(roleCode)) {
			return branchAccessService.getAccessibleBranchIds(actor);
		}
		throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
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

	/**
	 * `postedBy` trước đây KHÔNG BAO GIỜ được ghi — cùng nguyên nhân ở {@link #resolveActor}:
	 * `principal` (UserDetails) luôn null nên nhánh gán postedBy luôn bị bỏ qua trong im lặng.
	 */
	private void savePost(String facebookPostId, String content, String postType,
			Long tournamentId, Authentication authentication) {
		FacebookPost post = FacebookPost.builder()
				.facebookPostId(facebookPostId)
				.content(content)
				.postType(postType)
				.postedAt(Instant.now())
				.build();

		if (tournamentId != null) {
			tournamentRepository.findById(tournamentId).ifPresent(post::setTournament);
		}
		if (authentication != null && authentication.getCredentials() instanceof Long userId) {
			userRepository.findById(userId).ifPresent(post::setPostedBy);
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
