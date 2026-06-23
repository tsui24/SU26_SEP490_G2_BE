package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class NewsPostResponse {
    private Long id;
    private String title;
    private String slug;
    private String thumbnailUrl;
    private String content;
    private String status;
    private Long categoryId;
    private String categoryName;
    private List<String> tags;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
