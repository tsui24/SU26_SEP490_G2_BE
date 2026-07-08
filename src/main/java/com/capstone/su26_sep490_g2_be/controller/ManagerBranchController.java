package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.BranchListItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.BranchResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.service.BranchService;
import com.capstone.su26_sep490_g2_be.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Manager — Branches", description = "Manager xem các chi nhánh được phân quyền — requires MANAGER role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/manager/branches")
@RequiredArgsConstructor
public class ManagerBranchController {

	private final BranchService branchService;
	private final SecurityUtil securityUtil;

	@Operation(summary = "Danh sách chi nhánh được truy cập", description = "Toàn chuỗi hoặc theo chi nhánh được Owner gán")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<BranchListItemResponse>>> listBranches(
			Authentication authentication,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		User manager = securityUtil.resolveCurrentUser(authentication);
		return ResponseEntity.ok(ApiResponse.success(branchService.listAccessibleBranches(manager, page, size)));
	}

	@Operation(summary = "Chi tiết chi nhánh", description = "Chỉ xem được nếu chi nhánh nằm trong phạm vi truy cập")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<BranchResponse>> getBranch(
			Authentication authentication, @PathVariable Long id) {
		User manager = securityUtil.resolveCurrentUser(authentication);
		return ResponseEntity.ok(ApiResponse.success(branchService.getAccessibleBranch(manager, id)));
	}
}
