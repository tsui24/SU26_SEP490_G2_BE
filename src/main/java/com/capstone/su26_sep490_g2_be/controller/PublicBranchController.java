package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.BranchListItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.BranchResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Public — Branches", description = "Danh sách và chi tiết chi nhánh công khai — không cần đăng nhập")
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class PublicBranchController {

	private final BranchService branchService;

	@Operation(summary = "Danh sách chi nhánh công khai (chỉ ACTIVE)")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<BranchListItemResponse>>> listBranches(
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(
				branchService.listPublicBranches(search, page, size)));
	}

	@Operation(summary = "Chi tiết chi nhánh công khai (chỉ ACTIVE)")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<BranchResponse>> getBranch(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(branchService.getPublicBranch(id)));
	}
}
