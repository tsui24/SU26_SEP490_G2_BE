package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.BranchStatusUpdateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateBranchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateBranchRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.BranchListItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.BranchResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.service.BranchService;
import com.capstone.su26_sep490_g2_be.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Owner — Branches", description = "Owner tạo và quản lý chi nhánh — requires OWNER role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/owner/branches")
@RequiredArgsConstructor
public class OwnerBranchController {

	private final BranchService branchService;

	@Operation(summary = "Danh sách chi nhánh")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<BranchListItemResponse>>> listBranches(
			Authentication authentication,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(branchService.listBranchesForOwner(
				SecurityUtil.extractUserId(authentication), search, status, page, size)));
	}

	@Operation(summary = "Tạo chi nhánh")
	@PostMapping
	public ResponseEntity<ApiResponse<BranchResponse>> createBranch(
			Authentication authentication,
			@Valid @RequestBody CreateBranchRequest request) {
		BranchResponse response = branchService.createBranch(SecurityUtil.extractUserId(authentication), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đã tạo chi nhánh", response));
	}

	@Operation(summary = "Chi tiết chi nhánh")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<BranchResponse>> getBranch(
			Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				branchService.getBranchForOwner(SecurityUtil.extractUserId(authentication), id)));
	}

	@Operation(summary = "Cập nhật chi nhánh")
	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<BranchResponse>> updateBranch(
			Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody UpdateBranchRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				branchService.updateBranch(SecurityUtil.extractUserId(authentication), id, request)));
	}

	@Operation(summary = "Đổi trạng thái chi nhánh", description = "ACTIVE / INACTIVE")
	@PatchMapping("/{id}/status")
	public ResponseEntity<ApiResponse<BranchResponse>> updateStatus(
			Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody BranchStatusUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				branchService.updateStatus(SecurityUtil.extractUserId(authentication), id, request)));
	}
}
