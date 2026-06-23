package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class NewsPostRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String slug;

    @NotNull
    private Long categoryId;

    private String thumbnailUrl;

    @NotBlank
    private String content;

    private Set<Long> tagIds;
}
