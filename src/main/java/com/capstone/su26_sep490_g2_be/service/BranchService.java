package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.BranchStatusUpdateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateBranchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateBranchRequest;
import com.capstone.su26_sep490_g2_be.dto.response.BranchListItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.BranchResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.User;

public interface BranchService {

	BranchResponse createBranch(Long ownerId, CreateBranchRequest request);

	BranchResponse updateBranch(Long ownerId, Long branchId, UpdateBranchRequest request);

	BranchResponse updateStatus(Long ownerId, Long branchId, BranchStatusUpdateRequest request);

	BranchResponse getBranchForOwner(Long ownerId, Long branchId);

	PageResponse<BranchListItemResponse> listBranchesForOwner(
			Long ownerId, String search, String status, int page, int size);

	/** Manager — chỉ đọc, giới hạn trong các chi nhánh được truy cập (qua {@link BranchAccessService}). */
	PageResponse<BranchListItemResponse> listAccessibleBranches(User manager, int page, int size);

	BranchResponse getAccessibleBranch(User manager, Long branchId);

	/** Public — không cần đăng nhập, chỉ trả về chi nhánh đang ACTIVE. */
	PageResponse<BranchListItemResponse> listPublicBranches(String search, int page, int size);

	/** Public — không cần đăng nhập, chỉ trả về chi nhánh đang ACTIVE. */
	BranchResponse getPublicBranch(Long branchId);
}
