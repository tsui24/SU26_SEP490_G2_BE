package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin hồ sơ phản hồi cho người dùng")
public class UserProfileResponse {

	@Schema(description = "Email tài khoản")
	private String email;

	@Schema(description = "Số điện thoại tài khoản")
	private String phone;

	@Schema(description = "Họ và tên đầy đủ")
	private String fullName;

	@Schema(description = "Tên hiển thị")
	private String displayName;

	@Schema(description = "Đường dẫn ảnh đại diện")
	private String avatarUrl;

	@Schema(description = "Ngày sinh")
	private LocalDate dateOfBirth;

	@Schema(description = "Giới tính")
	private String gender;

	@Schema(description = "Xếp hạng bi-a (Chỉ có giá trị đối với tài khoản PLAYER các role khác không hiện field này )")
	private String billiardRank;

	@Schema(description = "Tiểu sử/Giới thiệu bản thân")
	private String bio;

}
