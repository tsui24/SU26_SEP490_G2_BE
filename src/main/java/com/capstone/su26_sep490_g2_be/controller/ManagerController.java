package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Manager", description = "Manager APIs — requires MANAGER role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class ManagerController {

	private final AccountService accountService;

	@Operation(summary = "Get all, search and filter staff accounts",
			description = "Lấy danh sách các tài khoản có role là STAFF. Hỗ trợ tìm kiếm lọc theo tên, số điện thoại hoặc email.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền")
	})
	@GetMapping("/accounts/staffs")
	public ResponseEntity<ApiResponse<PageResponse<EmployeeAccountResponse>>> getStaffs(
			@Parameter(description = "Từ khóa tìm kiếm lọc theo tên (fullName hoặc displayName), số điện thoại hoặc email")
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		PageResponse<EmployeeAccountResponse> response = accountService.getStaffsByManager(search, page, size);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(summary = "Manager can create Staff account",
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
				.body(ApiResponse.success("Đã tạo tài khoản Staff", response));
	}
	@Operation(summary = "View staff details",
			description = "Xem thông tin chi tiết của một nhân viên  theo ID.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy chi tiết thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Nhân viên không tồn tại")
	})
	@GetMapping("/employees/{id}")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> getEmployeeDetail(
			@PathVariable Long id) {
		EmployeeAccountResponse response = accountService.getEmployeeDetail(id);
		return ResponseEntity.ok(ApiResponse.success(response));
	}


	@Operation(summary = "Manager can deactivate staff account",
			description = "Vô hiệu hóa tài khoản của một nhân viên (chuyển trạng thái sang LOCKED).")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vô hiệu hóa thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Nhân viên không tồn tại")
	})
	@PutMapping("/accounts/staffs/{id}/deactivate")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> deactivateEmployee(
			@PathVariable Long id) {
		EmployeeAccountResponse response = accountService.deactivateEmployee(id);
		return ResponseEntity.ok(ApiResponse.success("Đã vô hiệu hóa tài khoản Staff", response));
	}
}