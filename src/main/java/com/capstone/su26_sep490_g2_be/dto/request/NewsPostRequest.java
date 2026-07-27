package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class NewsPostRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Slug không được để trống")
    private String slug;

    @NotNull(message = "Chuyên mục không được để trống")
    private Long categoryId;

    private String thumbnailUrl;

    @NotBlank(message = "Nội dung không được để trống")
    private String content;

    private Set<Long> tagIds;
}
