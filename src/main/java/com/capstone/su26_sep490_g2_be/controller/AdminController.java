package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.service.AccountService;
import com.capstone.su26_sep490_g2_be.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin", description = "Admin management APIs — requires ADMIN role")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

	private final AccountService accountService;
	private final LeaderboardService leaderboardService;

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
				.body(ApiResponse.success("Đã tạo tài khoản Owner", response));
	}

	@Operation(summary = "Create Admin account", description = "Admin tạo tài khoản Admin (quản trị hệ thống)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo Admin thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email đã tồn tại")
	})
	@PostMapping("/accounts/admin")
	public ResponseEntity<ApiResponse<UserResponse>> createAdmin(
			@Valid @RequestBody CreateAccountRequest request) {
		UserResponse response = accountService.createAccount(request, RoleCode.ADMIN, RoleCode.ADMIN);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Đã tạo tài khoản Admin", response));
	}

	@Operation(summary = "Get all , search, and filter accounts",
			description = "Lấy danh sách các tài khoản. Có hỗ trợ lọc theo role và tìm kiếm theo tên hoặc email.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền")
	})
	@GetMapping("/accounts")
	public ResponseEntity<ApiResponse<PageResponse<EmployeeAccountResponse>>> getUsers(
			@Parameter(description = "Mã vai trò cần lọc (ADMIN hoặc STAFF hoặc MANAGER hoặc OWNER hoặc PLAYER)")
			@RequestParam(required = false) String role,
			@Parameter(description = "Từ khóa tìm kiếm theo tên (fullName hoặc displayName)")
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		PageResponse<EmployeeAccountResponse> response = accountService.getUsers(role, search, page, size);
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
		EmployeeAccountResponse response = accountService.deactivateEmployee(null, id);
		return ResponseEntity.ok(ApiResponse.success("Đã vô hiệu hóa tài khoản", response));
	}

	@Operation(summary = "Admin can reactivate users",
			description = "Mở khóa lại tài khoản đã bị vô hiệu hóa (chuyển trạng thái sang ACTIVE).")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Mở khóa thành công"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User không tồn tại")
	})
	@PutMapping("/accounts/{id}/reactivate")
	public ResponseEntity<ApiResponse<EmployeeAccountResponse>> reactivateUser(
			@PathVariable Long id) {
		EmployeeAccountResponse response = accountService.reactivateEmployee(null, id);
		return ResponseEntity.ok(ApiResponse.success("Đã mở khóa tài khoản", response));
	}

	@Operation(summary = "Tính lại điểm tích lũy",
			description = "Tính lại points_earned cho toàn bộ kết quả của các giải đã COMPLETED theo "
					+ "công thức hiện hành. Cần chạy 1 lần cho dữ liệu cũ (chốt trước khi có công thức "
					+ "nên đang lưu 0 điểm), hoặc sau khi đổi thang điểm. Chạy lại nhiều lần vẫn ra "
					+ "cùng kết quả.")
	@PostMapping("/leaderboard/recalculate-points")
	public ResponseEntity<ApiResponse<Integer>> recalculateLeaderboardPoints() {
		int updated = leaderboardService.recalculatePoints();
		return ResponseEntity.ok(ApiResponse.success("Đã cập nhật " + updated + " dòng kết quả", updated));
	}
}