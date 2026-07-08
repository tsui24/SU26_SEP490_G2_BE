package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.User;

import java.util.List;

/**
 * Trung tâm hóa toàn bộ logic phân quyền Manager–Branch.
 * Mọi nơi cần kiểm tra quyền của Manager đối với chi nhánh phải gọi qua service này.
 */
public interface BranchAccessService {

	/** Manager có quyền truy cập branch này? */
	boolean canManagerAccessBranch(User manager, Long branchId);

	/** Danh sách branchId manager được phép thao tác. */
	List<Long> getAccessibleBranchIds(User manager);

	/** Manager có quyền tạo tournament tại branch? */
	boolean canManagerCreateTournamentAt(User manager, Long branchId);

	/** Lấy danh sách Branch entity manager truy cập được (chỉ ACTIVE). */
	List<Branch> getAccessibleBranches(User manager);

	/** Manager có quyền toàn chuỗi? */
	boolean isChainWideManager(User manager);
}
