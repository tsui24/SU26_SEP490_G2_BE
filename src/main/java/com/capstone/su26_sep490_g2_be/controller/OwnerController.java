package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Owner", description = "Owner management APIs — requires OWNER role")
@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerController {

	private final AccountService accountService;

	@Operation(summary = "Create Manager account",
			description = "Tạo Manager kèm profile cơ bản (avatar, gender, ...). Không có billiard ranking.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo Manager thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email đã tồn tại")
	})
	@PostMapping("/accounts/manager")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> createManager(
			@Valid @RequestBody CreateManagerAccountRequest request) {
		EmployeeAccountResponse response = accountService.createManagerAccount(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Manager account created", response));
	}

	@Operation(summary = "Create Staff account",
			description = "Tạo Staff kèm profile cơ bản (avatar, gender, ...). Không có billiard ranking.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo Staff thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email đã tồn tại")
	})
	@PostMapping("/accounts/staff")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> createStaff(
			@Valid @RequestBody CreateStaffAccountRequest request) {
		EmployeeAccountResponse response = accountService.createStaffAccount(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Staff account created", response));
	}
}
