package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Tạo profile Player — bước 2 sau khi đăng ký tài khoản")
public class CreatePlayerProfileRequest {

	@NotBlank(message = "Họ tên không được để trống")
	@Schema(example = "Nguyen Van A")
	private String fullName;

	@Schema(example = "Player A")
	private String displayName;

	@Schema(description = "MinIO object key (data.objectKey từ POST /storage/images)", example = "avatars/a1b2c3d4-e5f6.jpg")
	private String avatarUrl;

	@PastOrPresent(message = "Ngày sinh không được là ngày trong tương lai")
	@Schema(example = "1998-05-15")
	private LocalDate dateOfBirth;

	@Schema(example = "MALE")
	private String gender;

	@Schema(description = "Xếp hạng bi-a dạng chuỗi (vd. A+, A, B-, C, D). Mặc định UNRANKED nếu bỏ trống", example = "C+")
	private String billiardRank;

	@Schema(example = "Passionate billiards player")
	private String bio;
}
