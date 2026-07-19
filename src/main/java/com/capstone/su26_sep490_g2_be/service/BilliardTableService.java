package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.CreateBilliardTableRequest;
import com.capstone.su26_sep490_g2_be.dto.request.TableStatusUpdateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateBilliardTableRequest;
import com.capstone.su26_sep490_g2_be.dto.response.BilliardTableResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;

import java.util.List;

public interface BilliardTableService {

	BilliardTableResponse createTable(Long ownerId, CreateBilliardTableRequest request);

	BilliardTableResponse updateTable(Long ownerId, Long tableId, UpdateBilliardTableRequest request);

	BilliardTableResponse updateStatus(Long ownerId, Long tableId, TableStatusUpdateRequest request);

	BilliardTableResponse getTableForOwner(Long ownerId, Long tableId);

	PageResponse<BilliardTableResponse> listTablesForOwner(
			Long ownerId, String search, String status, Long branchId, int page, int size);

	/** Danh sách bàn ACTIVE của owner — dùng cho dropdown gán bàn (Owner/Manager), không phân trang. */
	List<BilliardTableResponse> listActiveForOwner(Long ownerId);
}
