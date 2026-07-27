package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Owner tạo tài khoản Staff kèm thông tin cơ bản (không có ranking)")
public class CreateStaffAccountRequest {

	@NotBlank(message = "Email không được để trống")
	@Email(message = "Định dạng email không hợp lệ")
	@Schema(example = "staff@example.com")
	private String email;

	@Pattern(regexp = "^(0[3|5|7|8|9])[0-9]{8}$", message = "Số điện thoại không hợp lệ")
	@Schema(example = "0912345678")
	private String phone;

	@NotBlank(message = "Mật khẩu không được để trống")
	@Size(min = 6, max = 100, message = "Mật khẩu phải từ 6-100 ký tự")
	@Schema(example = "P@ssw0rd")
	private String password;

	@NotBlank(message = "Họ tên không được để trống")
	@Schema(example = "Le Van C")
	private String fullName;

	@Schema(example = "Staff C")
	private String displayName;

	@Schema(example = "https://example.com/avatar.jpg")
	private String avatarUrl;

	@PastOrPresent(message = "Ngày sinh không được là ngày trong tương lai")
	@Schema(example = "1995-08-10")
	private LocalDate dateOfBirth;

	@Schema(example = "FEMALE")
	private String gender;

	@Schema(example = "Referee / venue staff")
	private String bio;

	@Schema(description = "Chi nhánh làm việc (tùy chọn) — 1 staff làm việc tại 1 chi nhánh", example = "1")
	private Long branchId;
}
