package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tài khoản Manager/Staff đã tạo kèm profile cơ bản")
public class EmployeeAccountResponse {

	private Long id;
	private String email;
	private String phone;
	private String role;
	private String status;

	private String fullName;
	private String displayName;
	private String avatarUrl;
	private LocalDate dateOfBirth;
	private String gender;
	private String bio;
}
