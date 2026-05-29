package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Yêu cầu tạo hoặc cập nhật hồ sơ người dùng")
public class UserProfileRequest {

	@NotBlank(message = "Full name is required")
	@Schema(description = "Họ và tên đầy đủ", example = "Nguyen Van A")
	private String fullName;

	@Schema(description = "Email tài khoản")
	private String email;

	@Pattern(
			regexp = "^(0[3|5|7|8|9])[0-9]{8}$",
			message = "Phone number is invalid"
	)
	@Schema(description = "Số điện thoại tài khoản")
	private String phone;

	@Schema(description = "Tên hiển thị", example = "Player A")
	private String displayName;

	@Schema(description = "Đường dẫn ảnh đại diện", example = "https://example.com/avatar.jpg")
	private String avatarUrl;

	@Schema(description = "Ngày sinh", example = "1998-05-15")
	private LocalDate dateOfBirth;

	@Schema(description = "Giới tính", example = "MALE")
	private String gender;

	@Schema(description = "Xếp hạng bi-a (LƯU Ý: Chỉ khả dụng và có hiệu lực với tài khoản role PLAYER. Các role khác không thể set trường này)", example = "C+")
	private String billiardRank;

	@Schema(description = "Tiểu sử/Giới thiệu bản thân", example = "Passionate billiards player")
	private String bio;
}
