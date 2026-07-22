package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.NewsTagRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.NewsTagResponse;
import com.capstone.su26_sep490_g2_be.entity.NewsTag;
import com.capstone.su26_sep490_g2_be.service.NewsTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "News Tags (Owner + Manager)", description = "Quản lý thẻ bài viết")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/shared/news/tags")
@RequiredArgsConstructor
public class NewsTagController {

    private final NewsTagService tagService;

    @Operation(summary = "Danh sách thẻ")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NewsTagResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(
                tagService.getAll().stream().map(this::toResponse).toList()));
    }

    @Operation(summary = "Tạo thẻ")
    @PostMapping
    public ResponseEntity<ApiResponse<NewsTagResponse>> create(@Valid @RequestBody NewsTagRequest request) {
        NewsTag tag = NewsTag.builder().name(request.getName()).slug(request.getSlug()).build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(tagService.create(tag))));
    }

    @Operation(summary = "Cập nhật thẻ")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NewsTagResponse>> update(
            @PathVariable Long id, @Valid @RequestBody NewsTagRequest request) {
        NewsTag tag = NewsTag.builder().name(request.getName()).slug(request.getSlug()).build();
        return ResponseEntity.ok(ApiResponse.success(toResponse(tagService.update(id, tag))));
    }

    @Operation(summary = "Xoá thẻ")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private NewsTagResponse toResponse(NewsTag tag) {
        return NewsTagResponse.builder().id(tag.getId()).name(tag.getName()).slug(tag.getSlug()).build();
    }
}
