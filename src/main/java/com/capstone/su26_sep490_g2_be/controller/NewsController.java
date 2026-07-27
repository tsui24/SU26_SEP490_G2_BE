package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.NewsCategoryRequest;
import com.capstone.su26_sep490_g2_be.dto.request.NewsPostRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.NewsCategoryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.NewsPostResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.NewsCategory;
import com.capstone.su26_sep490_g2_be.entity.NewsPost;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.NewsCategoryStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.NewsCategoryService;
import com.capstone.su26_sep490_g2_be.service.NewsPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Tag(name = "News & Blog", description = "Bài viết và danh mục tin tức")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NewsController {

    private final NewsPostService postService;
    private final NewsCategoryService categoryService;

    /* ── Public endpoints ── */

    @Operation(summary = "Danh sách bài viết đã xuất bản")
    @GetMapping("/news")
    public ResponseEntity<ApiResponse<PageResponse<NewsPostResponse>>> listPublished(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        var result = postService.getPublished(search, categoryId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(result, this::toResponse)));
    }

    @Operation(summary = "Chi tiết bài viết theo slug")
    @GetMapping("/news/{slug}")
    public ResponseEntity<ApiResponse<NewsPostResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(toResponse(postService.getBySlug(slug))));
    }

    @Operation(summary = "Danh sách danh mục tin tức")
    @GetMapping("/news/categories")
    public ResponseEntity<ApiResponse<List<NewsCategoryResponse>>> listCategories() {
        return ResponseEntity.ok(ApiResponse.success(
                categoryService.getActive().stream().map(this::toCatResponse).toList()));
    }

    /* ── Owner CMS ── */

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Danh sách tất cả bài viết (Owner CMS)")
    @GetMapping("/owner/news")
    public ResponseEntity<ApiResponse<PageResponse<NewsPostResponse>>> ownerList(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(postService.getAll(extractUserId(auth), pageable), this::toResponse)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Chi tiết bài viết theo id (Owner CMS)")
    @GetMapping("/owner/news/{id}")
    public ResponseEntity<ApiResponse<NewsPostResponse>> ownerGetById(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(toResponse(postService.getById(id, extractUserId(auth)))));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Tạo bài viết (Owner)")
    @PostMapping("/owner/news")
    public ResponseEntity<ApiResponse<NewsPostResponse>> ownerCreate(
            Authentication auth,
            @Valid @RequestBody NewsPostRequest request) {
        NewsPost post = buildPost(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(
                        postService.create(post, extractUserId(auth), request.getCategoryId(),
                                request.getTagIds() != null ? request.getTagIds() : Set.of()))));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật bài viết (Owner)")
    @PutMapping("/owner/news/{id}")
    public ResponseEntity<ApiResponse<NewsPostResponse>> ownerUpdate(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody NewsPostRequest request) {
        return ResponseEntity.ok(ApiResponse.success(toResponse(
                postService.update(id, extractUserId(auth), buildPost(request), request.getCategoryId(),
                        request.getTagIds() != null ? request.getTagIds() : Set.of()))));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Xuất bản bài viết (Owner)")
    @PostMapping("/owner/news/{id}/publish")
    public ResponseEntity<ApiResponse<Void>> ownerPublish(Authentication auth, @PathVariable Long id) {
        postService.publish(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Ẩn bài viết (Owner)")
    @PostMapping("/owner/news/{id}/hide")
    public ResponseEntity<ApiResponse<Void>> ownerHide(Authentication auth, @PathVariable Long id) {
        postService.hide(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Xoá bài viết (Owner)")
    @DeleteMapping("/owner/news/{id}")
    public ResponseEntity<ApiResponse<Void>> ownerDelete(Authentication auth, @PathVariable Long id) {
        postService.delete(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /* ── Owner Category CMS ── */

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/owner/news/categories")
    public ResponseEntity<ApiResponse<List<NewsCategoryResponse>>> ownerListCategories() {
        return ResponseEntity.ok(ApiResponse.success(
                categoryService.getAll().stream().map(this::toCatResponse).toList()));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/owner/news/categories")
    public ResponseEntity<ApiResponse<NewsCategoryResponse>> ownerCreateCategory(
            @Valid @RequestBody NewsCategoryRequest request) {
        NewsCategory cat = NewsCategory.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .status(NewsCategoryStatus.ACTIVE.getValue())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toCatResponse(categoryService.create(cat))));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/owner/news/categories/{id}")
    public ResponseEntity<ApiResponse<NewsCategoryResponse>> ownerUpdateCategory(
            @PathVariable Long id, @Valid @RequestBody NewsCategoryRequest request) {
        NewsCategory cat = NewsCategory.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .build();
        return ResponseEntity.ok(ApiResponse.success(toCatResponse(categoryService.update(id, cat))));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/owner/news/categories/{id}/status")
    public ResponseEntity<ApiResponse<Void>> ownerCategoryStatus(
            @PathVariable Long id, @RequestParam String status) {
        categoryService.setStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /* ── Manager CMS (mirrors Owner) ── */

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/manager/news")
    public ResponseEntity<ApiResponse<PageResponse<NewsPostResponse>>> managerList(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(postService.getAll(extractUserId(auth), pageable), this::toResponse)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/manager/news/{id}")
    public ResponseEntity<ApiResponse<NewsPostResponse>> managerGetById(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(toResponse(postService.getById(id, extractUserId(auth)))));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/news")
    public ResponseEntity<ApiResponse<NewsPostResponse>> managerCreate(
            Authentication auth, @Valid @RequestBody NewsPostRequest request) {
        NewsPost post = buildPost(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(
                        postService.create(post, extractUserId(auth), request.getCategoryId(),
                                request.getTagIds() != null ? request.getTagIds() : Set.of()))));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/manager/news/{id}")
    public ResponseEntity<ApiResponse<NewsPostResponse>> managerUpdate(
            Authentication auth, @PathVariable Long id, @Valid @RequestBody NewsPostRequest request) {
        return ResponseEntity.ok(ApiResponse.success(toResponse(
                postService.update(id, extractUserId(auth), buildPost(request), request.getCategoryId(),
                        request.getTagIds() != null ? request.getTagIds() : Set.of()))));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/news/{id}/publish")
    public ResponseEntity<ApiResponse<Void>> managerPublish(Authentication auth, @PathVariable Long id) {
        postService.publish(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/news/{id}/hide")
    public ResponseEntity<ApiResponse<Void>> managerHide(Authentication auth, @PathVariable Long id) {
        postService.hide(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/manager/news/{id}")
    public ResponseEntity<ApiResponse<Void>> managerDelete(Authentication auth, @PathVariable Long id) {
        postService.delete(id, extractUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/manager/news/categories")
    public ResponseEntity<ApiResponse<List<NewsCategoryResponse>>> managerListCategories() {
        return ResponseEntity.ok(ApiResponse.success(
                categoryService.getAll().stream().map(this::toCatResponse).toList()));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/manager/news/categories")
    public ResponseEntity<ApiResponse<NewsCategoryResponse>> managerCreateCategory(
            @Valid @RequestBody NewsCategoryRequest req) {
        NewsCategory cat = NewsCategory.builder().name(req.getName()).slug(req.getSlug()).status(NewsCategoryStatus.ACTIVE.getValue()).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(toCatResponse(categoryService.create(cat))));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/manager/news/categories/{id}")
    public ResponseEntity<ApiResponse<NewsCategoryResponse>> managerUpdateCategory(
            @PathVariable Long id, @Valid @RequestBody NewsCategoryRequest request) {
        NewsCategory cat = NewsCategory.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .build();
        return ResponseEntity.ok(ApiResponse.success(toCatResponse(categoryService.update(id, cat))));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/manager/news/categories/{id}/status")
    public ResponseEntity<ApiResponse<Void>> managerCategoryStatus(
            @PathVariable Long id, @RequestParam String status) {
        categoryService.setStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /* ── Helpers ── */

    private NewsPost buildPost(NewsPostRequest req) {
        return NewsPost.builder()
                .title(req.getTitle())
                .slug(req.getSlug())
                .thumbnailUrl(req.getThumbnailUrl())
                .content(req.getContent())
                .build();
    }

    private NewsPostResponse toResponse(NewsPost post) {
        return NewsPostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .thumbnailUrl(post.getThumbnailUrl())
                .content(post.getContent())
                .status(post.getStatus())
                .categoryId(post.getCategory() != null ? post.getCategory().getId() : null)
                .categoryName(post.getCategory() != null ? post.getCategory().getName() : null)
                .tags(post.getTags() != null ? post.getTags().stream()
                        .map(t -> t.getName()).toList() : List.of())
                .tagIds(post.getTags() != null ? post.getTags().stream()
                        .map(t -> t.getId()).toList() : List.of())
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private NewsCategoryResponse toCatResponse(NewsCategory cat) {
        return NewsCategoryResponse.builder()
                .id(cat.getId())
                .name(cat.getName())
                .slug(cat.getSlug())
                .status(cat.getStatus())
                .createdAt(cat.getCreatedAt())
                .build();
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || !(auth.getCredentials() instanceof Long)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return (Long) auth.getCredentials();
    }
}
