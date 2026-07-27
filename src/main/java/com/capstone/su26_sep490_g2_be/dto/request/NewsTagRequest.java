package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NewsTagRequest {

    @NotBlank(message = "Tên tag không được để trống")
    @Size(max = 100, message = "Tên tag tối đa 100 ký tự")
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 100, message = "Slug tối đa 100 ký tự")
    private String slug;
}
