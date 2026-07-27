package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NewsCategoryRequest {

    @NotBlank(message = "Tên chuyên mục không được để trống")
    @Size(max = 120, message = "Tên chuyên mục tối đa 120 ký tự")
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 120, message = "Slug tối đa 120 ký tự")
    private String slug;
}
