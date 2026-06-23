package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class NewsCategoryResponse {
    private Long id;
    private String name;
    private String slug;
    private String status;
    private Instant createdAt;
}
