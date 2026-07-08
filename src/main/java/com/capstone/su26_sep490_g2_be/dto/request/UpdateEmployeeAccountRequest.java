package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Schema(description = "Cập nhật thông tin nhân viên (Manager/Staff). Với Manager, có thể đổi luôn phạm vi quản lý chi nhánh.")
public class UpdateEmployeeAccountRequest {

	@Schema(example = "0912345678")
	private String phone;

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

	@Schema(description = "Chỉ áp dụng cho Manager — true = quản lý toàn chuỗi, false = quản lý theo chi nhánh cụ thể")
	private Boolean manageAllBranches;

	@Schema(description = "Chỉ áp dụng cho Manager khi manageAllBranches=false")
	private List<Long> branchIds;

	@Schema(description = "Chỉ áp dụng cho Staff — đổi chi nhánh làm việc")
	private Long branchId;
}
