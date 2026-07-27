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
@Schema(description = "Owner tạo tài khoản Manager kèm thông tin cơ bản (không có ranking)")
public class CreateManagerAccountRequest {

	@NotBlank(message = "Email không được để trống")
	@Email(message = "Định dạng email không hợp lệ")
	@Schema(example = "manager@example.com")
	private String email;

	@Pattern(regexp = "^(0[3|5|7|8|9])[0-9]{8}$", message = "Số điện thoại không hợp lệ")
	@Schema(example = "0912345678")
	private String phone;

	@NotBlank(message = "Mật khẩu không được để trống")
	@Size(min = 6, max = 100, message = "Mật khẩu phải từ 6-100 ký tự")
	@Schema(example = "P@ssw0rd")
	private String password;

	@NotBlank(message = "Họ tên không được để trống")
	@Schema(example = "Tran Van B")
	private String fullName;

	@Schema(example = "Manager B")
	private String displayName;

	@Schema(example = "https://example.com/avatar.jpg")
	private String avatarUrl;

	@PastOrPresent(message = "Ngày sinh không được là ngày trong tương lai")
	@Schema(example = "1990-03-20")
	private LocalDate dateOfBirth;

	@Schema(example = "MALE")
	private String gender;

	@Schema(example = "Club manager")
	private String bio;

	@Schema(description = "true = quản lý toàn chuỗi, false = quản lý theo chi nhánh cụ thể", example = "false")
	private Boolean manageAllBranches;

	@Schema(description = "Danh sách branchId — bắt buộc khi manageAllBranches=false")
	private java.util.List<Long> branchIds;
}
