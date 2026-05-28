package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin", description = "Admin management APIs — requires ADMIN role")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

	private final AccountService accountService;

	@Operation(summary = "Create Owner account", description = "Admin tạo tài khoản Owner (chủ chuỗi quán)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo Owner thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email đã tồn tại")
	})
	@PostMapping("/accounts/owner")
	public ResponseEntity<ApiResponse<UserResponse>> createOwner(
			@Valid @RequestBody CreateAccountRequest request) {
		UserResponse response = accountService.createAccount(request, RoleCode.OWNER, RoleCode.ADMIN);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Owner account created", response));
	}

	@Operation(summary = "Get, search, and filter accounts",
			description = "Lấy danh sách các tài khoản. Có hỗ trợ lọc theo role và tìm kiếm theo tên.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền")
	})
	@GetMapping("/accounts")
	public ResponseEntity<ApiResponse<Page<EmployeeAccountResponse>>> getEmployees(
			@Parameter(description = "Mã vai trò cần lọc (STAFF hoặc MANAGER hoặc OWNER hoặc PLAYER)")
			@RequestParam(required = false) String role,
			@Parameter(description = "Từ khóa tìm kiếm theo tên (fullName hoặc displayName)")
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		Page<EmployeeAccountResponse> response = accountService.getEmployees(role, search, page, size);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(summary = "Admin can deactivate users",
			description = "Vô hiệu hóa tài khoản (chuyển trạng thái sang LOCKED).")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vô hiệu hóa thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User không tồn tại")
	})
	@PutMapping("/accounts/{id}/deactivate")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> deactivateUser(
			@PathVariable Long id) {
		EmployeeAccountResponse response = accountService.deactivateEmployee(id);
		return ResponseEntity.ok(ApiResponse.success("User account deactivated successfully", response));
	}
}