package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateEmployeeAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.service.AccountService;
import com.capstone.su26_sep490_g2_be.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Owner", description = "Owner management APIs — requires OWNER role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerController {

	private final AccountService accountService;

	@Operation(summary = "Create Manager account",
			description = "Tạo Manager kèm profile cơ bản (avatar, gender, ...) và phân quyền chi nhánh (toàn chuỗi hoặc theo chi nhánh).")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo Manager thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email đã tồn tại")
	})
	@PostMapping("/accounts/manager")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> createManager(
			Authentication authentication,
			@Valid @RequestBody CreateManagerAccountRequest request) {
		EmployeeAccountResponse response = accountService.createManagerAccount(
				request, SecurityUtil.extractUserId(authentication));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Đã tạo tài khoản Manager", response));
	}

	@Operation(summary = "Cập nhật thông tin Manager/Staff",
			description = "Sửa hồ sơ (họ tên, avatar, ...) — với Manager có thể đổi luôn phạm vi quản lý chi nhánh.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên")
	})
	@PutMapping("/employees/{id}")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> updateEmployee(
			Authentication authentication,
			@PathVariable Long id,
			@Valid @RequestBody UpdateEmployeeAccountRequest request) {
		EmployeeAccountResponse response = accountService.updateEmployee(
				SecurityUtil.extractUserId(authentication), id, request);
		return ResponseEntity.ok(ApiResponse.success("Đã cập nhật thông tin nhân viên", response));
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
			Authentication authentication,
			@Valid @RequestBody CreateStaffAccountRequest request) {
		EmployeeAccountResponse response = accountService.createStaffAccount(
				request, SecurityUtil.extractUserId(authentication));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Đã tạo tài khoản Staff", response));
	}

	@Operation(summary = "Get all , search, and filter employee accounts",
			description = "Lấy danh sách các tài khoản có role là STAFF và MANAGER. Có hỗ trợ lọc theo role và tìm kiếm theo tên hoặc email")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền")
	})
	@GetMapping("/employees")
	public ResponseEntity<ApiResponse<PageResponse<EmployeeAccountResponse>>> getEmployees(
			Authentication authentication,
			@Parameter(description = "Mã vai trò cần lọc (STAFF hoặc MANAGER)")
			@RequestParam(required = false) String role,
			@Parameter(description = "Từ khóa tìm kiếm theo tên (fullName hoặc displayName)")
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		PageResponse<EmployeeAccountResponse> response = accountService.getEmployees(
				SecurityUtil.extractUserId(authentication), role, search, page, size);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(summary = "View staff details",
			description = "Xem thông tin chi tiết của một nhân viên (Staff hoặc Manager) theo ID.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy chi tiết thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Nhân viên không tồn tại")
	})
	@GetMapping("/employees/{id}")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> getEmployeeDetail(
			Authentication authentication,
			@PathVariable Long id) {
		EmployeeAccountResponse response = accountService.getEmployeeDetail(
				SecurityUtil.extractUserId(authentication), id);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(summary = "Deactivate employee account",
			description = "Vô hiệu hóa tài khoản của một staff or một manager (chuyển trạng thái sang LOCKED).")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vô hiệu hóa thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Nhân viên không tồn tại")
	})
	@PutMapping("/employees/{id}/deactivate")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> deactivateEmployee(
			Authentication authentication,
			@PathVariable Long id) {
		EmployeeAccountResponse response = accountService.deactivateEmployee(
				SecurityUtil.extractUserId(authentication), id);
		return ResponseEntity.ok(ApiResponse.success("Đã vô hiệu hóa nhân viên", response));
	}

	@Operation(summary = "Reactivate employee account",
			description = "Mở khóa lại tài khoản của một staff hoặc một manager (chuyển trạng thái sang ACTIVE).")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Mở khóa thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Nhân viên không tồn tại")
	})
	@PutMapping("/employees/{id}/reactivate")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> reactivateEmployee(
			Authentication authentication,
			@PathVariable Long id) {
		EmployeeAccountResponse response = accountService.reactivateEmployee(
				SecurityUtil.extractUserId(authentication), id);
		return ResponseEntity.ok(ApiResponse.success("Đã mở khóa nhân viên", response));
	}
}